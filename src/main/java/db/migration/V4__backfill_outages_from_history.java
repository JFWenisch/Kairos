package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Backfills outage records from existing check_result history.
 * Intended for upgrades from a version before the outage feature was introduced.
 * Runs after V3 which creates the outage table.
 */
public class V4__backfill_outages_from_history extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        // Collect all resource IDs and their types
        List<Long> resourceIds = new ArrayList<>();
        List<String> resourceTypes = new ArrayList<>();

        try (Statement st = context.getConnection().createStatement();
             ResultSet rs = st.executeQuery("SELECT id, resource_type FROM monitored_resource ORDER BY id")) {
            while (rs.next()) {
                resourceIds.add(rs.getLong("id"));
                resourceTypes.add(rs.getString("resource_type"));
            }
        }

        for (int i = 0; i < resourceIds.size(); i++) {
            long resourceId = resourceIds.get(i);
            String resourceType = resourceTypes.get(i);

            // Resolve thresholds from resource_type_config, fall back to defaults
            int outageThreshold = 3;
            int recoveryThreshold = 2;

            try (PreparedStatement ps = context.getConnection().prepareStatement(
                    "SELECT outage_threshold, recovery_threshold FROM resource_type_config WHERE type_name = ?")) {
                ps.setString(1, resourceType);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        outageThreshold = rs.getInt("outage_threshold");
                        recoveryThreshold = rs.getInt("recovery_threshold");
                    }
                }
            }

            // Load the full check history for this resource, oldest first
            List<String> statuses = new ArrayList<>();
            List<Timestamp> timestamps = new ArrayList<>();

            try (PreparedStatement ps = context.getConnection().prepareStatement(
                    "SELECT status, checked_at FROM check_result WHERE resource_id = ? ORDER BY checked_at ASC")) {
                ps.setLong(1, resourceId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        statuses.add(rs.getString("status"));
                        timestamps.add(rs.getTimestamp("checked_at"));
                    }
                }
            }

            // Replay the outage lifecycle over the historical data
            int consecutiveFailures = 0;
            int consecutiveSuccesses = 0;
            Long openOutageId = null;
            Timestamp firstFailureTs = null;

            for (int j = 0; j < statuses.size(); j++) {
                String status = statuses.get(j);
                Timestamp ts = timestamps.get(j);

                if ("NOT_AVAILABLE".equals(status)) {
                    consecutiveSuccesses = 0;

                    if (openOutageId == null) {
                        if (consecutiveFailures == 0) {
                            firstFailureTs = ts; // remember start of this failure streak
                        }
                        consecutiveFailures++;

                        if (consecutiveFailures >= outageThreshold) {
                            // Open a new outage, start_date = first failure in the streak
                            try (PreparedStatement ins = context.getConnection().prepareStatement(
                                    "INSERT INTO outage (resource_id, start_date, active) VALUES (?, ?, TRUE)",
                                    Statement.RETURN_GENERATED_KEYS)) {
                                ins.setLong(1, resourceId);
                                ins.setTimestamp(2, firstFailureTs);
                                ins.executeUpdate();
                                try (ResultSet keys = ins.getGeneratedKeys()) {
                                    if (keys.next()) {
                                        openOutageId = keys.getLong(1);
                                    }
                                }
                            }
                            consecutiveFailures = 0;
                            firstFailureTs = null;
                        }
                    }
                    // If an outage is already active we simply stay in it

                } else if ("AVAILABLE".equals(status)) {
                    consecutiveFailures = 0;
                    firstFailureTs = null;

                    if (openOutageId != null) {
                        consecutiveSuccesses++;

                        if (consecutiveSuccesses >= recoveryThreshold) {
                            // Close the active outage; end_date = this check's timestamp
                            try (PreparedStatement upd = context.getConnection().prepareStatement(
                                    "UPDATE outage SET end_date = ?, active = FALSE WHERE id = ?")) {
                                upd.setTimestamp(1, ts);
                                upd.setLong(2, openOutageId);
                                upd.executeUpdate();
                            }
                            openOutageId = null;
                            consecutiveSuccesses = 0;
                        }
                    } else {
                        consecutiveSuccesses = 0;
                    }

                } else {
                    // UNKNOWN — treat as a gap; reset streak counters but keep any open outage
                    consecutiveFailures = 0;
                    consecutiveSuccesses = 0;
                    firstFailureTs = null;
                }
            }
            // Any outage still open at the end remains active with no end_date
        }
    }
}

package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Adds default DiscoveryServiceConfig for OPENSHIFT_ROUTE discovery service type.
 */
public class V21__add_openshift_route_discovery_config extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            // Check if OPENSHIFT_ROUTE config already exists
            boolean exists = false;
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT 1 FROM discovery_service_config WHERE type_name = 'OPENSHIFT_ROUTE'")) {
                exists = rs.next();
            }

            if (!exists) {
                // Insert default configuration for OPENSHIFT_ROUTE
                try (PreparedStatement ps = context.getConnection().prepareStatement(
                        "INSERT INTO discovery_service_config (type_name, sync_interval_minutes, parallelism) VALUES (?, ?, ?)")) {
                    ps.setString(1, "OPENSHIFT_ROUTE");
                    ps.setInt(2, 60);  // Default: sync every 60 minutes
                    ps.setInt(3, 1);   // Default: 1 parallel worker
                    ps.executeUpdate();
                }
            }
        }
    }
}

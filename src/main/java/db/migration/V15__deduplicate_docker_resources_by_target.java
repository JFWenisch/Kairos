package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Consolidates duplicate DOCKER resources that share the same normalized target.
 *
 * <p>The keeper is chosen by oldest check history (MIN(checked_at)); ties fall
 * back to earliest created_at and then lowest resource id. Check history and
 * outage rows are re-linked to the keeper before duplicate resources are removed.
 */
public class V15__deduplicate_docker_resources_by_target extends BaseJavaMigration {

    private record DockerResourceRow(Long id, String target, Long groupId, LocalDateTime createdAt) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();

        if (!hasTable(metaData, "MONITORED_RESOURCE")) {
            return;
        }

        boolean hasResourceType = hasColumn(metaData, "MONITORED_RESOURCE", "RESOURCE_TYPE");
        boolean hasTarget = hasColumn(metaData, "MONITORED_RESOURCE", "TARGET");
        boolean hasGroupId = hasColumn(metaData, "MONITORED_RESOURCE", "RESOURCE_GROUP_ID");
        boolean hasCreatedAt = hasColumn(metaData, "MONITORED_RESOURCE", "CREATED_AT");

        if (!hasResourceType || !hasTarget) {
            return;
        }

        boolean historyAvailable = hasTable(metaData, "CHECK_RESULT")
                && hasColumn(metaData, "CHECK_RESULT", "RESOURCE_ID")
                && hasColumn(metaData, "CHECK_RESULT", "CHECKED_AT");
        boolean outageRelinkAvailable = hasTable(metaData, "OUTAGE")
                && hasColumn(metaData, "OUTAGE", "RESOURCE_ID");

        Map<String, List<DockerResourceRow>> byNormalizedTarget = new LinkedHashMap<>();
        String selectSql = "SELECT id, target"
                + (hasGroupId ? ", resource_group_id" : "")
                + (hasCreatedAt ? ", created_at" : "")
                + " FROM monitored_resource WHERE resource_type = 'DOCKER'";
        try (PreparedStatement ps = connection.prepareStatement(selectSql)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Long id = rs.getLong("id");
                    String target = rs.getString("target");
                    if (target == null || target.isBlank()) {
                        continue;
                    }
                    Long groupId = hasGroupId ? (Long) rs.getObject("resource_group_id") : null;
                    Timestamp createdAtTs = hasCreatedAt ? rs.getTimestamp("created_at") : null;
                    LocalDateTime createdAt = createdAtTs == null ? null : createdAtTs.toLocalDateTime();

                    String key = normalizeTarget(target);
                    byNormalizedTarget.computeIfAbsent(key, ignored -> new ArrayList<>())
                            .add(new DockerResourceRow(id, target, groupId, createdAt));
                }
            }
        }

        for (List<DockerResourceRow> duplicates : byNormalizedTarget.values()) {
            if (duplicates.size() < 2) {
                continue;
            }

            DockerResourceRow keeper = selectKeeper(connection, duplicates, historyAvailable);
            Long keeperGroupId = keeper.groupId();

            for (DockerResourceRow duplicate : duplicates) {
                if (duplicate.id().equals(keeper.id())) {
                    continue;
                }

                if (keeperGroupId == null && duplicate.groupId() != null) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE monitored_resource SET resource_group_id = ? WHERE id = ?")) {
                        ps.setLong(1, duplicate.groupId());
                        ps.setLong(2, keeper.id());
                        ps.executeUpdate();
                    }
                    keeperGroupId = duplicate.groupId();
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE check_result SET resource_id = ? WHERE resource_id = ?")) {
                    ps.setLong(1, keeper.id());
                    ps.setLong(2, duplicate.id());
                    ps.executeUpdate();
                }

                if (outageRelinkAvailable) {
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE outage SET resource_id = ? WHERE resource_id = ?")) {
                        ps.setLong(1, keeper.id());
                        ps.setLong(2, duplicate.id());
                        ps.executeUpdate();
                    }
                }

                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM monitored_resource WHERE id = ?")) {
                    ps.setLong(1, duplicate.id());
                    ps.executeUpdate();
                }
            }
        }
    }

    private DockerResourceRow selectKeeper(Connection connection,
                                           List<DockerResourceRow> candidates,
                                           boolean historyAvailable) throws Exception {
        DockerResourceRow winner = null;
        LocalDateTime winnerOldestHistory = null;

        for (DockerResourceRow candidate : candidates) {
            LocalDateTime candidateOldestHistory = oldestHistoryAt(connection, candidate.id(), historyAvailable);
            if (winner == null || isBetterCandidate(candidate, candidateOldestHistory, winner, winnerOldestHistory)) {
                winner = candidate;
                winnerOldestHistory = candidateOldestHistory;
            }
        }

        return winner;
    }

    private LocalDateTime oldestHistoryAt(Connection connection, Long resourceId, boolean historyAvailable) throws Exception {
        if (!historyAvailable) {
            return null;
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT MIN(checked_at) AS oldest FROM check_result WHERE resource_id = ?")) {
            ps.setLong(1, resourceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Timestamp ts = rs.getTimestamp("oldest");
                return ts == null ? null : ts.toLocalDateTime();
            }
        }
    }

    private boolean isBetterCandidate(DockerResourceRow candidate,
                                      LocalDateTime candidateOldestHistory,
                                      DockerResourceRow currentWinner,
                                      LocalDateTime winnerOldestHistory) {
        if (candidateOldestHistory != null && winnerOldestHistory == null) {
            return true;
        }
        if (candidateOldestHistory == null && winnerOldestHistory != null) {
            return false;
        }
        if (candidateOldestHistory != null) {
            int historyCompare = candidateOldestHistory.compareTo(winnerOldestHistory);
            if (historyCompare != 0) {
                return historyCompare < 0;
            }
        }

        if (candidate.createdAt() != null && currentWinner.createdAt() == null) {
            return true;
        }
        if (candidate.createdAt() == null && currentWinner.createdAt() != null) {
            return false;
        }
        if (candidate.createdAt() != null) {
            int createdCompare = candidate.createdAt().compareTo(currentWinner.createdAt());
            if (createdCompare != 0) {
                return createdCompare < 0;
            }
        }

        return candidate.id() < currentWinner.id();
    }

    private String normalizeTarget(String target) {
        return target.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasTable(DatabaseMetaData metaData, String tableName) throws Exception {
        try (ResultSet rs = metaData.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private boolean hasColumn(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }
}
package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Replaces monitored_resource.resource_group_id with the resource_group_resource join table.
 *
 * <p>This migration is defensive for fresh schemas where JPA-managed tables may not exist yet
 * when Flyway runs. In that case, it safely no-ops.</p>
 */
public class V16__resource_multi_group extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData metaData = connection.getMetaData();

        if (!hasTable(metaData, "MONITORED_RESOURCE") || !hasTable(metaData, "RESOURCE_GROUP")) {
            return;
        }

        ensureJoinTableExists(connection);

        boolean hasLegacyColumn = hasColumn(metaData, "MONITORED_RESOURCE", "RESOURCE_GROUP_ID");
        if (!hasLegacyColumn) {
            return;
        }

        // Copy legacy single-group assignments into the join table without duplicates.
        try (PreparedStatement ps = connection.prepareStatement(
                """
                INSERT INTO resource_group_resource (resource_id, resource_group_id)
                SELECT m.id, m.resource_group_id
                FROM monitored_resource m
                WHERE m.resource_group_id IS NOT NULL
                  AND NOT EXISTS (
                      SELECT 1
                      FROM resource_group_resource x
                      WHERE x.resource_id = m.id
                        AND x.resource_group_id = m.resource_group_id
                  )
                """)) {
            ps.executeUpdate();
        }

        // Remove the now-redundant legacy column.
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE monitored_resource DROP COLUMN resource_group_id");
        }

        // Recreate constraints if they are not present yet.
        if (!hasForeignKey(metaData, "RESOURCE_GROUP_RESOURCE", "FK_RGR_RESOURCE")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "ALTER TABLE resource_group_resource "
                                + "ADD CONSTRAINT fk_rgr_resource "
                                + "FOREIGN KEY (resource_id) REFERENCES monitored_resource (id)");
            }
        }

        if (!hasForeignKey(metaData, "RESOURCE_GROUP_RESOURCE", "FK_RGR_GROUP")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(
                        "ALTER TABLE resource_group_resource "
                                + "ADD CONSTRAINT fk_rgr_group "
                                + "FOREIGN KEY (resource_group_id) REFERENCES resource_group (id)");
            }
        }
    }

    private void ensureJoinTableExists(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS resource_group_resource (
                        resource_id       BIGINT NOT NULL,
                        resource_group_id BIGINT NOT NULL,
                        CONSTRAINT pk_resource_group_resource PRIMARY KEY (resource_id, resource_group_id)
                    )
                    """);
        }
    }

    private boolean hasTable(DatabaseMetaData metaData, String tableName) throws Exception {
        try (ResultSet rs = metaData.getTables(null, null, null, new String[]{"TABLE"})) {
            while (rs.next()) {
                String current = rs.getString("TABLE_NAME");
                if (current != null && current.equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasColumn(DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet rs = metaData.getColumns(null, null, null, null)) {
            while (rs.next()) {
                String currentTable = rs.getString("TABLE_NAME");
                String currentColumn = rs.getString("COLUMN_NAME");
                if (currentTable != null
                        && currentColumn != null
                        && currentTable.equalsIgnoreCase(tableName)
                        && currentColumn.equalsIgnoreCase(columnName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasForeignKey(DatabaseMetaData metaData, String tableName, String fkName) throws Exception {
        try (ResultSet rs = metaData.getImportedKeys(null, null, null)) {
            while (rs.next()) {
                String fkTable = rs.getString("FKTABLE_NAME");
                String currentFkName = rs.getString("FK_NAME");
                if (fkTable != null
                        && currentFkName != null
                        && fkTable.equalsIgnoreCase(tableName)
                        && currentFkName.equalsIgnoreCase(fkName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Adds check history retention configuration columns to resource_type_config.
 * Creates an index on check_result.checked_at for efficient retention cleanup queries.
 */
public class V6__add_check_history_retention_config extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        addRetentionColumnsToResourceTypeConfig(context);
        createCheckResultCheckedAtIndex(context);
    }

    private void addRetentionColumnsToResourceTypeConfig(Context context) throws Exception {
        if (!tableExists(context, "resource_type_config")) {
            return;
        }

        try (Statement st = context.getConnection().createStatement()) {
            // Add retention enabled column
            if (!columnExists(context, "resource_type_config", "check_history_retention_enabled")) {
                st.executeUpdate(
                    "ALTER TABLE resource_type_config " +
                    "ADD COLUMN check_history_retention_enabled BOOLEAN DEFAULT TRUE NOT NULL"
                );
            }

            // Add retention interval minutes column
            if (!columnExists(context, "resource_type_config", "check_history_retention_interval_minutes")) {
                st.executeUpdate(
                    "ALTER TABLE resource_type_config " +
                    "ADD COLUMN check_history_retention_interval_minutes INT DEFAULT 60 NOT NULL"
                );
            }

            // Add retention days column
            if (!columnExists(context, "resource_type_config", "check_history_retention_days")) {
                st.executeUpdate(
                    "ALTER TABLE resource_type_config " +
                    "ADD COLUMN check_history_retention_days INT DEFAULT 31 NOT NULL"
                );
            }
        }
    }

    private void createCheckResultCheckedAtIndex(Context context) throws Exception {
        if (!tableExists(context, "check_result")) {
            return;
        }

        try (Statement st = context.getConnection().createStatement()) {
            // Create index if it doesn't exist (database-agnostic via metadata check)
            if (!indexExists(context, "check_result", "idx_check_result_checked_at")) {
                st.executeUpdate(
                    "CREATE INDEX idx_check_result_checked_at ON check_result (checked_at)"
                );
            }
        }
    }

    private boolean tableExists(Context context, String tableName) throws Exception {
        DatabaseMetaData metaData = context.getConnection().getMetaData();
        try (ResultSet upper = metaData.getTables(null, null, tableName.toUpperCase(), null)) {
            if (upper.next()) {
                return true;
            }
        }
        try (ResultSet lower = metaData.getTables(null, null, tableName.toLowerCase(), null)) {
            return lower.next();
        }
    }

    private boolean columnExists(Context context, String tableName, String columnName) throws Exception {
        DatabaseMetaData metaData = context.getConnection().getMetaData();
        try (ResultSet upper = metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (upper.next()) {
                return true;
            }
        }
        try (ResultSet lower = metaData.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
            return lower.next();
        }
    }

    private boolean indexExists(Context context, String tableName, String indexName) throws Exception {
        DatabaseMetaData metaData = context.getConnection().getMetaData();
        try (ResultSet upper = metaData.getIndexInfo(null, null, tableName.toUpperCase(), false, false)) {
            while (upper.next()) {
                String idxName = upper.getString("INDEX_NAME");
                if (idxName != null && idxName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        try (ResultSet lower = metaData.getIndexInfo(null, null, tableName.toLowerCase(), false, false)) {
            while (lower.next()) {
                String idxName = lower.getString("INDEX_NAME");
                if (idxName != null && idxName.equalsIgnoreCase(indexName)) {
                    return true;
                }
            }
        }
        return false;
    }
}

package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Adds outage retention configuration columns to resource_type_config.
 * Creates an index on outage.end_date for efficient retention cleanup queries.
 */
public class V18__add_outage_retention_config extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        addOutageRetentionColumnsToResourceTypeConfig(context);
        createOutageEndDateIndex(context);
    }

    private void addOutageRetentionColumnsToResourceTypeConfig(Context context) throws Exception {
        if (!tableExists(context, "resource_type_config")) {
            return;
        }

        try (Statement st = context.getConnection().createStatement()) {
            if (!columnExists(context, "resource_type_config", "outage_retention_enabled")) {
                st.executeUpdate(
                        "ALTER TABLE resource_type_config " +
                        "ADD COLUMN outage_retention_enabled BOOLEAN DEFAULT TRUE NOT NULL"
                );
            }

            if (!columnExists(context, "resource_type_config", "outage_retention_interval_hours")) {
                st.executeUpdate(
                        "ALTER TABLE resource_type_config " +
                        "ADD COLUMN outage_retention_interval_hours INT DEFAULT 12 NOT NULL"
                );
            }

            if (!columnExists(context, "resource_type_config", "outage_retention_days")) {
                st.executeUpdate(
                        "ALTER TABLE resource_type_config " +
                        "ADD COLUMN outage_retention_days INT DEFAULT 31 NOT NULL"
                );
            }
        }
    }

    private void createOutageEndDateIndex(Context context) throws Exception {
        if (!tableExists(context, "outage")) {
            return;
        }

        try (Statement st = context.getConnection().createStatement()) {
            if (!indexExists(context, "outage", "idx_outage_end_date")) {
                st.executeUpdate("CREATE INDEX idx_outage_end_date ON outage (end_date)");
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

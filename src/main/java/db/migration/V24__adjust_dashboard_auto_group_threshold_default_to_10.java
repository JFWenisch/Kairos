package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Adjusts dashboard_auto_group_threshold default from 15 to 10.
 */
public class V24__adjust_dashboard_auto_group_threshold_default_to_10 extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!columnExists(context, "resource_type_config", "dashboard_auto_group_threshold")) {
            return;
        }

        try (Statement st = context.getConnection().createStatement()) {
            // Align bootstrap/defaulted values from V23 to the new default.
            st.executeUpdate(
                "UPDATE resource_type_config "
                    + "SET dashboard_auto_group_threshold = 10 "
                    + "WHERE dashboard_auto_group_threshold IS NULL "
                    + "OR dashboard_auto_group_threshold < 1 "
                    + "OR dashboard_auto_group_threshold = 15"
            );

            String databaseProductName = context.getConnection().getMetaData().getDatabaseProductName();
            if (databaseProductName != null && databaseProductName.toLowerCase().contains("postgresql")) {
                st.executeUpdate(
                    "ALTER TABLE resource_type_config "
                        + "ALTER COLUMN dashboard_auto_group_threshold SET DEFAULT 10"
                );
            }
        }
    }

    private boolean columnExists(Context context, String tableName, String columnName) throws Exception {
        var metaData = context.getConnection().getMetaData();

        try (ResultSet upper = metaData.getColumns(null, null, tableName.toUpperCase(), columnName.toUpperCase())) {
            if (upper.next()) {
                return true;
            }
        }

        try (ResultSet lower = metaData.getColumns(null, null, tableName.toLowerCase(), columnName.toLowerCase())) {
            return lower.next();
        }
    }
}

package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Adds a global dashboard auto-group threshold to resource_type_config.
 */
public class V23__add_dashboard_auto_group_threshold extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            if (!columnExists(context, "resource_type_config", "dashboard_auto_group_threshold")) {
                st.executeUpdate(
                    "ALTER TABLE resource_type_config ADD COLUMN dashboard_auto_group_threshold INT DEFAULT 15 NOT NULL"
                );
            }

            st.executeUpdate(
                "UPDATE resource_type_config "
                    + "SET dashboard_auto_group_threshold = 15 "
                    + "WHERE dashboard_auto_group_threshold IS NULL OR dashboard_auto_group_threshold < 1"
            );
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

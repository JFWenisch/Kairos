package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Adds global instant-check settings to resource_type_config.
 */
public class V20__add_instant_check_settings extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            if (!columnExists(context, "resource_type_config", "instant_check_enabled")) {
                st.executeUpdate("ALTER TABLE resource_type_config ADD COLUMN instant_check_enabled BOOLEAN DEFAULT FALSE");
            }
            if (!columnExists(context, "resource_type_config", "instant_check_allow_public")) {
                st.executeUpdate("ALTER TABLE resource_type_config ADD COLUMN instant_check_allow_public BOOLEAN DEFAULT FALSE");
            }
            if (!columnExists(context, "resource_type_config", "instant_check_use_stored_auth")) {
                st.executeUpdate("ALTER TABLE resource_type_config ADD COLUMN instant_check_use_stored_auth BOOLEAN DEFAULT FALSE");
            }
            if (!columnExists(context, "resource_type_config", "instant_check_allowed_domains")) {
                st.executeUpdate("ALTER TABLE resource_type_config ADD COLUMN instant_check_allowed_domains VARCHAR(4000) DEFAULT '*'");
            }

            st.executeUpdate("UPDATE resource_type_config SET instant_check_enabled = FALSE WHERE instant_check_enabled IS NULL");
            st.executeUpdate("UPDATE resource_type_config SET instant_check_allow_public = FALSE WHERE instant_check_allow_public IS NULL");
            st.executeUpdate("UPDATE resource_type_config SET instant_check_use_stored_auth = FALSE WHERE instant_check_use_stored_auth IS NULL");
            st.executeUpdate("UPDATE resource_type_config SET instant_check_allowed_domains = '*' WHERE instant_check_allowed_domains IS NULL OR TRIM(instant_check_allowed_domains) = ''");
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

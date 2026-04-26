package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Creates the custom_header_settings table and seeds the singleton row.
 */
public class V17__add_custom_header_settings extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            if (!tableExists(context, "custom_header_settings")) {
                st.executeUpdate(
                    "CREATE TABLE custom_header_settings (" +
                    "  id BIGINT PRIMARY KEY, " +
                    "  content TEXT, " +
                    "  apply_to_admin BOOLEAN NOT NULL DEFAULT FALSE" +
                    ")"
                );
            }

            try (ResultSet rs = context.getConnection().createStatement()
                    .executeQuery("SELECT COUNT(*) FROM custom_header_settings WHERE id = 1")) {
                if (rs.next() && rs.getLong(1) == 0) {
                    st.executeUpdate(
                        "INSERT INTO custom_header_settings (id, content, apply_to_admin) VALUES (1, '', FALSE)"
                    );
                }
            }
        }
    }

    private boolean tableExists(Context context, String tableName) throws Exception {
        DatabaseMetaData meta = context.getConnection().getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            if (rs.next()) return true;
        }
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}

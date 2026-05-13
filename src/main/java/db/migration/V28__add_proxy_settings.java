package db.migration;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V28__add_proxy_settings extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement st = context.getConnection().createStatement()) {
            if (!tableExists(context, "proxy_settings")) {
                st.executeUpdate(
                        "CREATE TABLE proxy_settings ("
                                + "id BIGINT PRIMARY KEY, "
                                + "proxy_enabled BOOLEAN NOT NULL DEFAULT FALSE, "
                                + "http_proxy_enabled BOOLEAN NOT NULL DEFAULT FALSE, "
                                + "http_proxy_host VARCHAR(255), "
                                + "http_proxy_port INTEGER, "
                                + "socks_proxy_enabled BOOLEAN NOT NULL DEFAULT FALSE, "
                                + "socks_proxy_host VARCHAR(255), "
                                + "socks_proxy_port INTEGER, "
                                + "proxy_username VARCHAR(255), "
                                + "proxy_password VARCHAR(4000), "
                                + "mode VARCHAR(32) NOT NULL DEFAULT 'BLACKLIST', "
                                + "target_rules TEXT"
                                + ")"
                );
            }

            try (ResultSet rs = context.getConnection().createStatement()
                    .executeQuery("SELECT COUNT(*) FROM proxy_settings WHERE id = 1")) {
                if (rs.next() && rs.getLong(1) == 0) {
                    st.executeUpdate(
                            "INSERT INTO proxy_settings ("
                                    + "id, proxy_enabled, http_proxy_enabled, socks_proxy_enabled, mode, target_rules"
                                    + ") VALUES (1, FALSE, FALSE, FALSE, 'BLACKLIST', '')"
                    );
                }
            }
        }
    }

    private boolean tableExists(Context context, String tableName) throws Exception {
        DatabaseMetaData meta = context.getConnection().getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
            if (rs.next()) {
                return true;
            }
        }
        try (ResultSet rs = meta.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}

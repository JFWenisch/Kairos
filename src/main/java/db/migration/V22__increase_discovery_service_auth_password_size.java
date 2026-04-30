package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Increases the password column size in discovery_service_auth table to support long tokens (JWT, OAuth, etc).
 */
public class V22__increase_discovery_service_auth_password_size extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        try (Statement stmt = context.getConnection().createStatement()) {
            if (!tableExists(context, "discovery_service_auth") || isPasswordColumnWideEnough(context)) {
                return;
            }

            String product = context.getConnection().getMetaData().getDatabaseProductName().toLowerCase();

            if (product.contains("h2")) {
                stmt.executeUpdate("ALTER TABLE discovery_service_auth ALTER COLUMN password SET DATA TYPE VARCHAR(4000)");
            } else if (product.contains("postgres")) {
                stmt.executeUpdate("ALTER TABLE discovery_service_auth ALTER COLUMN password TYPE VARCHAR(4000)");
            } else if (product.contains("mysql") || product.contains("mariadb")) {
                stmt.executeUpdate("ALTER TABLE discovery_service_auth MODIFY password VARCHAR(4000)");
            } else {
                throw new IllegalStateException("Unsupported database for V22 password column migration: " + product);
            }
        }
    }

    private boolean tableExists(Context context, String tableName) throws Exception {
        var metaData = context.getConnection().getMetaData();

        try (ResultSet upper = metaData.getTables(null, null, tableName.toUpperCase(), null)) {
            if (upper.next()) {
                return true;
            }
        }

        try (ResultSet lower = metaData.getTables(null, null, tableName.toLowerCase(), null)) {
            return lower.next();
        }
    }

    private boolean isPasswordColumnWideEnough(Context context) throws Exception {
        var metaData = context.getConnection().getMetaData();

        Integer upperSize = readColumnSize(metaData, "DISCOVERY_SERVICE_AUTH", "PASSWORD");
        if (upperSize != null) {
            return upperSize >= 4000;
        }

        Integer lowerSize = readColumnSize(metaData, "discovery_service_auth", "password");
        return lowerSize != null && lowerSize >= 4000;
    }

    private Integer readColumnSize(java.sql.DatabaseMetaData metaData, String tableName, String columnName) throws Exception {
        try (ResultSet rs = metaData.getColumns(null, null, tableName, columnName)) {
            if (rs.next()) {
                return rs.getInt("COLUMN_SIZE");
            }
        }
        return null;
    }
}

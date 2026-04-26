package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * Reclassifies DB-infrastructure related check failures from NOT_AVAILABLE to UNKNOWN.
 *
 * The table may not exist yet on fresh installs because core schema creation happens
 * later via JPA ddl-auto, so this migration must be a no-op in that case.
 */
public class V10__convert_jpa_jdbc_not_available_to_unknown extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!tableExists(context, "check_result")) {
            return;
        }

        try (PreparedStatement ps = context.getConnection().prepareStatement(
                """
                UPDATE check_result
                   SET status = 'UNKNOWN'
                 WHERE status = 'NOT_AVAILABLE'
                   AND (
                        UPPER(COALESCE(message, '')) LIKE '%JDBC%'
                        OR UPPER(COALESCE(message, '')) LIKE '%JPA%'
                   )
                """)) {
            ps.executeUpdate();
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
}

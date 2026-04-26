package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

public class V2__expand_resource_type_for_dockerrepository extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        if (!tableExists(context)) {
            return;
        }

        String product = context.getConnection().getMetaData().getDatabaseProductName().toLowerCase();
        if (product.contains("h2")) {
            migrateH2(context);
            return;
        }

        if (product.contains("postgresql")) {
            migratePostgres(context);
        }
    }

    private boolean tableExists(Context context) throws Exception {
        DatabaseMetaData metaData = context.getConnection().getMetaData();
        try (ResultSet resultSet = metaData.getTables(null, null, "MONITORED_RESOURCE", null)) {
            if (resultSet.next()) {
                return true;
            }
        }

        try (ResultSet resultSet = metaData.getTables(null, null, "monitored_resource", null)) {
            return resultSet.next();
        }
    }

    private void migrateH2(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    ALTER TABLE IF EXISTS monitored_resource
                    ALTER COLUMN resource_type ENUM('HTTP','DOCKER','DOCKERREPOSITORY')
                    """);
        }
    }

    private void migratePostgres(Context context) throws Exception {
        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("""
                    DO $$
                    DECLARE c RECORD;
                    BEGIN
                        FOR c IN
                            SELECT con.conname AS name
                            FROM pg_constraint con
                            JOIN pg_class rel ON rel.oid = con.conrelid
                            JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                            WHERE rel.relname = 'monitored_resource'
                              AND con.contype = 'c'
                              AND pg_get_constraintdef(con.oid) ILIKE '%resource_type%'
                        LOOP
                            EXECUTE format('ALTER TABLE monitored_resource DROP CONSTRAINT %I', c.name);
                        END LOOP;
                    END $$;
                    """);

            statement.execute("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1
                            FROM pg_constraint con
                            JOIN pg_class rel ON rel.oid = con.conrelid
                            WHERE rel.relname = 'monitored_resource'
                              AND con.conname = 'monitored_resource_resource_type_check'
                        ) THEN
                            ALTER TABLE monitored_resource
                            ADD CONSTRAINT monitored_resource_resource_type_check
                            CHECK (resource_type IN ('HTTP','DOCKER','DOCKERREPOSITORY'));
                        END IF;
                    END $$;
                    """);
        }
    }
}

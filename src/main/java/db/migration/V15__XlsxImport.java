package db.migration;

import java.util.*;
import org.flywaydb.core.api.migration.*;

/** Conserva lotes CSV y permite una importación independiente por hoja del mismo libro. */
public class V15__XlsxImport extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 2026101515; }
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection(); var columns=new HashMap<String,Set<String>>();
        try(var q=connection.prepareStatement("""
                SELECT t.constraint_name,k.column_name FROM information_schema.table_constraints t
                JOIN information_schema.key_column_usage k ON t.constraint_name=k.constraint_name AND t.constraint_schema=k.constraint_schema
                WHERE LOWER(t.table_name)='csv_import_batch' AND t.constraint_schema=? AND t.constraint_type='UNIQUE'
                """)) {
            q.setString(1,connection.getSchema()); try(var r=q.executeQuery()) { while(r.next()) columns.computeIfAbsent(r.getString(1),k -> new HashSet<>()).add(r.getString(2).toLowerCase(Locale.ROOT)); }
        }
        var names=columns.entrySet().stream().filter(e -> e.getValue().equals(Set.of("version_id","source_hash"))).map(Map.Entry::getKey).toList();
        if(names.size()!=1) throw new IllegalStateException("No se identificó la restricción del original CSV.");
        try(var sql=connection.createStatement()) {
            sql.execute("ALTER TABLE csv_import_batch DROP CONSTRAINT \""+names.getFirst().replace("\"","\"\"")+"\"");
            sql.execute("ALTER TABLE csv_import_batch ADD COLUMN source_sheet INTEGER NOT NULL DEFAULT 0");
            sql.execute("ALTER TABLE csv_import_batch ADD CONSTRAINT uq_import_source_sheet UNIQUE(version_id,source_hash,source_sheet)");
            sql.execute("ALTER TABLE csv_import_batch ADD CONSTRAINT ck_import_source_sheet CHECK(source_sheet BETWEEN 0 AND 30)");
            sql.execute("""
                    CREATE TABLE xlsx_import_workbook (
                      id VARCHAR(36) PRIMARY KEY, version_id VARCHAR(36) NOT NULL REFERENCES survey_version(id),
                      file_name VARCHAR(200) NOT NULL, source_base64 TEXT NOT NULL, source_hash VARCHAR(64) NOT NULL,
                      platform VARCHAR(40) NOT NULL, actor VARCHAR(100) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
                      UNIQUE(version_id,source_hash)
                    )
                    """);
        }
    }
}

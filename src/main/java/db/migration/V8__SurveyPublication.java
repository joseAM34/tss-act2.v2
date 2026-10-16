package db.migration;

import java.sql.Connection;
import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Amplía V7 sin modificarla; su CHECK anónimo tiene nombres distintos en H2/PostgreSQL. */
public class V8__SurveyPublication extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 2026101408; }

    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        var names = new ArrayList<String>();
        try (var query = connection.prepareStatement("""
                SELECT t.constraint_name, c.check_clause FROM information_schema.table_constraints t
                JOIN information_schema.check_constraints c ON t.constraint_name = c.constraint_name
                    AND t.constraint_schema = c.constraint_schema
                WHERE LOWER(t.table_name) = 'survey_version' AND t.constraint_schema = ? AND t.constraint_type = 'CHECK'
                """)) {
            query.setString(1, connection.getSchema());
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    String clause = result.getString(2).replace("::text", "").replace("::character varying", "")
                            .replaceAll("[\\s\"()]", "");
                    if (clause.equalsIgnoreCase("status='BORRADOR'")) names.add(result.getString(1));
                }
            }
        }
        if (names.size() != 1) throw new IllegalStateException("No se identificó de forma única la restricción de estado de V7.");
        try (var sql = connection.createStatement()) {
            sql.execute("ALTER TABLE survey_version DROP CONSTRAINT \"" + names.getFirst().replace("\"", "\"\"") + "\"");
            sql.execute("ALTER TABLE survey_version ADD CONSTRAINT ck_survey_version_status CHECK (status IN ('BORRADOR', 'PUBLICADA', 'CERRADA'))");
            sql.execute("ALTER TABLE survey_version ADD COLUMN definition_json TEXT");
            sql.execute("ALTER TABLE survey_version ADD COLUMN definition_hash VARCHAR(64)");
            sql.execute("ALTER TABLE survey_version ADD COLUMN published_at TIMESTAMP WITH TIME ZONE");
            sql.execute("ALTER TABLE survey_version ADD COLUMN closed_at TIMESTAMP WITH TIME ZONE");
            sql.execute("ALTER TABLE survey_version ADD COLUMN publication_number INTEGER");
            sql.execute("ALTER TABLE survey_version ADD COLUMN predecessor_id VARCHAR(36) REFERENCES survey_version(id)");
            sql.execute("ALTER TABLE survey_version ADD CONSTRAINT uq_survey_publication_number UNIQUE (survey_id, publication_number)");
            sql.execute("ALTER TABLE survey_version ADD CONSTRAINT ck_survey_publication_fields CHECK ((status = 'BORRADOR' AND publication_number IS NULL AND published_at IS NULL AND closed_at IS NULL AND definition_json IS NULL AND definition_hash IS NULL) OR (status IN ('PUBLICADA', 'CERRADA') AND publication_number IS NOT NULL AND publication_number > 0 AND published_at IS NOT NULL AND definition_json IS NOT NULL AND definition_hash IS NOT NULL AND ((status = 'PUBLICADA' AND closed_at IS NULL) OR (status = 'CERRADA' AND closed_at IS NOT NULL))))");
            sql.execute("ALTER TABLE survey ADD COLUMN next_publication_number INTEGER NOT NULL DEFAULT 1");
            sql.execute("ALTER TABLE survey ADD CONSTRAINT ck_survey_next_publication CHECK (next_publication_number > 0)");
            sql.execute("ALTER TABLE survey ADD COLUMN draft_version_id VARCHAR(36) REFERENCES survey_version(id)");
            sql.execute("UPDATE survey SET draft_version_id = (SELECT v.id FROM survey_version v WHERE v.survey_id = survey.id AND v.status = 'BORRADOR')");
        }
    }
}

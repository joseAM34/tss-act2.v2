package db.migration;

import java.util.ArrayList;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Conserva envíos históricos y añade vínculos verificables para capturas dinámicas. */
public class V9__DynamicSurveyResponses extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 2026101409; }
    @Override public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var names = new ArrayList<String>();
        try (var query = connection.prepareStatement("""
                SELECT t.constraint_name, c.check_clause FROM information_schema.table_constraints t
                JOIN information_schema.check_constraints c ON t.constraint_name = c.constraint_name AND t.constraint_schema = c.constraint_schema
                WHERE LOWER(t.table_name) = 'survey_response' AND t.constraint_schema = ? AND t.constraint_type = 'CHECK'
                """)) {
            query.setString(1, connection.getSchema());
            try (var result = query.executeQuery()) {
                while (result.next()) {
                    String clause = result.getString(2).toLowerCase(java.util.Locale.ROOT);
                    if (clause.contains("survey_slug") && clause.contains("titulados") && clause.contains("empleadores") && !clause.contains("consent")) names.add(result.getString(1));
                }
            }
        }
        if (names.size() != 1) throw new IllegalStateException("No se identificó de forma única el CHECK de encuesta de V1.");
        try (var sql = connection.createStatement()) {
            sql.execute("ALTER TABLE survey_response DROP CONSTRAINT \"" + names.getFirst().replace("\"", "\"\"") + "\"");
            sql.execute("ALTER TABLE survey_version ADD CONSTRAINT uq_version_survey_binding UNIQUE (id, survey_id)");
            sql.execute("ALTER TABLE survey_version ADD CONSTRAINT uq_publication_capture_binding UNIQUE (id, survey_id, version_code, definition_hash)");
            sql.execute("ALTER TABLE survey ADD CONSTRAINT uq_survey_slug_binding UNIQUE (id, slug)");
            sql.execute("ALTER TABLE survey ADD COLUMN active_version_id VARCHAR(36)");
            sql.execute("ALTER TABLE survey ADD CONSTRAINT fk_survey_active_version FOREIGN KEY (active_version_id, id) REFERENCES survey_version(id, survey_id)");
            sql.execute("ALTER TABLE survey_response ADD COLUMN publication_version_id VARCHAR(36)");
            sql.execute("ALTER TABLE survey_response ADD COLUMN dynamic_survey_id VARCHAR(36)");
            sql.execute("ALTER TABLE survey_response ADD COLUMN publication_hash VARCHAR(64)");
            sql.execute("ALTER TABLE survey_response ADD CONSTRAINT fk_response_publication FOREIGN KEY (publication_version_id, dynamic_survey_id, instrument_version, publication_hash) REFERENCES survey_version(id, survey_id, version_code, definition_hash)");
            sql.execute("ALTER TABLE survey_response ADD CONSTRAINT fk_response_survey FOREIGN KEY (dynamic_survey_id, survey_slug) REFERENCES survey(id, slug)");
            sql.execute("ALTER TABLE survey_response ADD CONSTRAINT ck_response_instrument_binding CHECK ((publication_version_id IS NULL AND dynamic_survey_id IS NULL AND publication_hash IS NULL AND survey_slug IN ('titulados', 'empleadores')) OR (publication_version_id IS NOT NULL AND dynamic_survey_id IS NOT NULL AND publication_hash IS NOT NULL))");
        }
    }
}

package bo.edu.umss.mercado.survey;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Gestión de la encuesta completa; conserva los originales que ya tienen datos asociados. */
@Service
public class SurveyManagementService {
    private final JdbcTemplate jdbc;
    public SurveyManagementService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record State(String id, String slug, String title, boolean archived, int versions,
                        long responses, long imports, long workbooks, long configurations, long preparations,
                        boolean deletable, String hash) {}

    @Transactional(readOnly = true)
    public State get(String slug) { return snapshot(slug).state(); }

    @Transactional
    public void archive(String slug, String expectedHash, String actor) {
        Snapshot snapshot = checked(slug, expectedHash, actor);
        if (snapshot.state().archived()) throw conflict("Esta encuesta ya está archivada.");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // El bloqueo de encuesta coincide con publicación y recepción: no entra un envío nuevo después del cierre.
        for (var row : jdbc.queryForList("SELECT * FROM survey_version WHERE survey_id = ? AND status = 'PUBLICADA' ORDER BY id", snapshot.state().id())) {
            String id = (String) row.get("id");
            long revision = ((Number) row.get("revision")).longValue();
            jdbc.update("UPDATE survey_version SET status = 'CERRADA', closed_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ?", now, now, id);
            jdbc.update("INSERT INTO survey_definition_event VALUES (?, ?, ?, 'ARCHIVAR', ?, ?, ?, ?)",
                    UUID.randomUUID().toString(), id, revision + 1, actor, now, row.get("definition_json"), row.get("definition_hash"));
        }
        jdbc.update("UPDATE survey SET archived = TRUE, active_version_id = NULL WHERE id = ?", snapshot.state().id());
        event(snapshot, "ARCHIVAR", actor);
    }

    @Transactional
    public void restore(String slug, String expectedHash, String actor) {
        Snapshot snapshot = checked(slug, expectedHash, actor);
        if (!snapshot.state().archived()) throw conflict("Esta encuesta ya está disponible.");
        // Recuperar el espacio no reabre publicaciones cerradas ni cambia su definición.
        jdbc.update("UPDATE survey SET archived = FALSE WHERE id = ?", snapshot.state().id());
        event(snapshot, "RESTAURAR", actor);
    }

    @Transactional
    public void delete(String slug, String expectedHash, String actor) {
        Snapshot snapshot = checked(slug, expectedHash, actor);
        if (!snapshot.state().deletable()) throw conflict("Esta encuesta tiene respuestas, importaciones o preparación de datos. Puedes archivarla conservando su historial.");
        String id = snapshot.state().id();
        event(snapshot, "ELIMINAR", actor);
        jdbc.update("UPDATE survey SET active_version_id = NULL, draft_version_id = NULL WHERE id = ?", id);
        jdbc.update("DELETE FROM survey_question_option WHERE version_id IN (SELECT id FROM survey_version WHERE survey_id = ?)", id);
        jdbc.update("DELETE FROM survey_question WHERE version_id IN (SELECT id FROM survey_version WHERE survey_id = ?)", id);
        jdbc.update("DELETE FROM survey_section WHERE version_id IN (SELECT id FROM survey_version WHERE survey_id = ?)", id);
        jdbc.update("DELETE FROM survey_definition_event WHERE version_id IN (SELECT id FROM survey_version WHERE survey_id = ?)", id);
        jdbc.update("UPDATE survey_version SET predecessor_id = NULL WHERE survey_id = ?", id);
        jdbc.update("DELETE FROM survey_version WHERE survey_id = ?", id);
        jdbc.update("DELETE FROM instrument_snapshot WHERE survey_slug = ?", slug);
        jdbc.update("DELETE FROM survey WHERE id = ?", id);
    }

    private record Snapshot(State state, Map<String, Object> content) {}

    private Snapshot checked(String slug, String expectedHash, String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 100) throw new IllegalArgumentException("El administrador no es válido.");
        var ids = jdbc.queryForList("SELECT id FROM survey WHERE slug = ? FOR UPDATE", String.class, slug);
        if (ids.isEmpty()) throw missing();
        // También coordina con las actualizaciones de borrador, que incrementan revision al guardar.
        jdbc.queryForList("SELECT id FROM survey_version WHERE survey_id = ? ORDER BY id FOR UPDATE", String.class, ids.getFirst());
        Snapshot current = snapshot(slug);
        if (expectedHash == null || !current.state().hash().equals(expectedHash))
            throw conflict("La encuesta o sus datos cambiaron. Recarga esta pantalla antes de continuar.");
        return current;
    }

    private Snapshot snapshot(String slug) {
        var surveys = jdbc.queryForList("SELECT id, slug, archived FROM survey WHERE slug = ?", slug);
        if (surveys.isEmpty()) throw missing();
        var survey = surveys.getFirst();
        String id = (String) survey.get("id");
        var versions = jdbc.queryForList("SELECT id, version_code, title, status, revision, definition_hash FROM survey_version WHERE survey_id = ? ORDER BY created_at DESC, id", id);
        if (versions.isEmpty()) throw missing();
        long responses = count("SELECT COUNT(*) FROM survey_response WHERE survey_slug = ?", slug);
        long imports = versionCount("csv_import_batch", id), workbooks = versionCount("xlsx_import_workbook", id);
        long configurations = versionCount("survey_preparation_config", id), preparations = versionCount("survey_preparation_run", id);
        var normalizedVersions = new ArrayList<Map<String, Object>>();
        for (var v : versions) {
            var value = new LinkedHashMap<String, Object>();
            for (String field : List.of("id", "version_code", "title", "status", "revision", "definition_hash")) value.put(field, v.get(field));
            normalizedVersions.add(value);
        }
        var content = new LinkedHashMap<String, Object>();
        content.put("id", id); content.put("slug", slug); content.put("archived", survey.get("archived")); content.put("versions", normalizedVersions);
        content.put("responses", responses); content.put("imports", imports); content.put("workbooks", workbooks);
        content.put("configurations", configurations); content.put("preparations", preparations);
        boolean deletable = responses == 0 && imports == 0 && workbooks == 0 && configurations == 0 && preparations == 0;
        return new Snapshot(new State(id, slug, (String) versions.getFirst().get("title"), (Boolean) survey.get("archived"), versions.size(),
                responses, imports, workbooks, configurations, preparations, deletable, CanonicalJson.hash(content)), content);
    }

    private long versionCount(String table, String id) {
        return count("SELECT COUNT(*) FROM " + table + " WHERE version_id IN (SELECT id FROM survey_version WHERE survey_id = ?)", id);
    }
    private long count(String sql, String id) { return jdbc.queryForObject(sql, Long.class, id); }
    private void event(Snapshot snapshot, String operation, String actor) {
        jdbc.update("INSERT INTO survey_management_event VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(),
                snapshot.state().id(), snapshot.state().slug(), operation, actor, OffsetDateTime.now(ZoneOffset.UTC),
                CanonicalJson.json(snapshot.content()), snapshot.state().hash());
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta administrable no encontrada."); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}

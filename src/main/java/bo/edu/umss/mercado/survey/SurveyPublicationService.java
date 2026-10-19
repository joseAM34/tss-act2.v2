package bo.edu.umss.mercado.survey;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.InstrumentRegistry;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SurveyPublicationService {
    private final JdbcTemplate jdbc;
    private final DraftSurveyService drafts;
    private final InstrumentRegistry registry;
    public SurveyPublicationService(JdbcTemplate jdbc, DraftSurveyService drafts, InstrumentRegistry registry) {
        this.jdbc = jdbc; this.drafts = drafts; this.registry = registry;
    }
    public record Version(String id, String surveyId, String slug, String code, String title, String status,
                          long revision, String publishedAt, String closedAt, String hash, PublishedSurvey content, boolean active) {
        public String publicPath() { return "/encuestas/" + slug + "/versiones/" + code; }
        public String principalPath() { return "/encuestas/" + slug; }
    }
    public record Summary(String id, String title, String code, String status) {}

    public List<Summary> recent() {
        return jdbc.query("SELECT v.id, v.title, v.version_code, v.status FROM survey_version v JOIN survey s ON s.id = v.survey_id WHERE s.archived = FALSE AND v.status IN ('PUBLICADA', 'CERRADA') ORDER BY v.published_at DESC, v.id",
                (rs, n) -> new Summary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)));
    }

    public List<Summary> publishedForSurvey(String surveyId) {
        return jdbc.query("SELECT v.id, v.title, v.version_code, v.status FROM survey_version v JOIN survey s ON s.id = v.survey_id WHERE v.survey_id = ? AND s.archived = FALSE AND v.status = 'PUBLICADA' ORDER BY v.published_at DESC, v.id",
                (rs, n) -> new Summary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)), surveyId);
    }

    @Transactional
    public String publish(String id, long revision, String actor) {
        var initial = drafts.get(id);
        var survey = jdbc.queryForMap("SELECT * FROM survey WHERE id = ? FOR UPDATE", initial.surveyId());
        if (Boolean.TRUE.equals(survey.get("archived"))) throw archived();
        var d = drafts.get(id);
        if (d.revision() != revision || !id.equals(survey.get("draft_version_id"))) throw conflict();
        drafts.validateForPublication(d, actor);
        int number = ((Number) survey.get("next_publication_number")).intValue();
        String code = "v" + number;
        var definition = new SurveyDefinition(d.slug(), d.title(), d.description(), code, d.items().stream().map(DraftSurveyService.Item::question).toList());
        var snapshot = registry.register(definition);
        if (!snapshot.studyId().equals(survey.get("study_id"))) throw new IllegalStateException("El estudio de publicación no coincide con el registro.");
        var frozen = new PublishedSurvey("PUBLICACION_V1", d.surveyId(), snapshot.studyId(), definition,
                d.sections(), d.items(), d.sourceSlug(), d.sourceVersion());
        String json = CanonicalJson.json(frozen), hash = CanonicalJson.hash(frozen);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        int changed = jdbc.update("""
                UPDATE survey_version SET status = 'PUBLICADA', version_code = ?, publication_number = ?,
                    definition_json = ?, definition_hash = ?, published_at = ?, updated_at = ?, revision = revision + 1
                WHERE id = ? AND revision = ? AND status = 'BORRADOR'
                """, code, number, json, hash, now, now, id, revision);
        if (changed != 1) throw conflict();
        jdbc.update("UPDATE survey SET next_publication_number = ?, draft_version_id = NULL WHERE id = ?", Math.addExact(number, 1), d.surveyId());
        event(id, revision + 1, "PUBLICAR", actor, json, hash);
        return id;
    }

    @Transactional(readOnly = true)
    public Version get(String id) {
        var rows = jdbc.queryForList("SELECT v.*, s.slug, s.study_id, s.active_version_id FROM survey_version v JOIN survey s ON s.id = v.survey_id WHERE v.id = ? AND v.status IN ('PUBLICADA', 'CERRADA')", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Versión publicada no encontrada.");
        var row = rows.getFirst();
        PublishedSurvey frozen;
        try { frozen = CanonicalJson.read((String) row.get("definition_json"), PublishedSurvey.class); }
        catch (RuntimeException ex) { throw corrupt(); }
        String hash = (String) row.get("definition_hash");
        var definition = frozen.definition();
        if (frozen.surveyId() == null || frozen.studyId() == null || definition == null) throw corrupt();
        if (!"PUBLICACION_V1".equals(frozen.schemaVersion()) || !hash.equals(CanonicalJson.hash(frozen))
                || !frozen.surveyId().equals(row.get("survey_id")) || !frozen.studyId().equals(row.get("study_id"))
                || !definition.slug().equals(row.get("slug")) || !definition.version().equals(row.get("version_code"))
                || !definition.title().equals(row.get("title")) || !definition.description().equals(row.get("description"))
                || !definition.questions().equals(frozen.items().stream().map(DraftSurveyService.Item::question).toList())) throw corrupt();
        var snapshots = jdbc.queryForList("SELECT study_id, definition_json, definition_hash FROM instrument_snapshot WHERE survey_slug = ? AND instrument_version = ?", definition.slug(), definition.version());
        if (snapshots.size() != 1) throw corrupt();
        var snapshot = snapshots.getFirst();
        if (!frozen.studyId().equals(snapshot.get("study_id")) || !CanonicalJson.hash(definition).equals(snapshot.get("definition_hash"))
                || !CanonicalJson.json(definition).equals(snapshot.get("definition_json"))) throw corrupt();
        return new Version(id, frozen.surveyId(), definition.slug(), definition.version(), definition.title(),
                (String) row.get("status"), ((Number) row.get("revision")).longValue(), row.get("published_at").toString(),
                row.get("closed_at") == null ? null : row.get("closed_at").toString(), hash, frozen, id.equals(row.get("active_version_id")));
    }

    @Transactional(readOnly = true)
    public Version publicVersion(String slug, String code) {
        var ids = jdbc.queryForList("SELECT v.id FROM survey_version v JOIN survey s ON s.id = v.survey_id WHERE s.slug = ? AND v.version_code = ? AND v.status IN ('PUBLICADA', 'CERRADA')", String.class, slug, code);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Versión no encontrada.");
        return get(ids.getFirst());
    }

    @Transactional
    public void close(String id, long revision, String actor) {
        Version version = lockForCapture(id);
        if (!"PUBLICADA".equals(version.status()) || version.revision() != revision) throw conflict();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (jdbc.update("UPDATE survey_version SET status = 'CERRADA', closed_at = ?, updated_at = ?, revision = revision + 1 WHERE id = ? AND revision = ? AND status = 'PUBLICADA'",
                now, now, id, revision) != 1) throw conflict();
        jdbc.update("UPDATE survey SET active_version_id = NULL WHERE id = ? AND active_version_id = ?", version.surveyId(), id);
        event(id, revision + 1, "CERRAR", actor, CanonicalJson.json(version.content()), version.hash());
    }

    /** Orden de bloqueos compartido por envío, cierre y activación. */
    public Version lockForCapture(String id) {
        Version initial = get(id);
        jdbc.queryForMap("SELECT id FROM survey WHERE id = ? FOR UPDATE", initial.surveyId());
        jdbc.queryForMap("SELECT id FROM survey_version WHERE id = ? FOR UPDATE", id);
        return get(id);
    }

    @Transactional
    public void activate(String id, long revision, String actor) {
        Version target = lockForCapture(id);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT archived FROM survey WHERE id = ?", Boolean.class, target.surveyId()))) throw archived();
        if (!"PUBLICADA".equals(target.status()) || target.revision() != revision) throw conflict();
        if (target.active()) return;
        String previous = jdbc.queryForObject("SELECT active_version_id FROM survey WHERE id = ?", String.class, target.surveyId());
        if (previous != null) {
            Version old = get(previous);
            jdbc.update("UPDATE survey_version SET revision = revision + 1, updated_at = ? WHERE id = ?", OffsetDateTime.now(ZoneOffset.UTC), previous);
            event(previous, old.revision() + 1, "DESACTIVAR", actor, CanonicalJson.json(old.content()), old.hash());
        }
        jdbc.update("UPDATE survey SET active_version_id = ? WHERE id = ?", id, target.surveyId());
        jdbc.update("UPDATE survey_version SET revision = revision + 1, updated_at = ? WHERE id = ?", OffsetDateTime.now(ZoneOffset.UTC), id);
        event(id, target.revision() + 1, "ACTIVAR", actor, CanonicalJson.json(target.content()), target.hash());
    }

    @Transactional(readOnly = true)
    public Version activeVersion(String slug) {
        var ids = jdbc.queryForList("SELECT active_version_id FROM survey WHERE slug = ? AND archived = FALSE AND active_version_id IS NOT NULL", String.class, slug);
        if (ids.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Esta encuesta no tiene una versión activa.");
        Version version = get(ids.getFirst());
        if (!"PUBLICADA".equals(version.status())) throw conflict();
        return version;
    }

    @Transactional(readOnly = true)
    public List<SurveyDefinition> activeDefinitions() {
        return jdbc.queryForList("SELECT active_version_id FROM survey WHERE archived = FALSE AND active_version_id IS NOT NULL ORDER BY created_at, id", String.class)
                .stream().map(this::get).filter(v -> "PUBLICADA".equals(v.status())).map(v -> v.content().definition()).toList();
    }

    @Transactional
    public String successor(String id, String actor) {
        Version version = get(id);
        var survey = jdbc.queryForMap("SELECT * FROM survey WHERE id = ? FOR UPDATE", version.surveyId());
        if (Boolean.TRUE.equals(survey.get("archived"))) throw archived();
        if (survey.get("draft_version_id") != null) return (String) survey.get("draft_version_id");
        return drafts.insertSuccessor(version.content(), actor, id);
    }

    private void event(String id, long revision, String operation, String actor, String json, String hash) {
        if (actor == null || actor.isBlank() || actor.length() > 100) throw new IllegalArgumentException("El administrador no es válido.");
        jdbc.update("INSERT INTO survey_definition_event VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID().toString(), id, revision,
                operation, actor, OffsetDateTime.now(ZoneOffset.UTC), json, hash);
    }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "La versión cambió o ya no admite esta operación. Recarga para ver su estado actual."); }
    private static ResponseStatusException archived() { return new ResponseStatusException(HttpStatus.CONFLICT, "Restaura esta encuesta antes de publicar o activar versiones."); }
    private static ResponseStatusException corrupt() { return new ResponseStatusException(HttpStatus.CONFLICT, "No se pudo verificar la integridad de esta publicación."); }
}

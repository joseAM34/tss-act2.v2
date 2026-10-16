package bo.edu.umss.mercado.data;

import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.ValidationResult;
import bo.edu.umss.mercado.survey.PublishedSurvey;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Captura lógica aceptada, separada de las revisiones analíticas y siempre de acceso administrativo. */
@Service
public class CaptureStore {
    private final JdbcTemplate jdbc;
    private final InstrumentRegistry registry;

    public CaptureStore(JdbcTemplate jdbc, InstrumentRegistry registry) {
        this.jdbc = jdbc;
        this.registry = registry;
    }

    public Form form(SurveyDefinition survey, ValidationResult validation) {
        var answers = new TreeMap<String, List<String>>();
        validation.answers().forEach((key, values) -> {
            boolean multiple = survey.questions().stream().anyMatch(q -> q.id().equals(key) && q.type().equals("checkbox"));
            answers.put(key, multiple ? values.stream().sorted().toList() : List.copyOf(values));
        });
        var states = new TreeMap<String, String>();
        validation.states().forEach((key, state) -> states.put(key, state.name()));
        return new Form(survey.slug(), survey.version(), CanonicalJson.hash(survey), true, answers, states);
    }

    public void persist(String responseId, SurveyDefinition survey, ValidationResult validation, OffsetDateTime submittedAt) {
        var snapshot = registry.register(survey);
        var payload = new Payload(responseId, submittedAt.toInstant().toString(), form(survey, validation));
        jdbc.update("""
                INSERT INTO response_capture (response_id, study_id, survey_slug, instrument_version,
                definition_hash, capture_json, capture_hash, provenance)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'CAPTURA_LOGICA')
                """, responseId, snapshot.studyId(), survey.slug(), survey.version(), snapshot.hash(),
                CanonicalJson.json(payload), CanonicalJson.hash(payload));
    }

    public String verifyRetry(String responseId, SurveyDefinition survey, ValidationResult validation) {
        Input original = input(responseId);
        Form current = form(survey, validation);
        Form saved = new Form(original.slug(), original.version(),
                original.definition() == null ? "" : CanonicalJson.hash(original.definition()), true,
                original.answers(), original.states());
        if (!original.hasSnapshot() || !CanonicalJson.hash(saved).equals(CanonicalJson.hash(current))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Este formulario ya fue enviado con otro contenido. Abra una nueva encuesta para responder de nuevo.");
        }
        return responseId;
    }

    public List<Input> inputs() {
        return jdbc.query("SELECT id FROM survey_response ORDER BY id", (rs, n) -> rs.getString("id"))
                .stream().map(this::input).toList();
    }

    public Input input(String id) {
        var rows = jdbc.queryForList("SELECT * FROM survey_response WHERE id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Respuesta no encontrada");
        Map<String, Object> row = rows.getFirst();
        String slug = (String) row.get("survey_slug");
        String version = (String) row.get("instrument_version");
        String submittedAt = jdbc.queryForObject("SELECT submitted_at FROM survey_response WHERE id = ?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant().toString(), id);
        var captures = jdbc.queryForList("SELECT * FROM response_capture WHERE response_id = ?", id);
        if (!captures.isEmpty()) {
            var capture = captures.getFirst();
            Payload payload = CanonicalJson.read((String) capture.get("capture_json"), Payload.class);
            String hash = (String) capture.get("capture_hash");
            var snapshots = jdbc.queryForList("""
                    SELECT definition_json, definition_hash FROM instrument_snapshot
                    WHERE survey_slug = ? AND instrument_version = ?
                    """, slug, version);
            if (snapshots.isEmpty()) throw corrupt();
            SurveyDefinition definition = CanonicalJson.read((String) snapshots.getFirst().get("definition_json"), SurveyDefinition.class);
            Form form = payload.form();
            String definitionHash = CanonicalJson.hash(definition);
            if (!hash.equals(CanonicalJson.hash(payload)) || !definitionHash.equals(capture.get("definition_hash"))
                    || !definitionHash.equals(snapshots.getFirst().get("definition_hash"))
                    || !definitionHash.equals(form.definitionHash()) || !id.equals(payload.responseId())
                    || !slug.equals(form.surveySlug()) || !version.equals(form.instrumentVersion())
                    || !slug.equals(definition.slug()) || !version.equals(definition.version())
                    || !submittedAt.equals(payload.submittedAt()) || !form.consent()) throw corrupt();
            if (row.get("publication_version_id") != null) {
                var published = jdbc.queryForList("SELECT definition_json, definition_hash FROM survey_version WHERE id = ?", row.get("publication_version_id"));
                if (published.size() != 1) throw corrupt();
                PublishedSurvey frozen = CanonicalJson.read((String) published.getFirst().get("definition_json"), PublishedSurvey.class);
                if (!row.get("publication_hash").equals(published.getFirst().get("definition_hash"))
                        || !row.get("publication_hash").equals(CanonicalJson.hash(frozen))
                        || !row.get("dynamic_survey_id").equals(frozen.surveyId()) || !definition.equals(frozen.definition())
                        || !capture.get("study_id").equals(frozen.studyId())) throw corrupt();
            }
            var imported=bo.edu.umss.mercado.importing.CsvImportEvidence.verify(jdbc,id,(String)row.get("publication_version_id"),hash);
            return new Input(id, slug, version, submittedAt, (String) capture.get("study_id"),
                    "NO_DECLARADO", hash, imported.isEmpty()?"CAPTURA_LOGICA":"XLSX".equals(imported.get("source_format"))?"IMPORTACION_XLSX":"IMPORTACION_CSV", definition, form.answers(), form.states(), true);
        }
        var answers = new TreeMap<String, List<String>>();
        jdbc.query("""
                SELECT question_id, answer_value FROM survey_answer WHERE response_id = ? ORDER BY question_id, item_index
                """, (RowCallbackHandler) rs -> answers.computeIfAbsent(rs.getString("question_id"), ignored -> new ArrayList<>())
                .add(rs.getString("answer_value")), id);
        var states = new TreeMap<String, String>();
        jdbc.query("SELECT question_id, answer_state FROM survey_question_state WHERE response_id = ? ORDER BY question_id",
                (RowCallbackHandler) rs -> states.put(rs.getString("question_id"), rs.getString("answer_state")), id);
        String hash = CanonicalJson.hash(Map.of("response_id", id, "slug", slug, "version", version,
                "submitted_at", submittedAt, "answers", answers, "states", states));
        return new Input(id, slug, version, submittedAt, "SIN_ESTUDIO", "NO_DECLARADO", hash,
                "RECONSTRUIDA_HISTORICA", null, answers, states, false);
    }

    public boolean approved(Input input) {
        if (!input.hasSnapshot()) return false;
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM instrument_snapshot i JOIN survey_study s ON s.id = i.study_id
                WHERE i.survey_slug = ? AND i.instrument_version = ? AND i.study_id = ?
                AND i.status = 'APROBADA' AND s.status = 'APROBADA'
                """, Integer.class, input.slug(), input.version(), input.studyId());
        return count != null && count == 1;
    }

    public String instrumentStatus(Input input) {
        if (!input.hasSnapshot()) return "SIN_DEFINICION_HISTORICA";
        return jdbc.queryForObject("SELECT status FROM instrument_snapshot WHERE survey_slug = ? AND instrument_version = ?",
                String.class, input.slug(), input.version());
    }

    private IllegalStateException corrupt() {
        return new IllegalStateException("La captura o definición no coincide con su huella. Revise su integridad antes de procesar.");
    }

    public record Form(String surveySlug, String instrumentVersion, String definitionHash, boolean consent,
                       Map<String, List<String>> answers, Map<String, String> states) {
        public Form {
            var copied = new LinkedHashMap<String, List<String>>();
            answers.forEach((key, values) -> copied.put(key, List.copyOf(values)));
            answers = java.util.Collections.unmodifiableMap(copied);
            states = Map.copyOf(states);
        }
    }
    public record Payload(String responseId, String submittedAt, Form form) {}
    public record Input(String responseId, String slug, String version, String submittedAt, String studyId,
                        String origin, String captureHash, String provenance, SurveyDefinition definition,
                        Map<String, List<String>> answers, Map<String, String> states, boolean hasSnapshot) {
        public Input {
            var copied = new LinkedHashMap<String, List<String>>();
            answers.forEach((key, values) -> copied.put(key, List.copyOf(values)));
            answers = java.util.Collections.unmodifiableMap(copied);
            states = Map.copyOf(states);
        }
    }
}

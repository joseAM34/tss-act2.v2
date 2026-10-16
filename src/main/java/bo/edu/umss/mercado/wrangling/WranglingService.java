package bo.edu.umss.mercado.wrangling;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.data.CaptureStore.Input;
import bo.edu.umss.mercado.survey.Question;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static bo.edu.umss.mercado.wrangling.WranglingEngine.map;

@Service
public class WranglingService {
    private static final Set<String> ORIGINS = Set.of("REAL", "SINTETICO", "NO_DECLARADO");
    private static final Set<String> MODES = Set.of("BORRADOR", "OFICIAL");
    private final JdbcTemplate jdbc;
    private final CaptureStore captures;
    private final WranglingEngine engine = new WranglingEngine();

    public WranglingService(JdbcTemplate jdbc, CaptureStore captures) {
        this.jdbc = jdbc;
        this.captures = captures;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public String create(String origin, String mode, Instant cutoffUtc) {
        return create(origin, mode, cutoffUtc, "ADMIN");
    }

    /** Solo se publica un conjunto después de completar todas las salidas y su inventario. */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public String create(String origin, String mode, Instant cutoffUtc, String actor) {
        require(ORIGINS, origin, "Origen no válido.");
        require(MODES, mode, "Modo no válido.");
        actor = text(actor, 100, "Se requiere identificar al administrador.");
        if (mode.equals("OFICIAL") && !origin.equals("REAL")) {
            throw new IllegalArgumentException("Un conjunto oficial solo puede usar datos clasificados como reales.");
        }
        Instant started = Instant.now().truncatedTo(ChronoUnit.MICROS);
        Instant cutoff = cutoffUtc == null ? started : cutoffUtc;
        List<Input> inputs = captures.inputs().stream().filter(i -> instant(i.submittedAt()).compareTo(cutoff) <= 0)
                .sorted(Comparator.comparing(Input::responseId)).toList();
        var prepared = new ArrayList<WranglingEngine.Prepared>();
        var members = new ArrayList<Map<String, Object>>();
        var instrumentManifest = new TreeMap<String, Map<String, Object>>();
        var studyManifest = new TreeMap<String, Map<String, Object>>();
        var reviewIssues = new ArrayList<Map<String, Object>>();
        for (Input input : inputs) {
            RevisionSet revisions = revisions(input.responseId(), started);
            String actualOrigin = revisions.origin() == null ? input.origin() : revisions.origin().value();
            if (actualOrigin == null || !ORIGINS.contains(actualOrigin)) actualOrigin = "NO_DECLARADO";
            boolean supported = Set.of("titulados", "empleadores").contains(input.slug());
            boolean included = supported && actualOrigin.equals(origin);
            String exclusion = !supported ? "ANALISIS_DINAMICO_PENDIENTE" : included ? "" : "ORIGEN_DISTINTO";
            if (included && mode.equals("OFICIAL") && !captures.approved(input)) {
                throw new IllegalArgumentException("No puede publicarse como oficial: el estudio y los instrumentos deben estar aprobados.");
            }
            var revisionMetadata = revisionMetadata(revisions);
            members.add(map("response_id", input.responseId(), "survey_slug", input.slug(), "instrument_version", input.version(),
                    "submitted_at", input.submittedAt(),
                    "capture_hash", input.captureHash(), "capture_provenance", input.provenance(), "origin", actualOrigin,
                    "included", included, "exclusion_reason", exclusion, "revisions", revisionMetadata));
            if (!included) continue;
            var answers = corrected(input, revisions);
            var changed = new HashSet<String>(revisions.corrections().keySet());
            var coded = new ArrayList<Map<String, Object>>();
            var textReviews = new ArrayList<Map<String, Object>>();
            for (Revision revision : revisions.coding().values()) {
                Question q = question(input, revision.questionId());
                if (!WranglingEngine.privacy(input.slug(), q).equals("TEXTO_LIBRE") || !q.applicable(answers)
                        || answers.getOrDefault(q.id(), List.of()).isEmpty()) continue;
                boolean sourceValid = CanonicalJson.hash(answers.get(q.id())).equals(revision.sourceValueHash());
                textReviews.add(map("response_id", input.responseId(), "survey_slug", input.slug(), "instrument_version", input.version(),
                        "question_id", q.id(), "codebook_version", revision.codebookVersion(), "revision_id", revision.id(), "valid", sourceValid));
                if (!sourceValid) {
                    reviewIssues.add(WranglingEngine.issue(input, q.id(), "CODIFICACION_TEXTO_CAMBIO", "PENDIENTE_REGLA",
                            "El texto cambió después de su revisión; se requiere una nueva codificación."));
                    continue;
                }
                for (String theme : revision.values()) coded.add(map("response_id", input.responseId(), "survey_slug", input.slug(),
                        "question_id", revision.questionId(), "theme", theme, "codebook_version", revision.codebookVersion(),
                        "revision_id", revision.id()));
            }
            prepared.add(new WranglingEngine.Prepared(input, actualOrigin, answers, input.states(), changed, coded, textReviews));
            String key = input.slug() + "/" + input.version();
            instrumentManifest.putIfAbsent(key, map("survey_slug", input.slug(), "instrument_version", input.version(),
                    "study_id", input.studyId(), "status", captures.instrumentStatus(input), "snapshot_available", input.hasSnapshot(),
                    "definition_hash", input.definition() == null ? null : CanonicalJson.hash(input.definition())));
            if (!studyManifest.containsKey(input.studyId())) {
                var studies = jdbc.queryForList("SELECT id, name, reference_year, status FROM survey_study WHERE id = ?", input.studyId());
                Map<String, Object> study = studies.isEmpty() ? null : studies.getFirst();
                studyManifest.put(input.studyId(), study == null
                        ? map("id", input.studyId(), "reference_year", null, "status", "NO_REGISTRADO")
                        : map("id", study.get("id"), "name", study.get("name"), "reference_year", study.get("reference_year"),
                                "status", study.get("status")));
            }
        }
        if (mode.equals("OFICIAL") && prepared.isEmpty()) {
            throw new IllegalArgumentException("No hay entradas reales aprobadas para producir un conjunto oficial.");
        }
        WranglingEngine.Product product = engine.prepare(prepared);
        product.issues().addAll(reviewIssues);
        var logical = map("rules_version", WranglingEngine.RULE_VERSION, "origin", origin, "mode", mode,
                "instruments", new ArrayList<>(instrumentManifest.values()), "studies", new ArrayList<>(studyManifest.values()), "members", members,
                "dictionary", product.dictionary(), "graduates", product.graduates(), "employers", product.employers(),
                "selections", product.selections(), "coding", product.coding(), "issues", product.issues(), "quality", product.quality(),
                "coverage", product.coverage(), "text_reviews", product.textReviews());
        String hash = CanonicalJson.hash(logical);
        String id = UUID.randomUUID().toString();
        var manifest = map("run_id", id, "created_at", started.toString(), "cutoff_utc", cutoff.toString(),
                "rules_version", WranglingEngine.RULE_VERSION, "origin", origin, "mode", mode,
                "input_count", inputs.size(), "included_count", prepared.size(), "excluded_count", inputs.size() - prepared.size(),
                "graduate_count", product.graduates().size(), "employer_count", product.employers().size(),
                "logical_hash", hash, "canonical_format", "CANONICAL_JSON_SHA256_V1",
                "instruments", new ArrayList<>(instrumentManifest.values()),
                "studies", new ArrayList<>(studyManifest.values()),
                "eligibility", "No se infiere elegibilidad institucional ni independencia entre participaciones.",
                "reference_policy", "Solo reglas temporales registradas en los snapshots; no se deriva antigüedad sin aprobación.",
                "privacy", "Producto administrativo sin contactos, identificación ni textos originales. No implica anonimización.",
                "hash_scope", "Miembros exactos, revisiones, reglas y productos; excluye ID, hora técnica y corte sin cambios de miembros.");
        var bundle = map("manifest", manifest, "dictionary", product.dictionary(), "graduates", product.graduates(),
                "employers", product.employers(), "selections", product.selections(), "coding", product.coding(),
                "issues", product.issues(), "quality", product.quality(), "members", members,
                "coverage", product.coverage(), "text_reviews", product.textReviews());
        jdbc.update("""
                INSERT INTO wrangling_run (id, requested_origin, publication_mode, cutoff_at, created_at, actor,
                  input_count, included_count, logical_hash, bundle_hash, bundle_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, origin, mode, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC), OffsetDateTime.ofInstant(started, ZoneOffset.UTC),
                actor, inputs.size(), prepared.size(), hash, CanonicalJson.hash(bundle), CanonicalJson.json(bundle));
        for (Map<String, Object> member : members) {
            jdbc.update("""
                    INSERT INTO wrangling_member (run_id, response_id, capture_hash, included, exclusion_reason, revisions_json)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, id, member.get("response_id"), member.get("capture_hash"), member.get("included"),
                    member.get("exclusion_reason"), CanonicalJson.json(member.get("revisions")));
        }
        return id;
    }

    public List<RunSummary> recent() {
        return jdbc.query("SELECT * FROM wrangling_run ORDER BY created_at DESC, id DESC LIMIT 100", (rs, n) -> summary(rs));
    }

    @SuppressWarnings("unchecked")
    public RunDetail detail(String id) {
        List<RunSummary> summaries = jdbc.query("SELECT * FROM wrangling_run WHERE id = ?", (rs, n) -> summary(rs), id);
        if (summaries.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto no encontrado.");
        Map<String, Object> data = CanonicalJson.read(bundle(id), Map.class);
        return new RunDetail(summaries.getFirst(), (Map<String, Object>) data.get("manifest"),
                (List<Map<String, Object>>) data.get("dictionary"), (List<Map<String, Object>>) data.get("graduates"),
                (List<Map<String, Object>>) data.get("employers"), (List<Map<String, Object>>) data.get("selections"),
                (List<Map<String, Object>>) data.get("coding"), (List<Map<String, Object>>) data.get("issues"),
                (List<Map<String, Object>>) data.get("quality"), (List<Map<String, Object>>) data.get("members"));
    }

    @SuppressWarnings("unchecked")
    public String bundle(String id) {
        var values = jdbc.queryForList("SELECT bundle_json, bundle_hash, logical_hash FROM wrangling_run WHERE id = ?", id);
        if (values.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto no encontrado.");
        Map<String, Object> persisted = values.getFirst();
        String json = (String) persisted.get("bundle_json");
        Map<String, Object> data = CanonicalJson.read(json, Map.class);
        if (!CanonicalJson.hash(data).equals(persisted.get("bundle_hash")) || !(data.get("manifest") instanceof Map<?, ?>)) {
            throw new IllegalStateException("El conjunto no coincide con sus huellas; no puede publicarse esta salida.");
        }
        Map<String, Object> manifest = (Map<String, Object>) data.get("manifest");
        var logical = map("rules_version", manifest.get("rules_version"), "origin", manifest.get("origin"), "mode", manifest.get("mode"),
                "instruments", manifest.get("instruments"), "studies", manifest.get("studies"), "members", data.get("members"),
                "dictionary", data.get("dictionary"), "graduates", data.get("graduates"), "employers", data.get("employers"),
                "selections", data.get("selections"), "coding", data.get("coding"), "issues", data.get("issues"), "quality", data.get("quality"));
        // Conservar la comprobación exacta de los conjuntos anteriores sin reconstruir metadata ausente.
        if (data.containsKey("coverage")) logical.put("coverage", data.get("coverage"));
        if (data.containsKey("text_reviews")) logical.put("text_reviews", data.get("text_reviews"));
        if (!CanonicalJson.hash(data).equals(persisted.get("bundle_hash"))
                || !CanonicalJson.hash(logical).equals(persisted.get("logical_hash"))
                || !persisted.get("logical_hash").equals(manifest.get("logical_hash")) || !id.equals(manifest.get("run_id"))) {
            throw new IllegalStateException("El conjunto no coincide con sus huellas; no puede publicarse esta salida.");
        }
        return json;
    }

    @Transactional
    public void correct(String responseId, String questionId, List<String> values, String reason, String actor) {
        Input input = captures.input(responseId);
        requireSupported(input);
        Question q = question(input, questionId);
        reason = text(reason, 1000, "Se requiere el motivo de la corrección.");
        actor = text(actor, 100, "Se requiere identificar al administrador.");
        List<String> safe = values == null ? List.of() : new ArrayList<>(values);
        if (!safe.isEmpty()) {
            String error = WranglingEngine.validate(q, safe);
            if (error != null) throw new IllegalArgumentException(error);
        }
        safe = List.copyOf(safe);
        var effective = corrected(input, revisions(responseId, Instant.now()));
        effective.put(questionId, safe);
        if ((!q.applicable(effective) && !safe.isEmpty()) || (q.applicable(effective) && q.required() && safe.isEmpty())) {
            throw new IllegalArgumentException("La corrección no respeta la aplicabilidad u obligatoriedad de esta versión.");
        }
        jdbc.update("""
                INSERT INTO wrangling_correction (id, response_id, question_id, values_json, reason, actor, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), responseId, questionId, CanonicalJson.json(safe), reason, actor, now());
    }

    @Transactional
    public void classify(String responseId, String origin, String reason, String actor) {
        captures.input(responseId);
        require(ORIGINS, origin, "Origen no válido.");
        reason = text(reason, 1000, "Se requiere el motivo de la clasificación.");
        actor = text(actor, 100, "Se requiere identificar al administrador.");
        jdbc.update("""
                INSERT INTO wrangling_origin_classification (id, response_id, origin, reason, actor, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), responseId, origin, reason, actor, now());
    }

    @Transactional
    public void codeText(String responseId, String questionId, List<String> themes, String codebookVersion, String reason, String actor) {
        Input input = captures.input(responseId);
        requireSupported(input);
        Question q = question(input, questionId);
        if (!WranglingEngine.privacy(input.slug(), q).equals("TEXTO_LIBRE")) throw new IllegalArgumentException("Solo se codifican preguntas de texto abierto.");
        var effective = corrected(input, revisions(responseId, Instant.now()));
        if (!q.applicable(effective) || effective.getOrDefault(questionId, List.of()).isEmpty()) {
            throw new IllegalArgumentException("La pregunta no tiene un texto aplicable para revisar.");
        }
        codebookVersion = text(codebookVersion, 100, "Se requiere la versión del libro de códigos.");
        reason = text(reason, 1000, "Se requiere el motivo y la revisión de la codificación.");
        actor = text(actor, 100, "Se requiere identificar al administrador.");
        List<String> safe = themes == null ? List.of() : themes.stream().map(t -> text(t, 100, "Categoría temática vacía.")).distinct().sorted().toList();
        if (safe.size() > 30 || safe.stream().anyMatch(t -> !t.matches("[\\p{L}\\p{N} _.-]{1,100}"))) {
            throw new IllegalArgumentException("Use hasta 30 categorías breves, sin datos de contacto ni símbolos de direcciones.");
        }
        jdbc.update("""
                INSERT INTO wrangling_text_coding (id, response_id, question_id, themes_json, source_value_hash, codebook_version, reason, actor, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID().toString(), responseId, questionId, CanonicalJson.json(safe),
                CanonicalJson.hash(effective.get(questionId)), codebookVersion, reason, actor, now());
    }

    public String origin(String responseId) {
        Input input = captures.input(responseId);
        Revision revision = revisions(responseId, Instant.now()).origin();
        return revision == null ? (input.origin() == null ? "NO_DECLARADO" : input.origin()) : revision.value();
    }

    private void requireSupported(Input input) {
        if (!Set.of("titulados", "empleadores").contains(input.slug()))
            throw new IllegalArgumentException("La preparación y las revisiones analíticas de esta encuesta se implementarán en el siguiente incremento.");
    }

    /** Historial privado para revisión administrativa, excluido del conjunto analítico. */
    public List<Map<String, Object>> reviewHistory(String responseId) {
        captures.input(responseId);
        var events = new ArrayList<Map<String, Object>>();
        events.addAll(jdbc.query("SELECT * FROM wrangling_correction WHERE response_id = ?", (rs, n) -> map(
                "event", "CORRECCION", "id", rs.getString("id"), "question_id", rs.getString("question_id"),
                "values", CanonicalJson.read(rs.getString("values_json"), List.class), "reason", rs.getString("reason"),
                "actor", rs.getString("actor"), "created_at", rs.getObject("created_at", OffsetDateTime.class).toInstant().toString()), responseId));
        events.addAll(jdbc.query("SELECT * FROM wrangling_origin_classification WHERE response_id = ?", (rs, n) -> map(
                "event", "ORIGEN", "id", rs.getString("id"), "origin", rs.getString("origin"), "reason", rs.getString("reason"),
                "actor", rs.getString("actor"), "created_at", rs.getObject("created_at", OffsetDateTime.class).toInstant().toString()), responseId));
        events.addAll(jdbc.query("SELECT * FROM wrangling_text_coding WHERE response_id = ?", (rs, n) -> map(
                "event", "CODIFICACION", "id", rs.getString("id"), "question_id", rs.getString("question_id"),
                "themes", CanonicalJson.read(rs.getString("themes_json"), List.class), "codebook_version", rs.getString("codebook_version"),
                "reason", rs.getString("reason"), "actor", rs.getString("actor"),
                "created_at", rs.getObject("created_at", OffsetDateTime.class).toInstant().toString()), responseId));
        events.sort(Comparator.comparing((Map<String, Object> event) -> event.get("created_at").toString()).thenComparing(e -> e.get("id").toString()));
        return events;
    }

    private record Revision(String id, String questionId, List<String> values, String value, String codebookVersion, String hash,
                            String sourceValueHash) {}
    private record RevisionSet(Map<String, Revision> corrections, Revision origin, Map<String, Revision> coding) {}

    private RevisionSet revisions(String responseId, Instant at) {
        OffsetDateTime timestamp = OffsetDateTime.ofInstant(at, ZoneOffset.UTC);
        Map<String, Revision> corrections = new TreeMap<>();
        jdbc.query("SELECT * FROM wrangling_correction WHERE response_id = ? AND created_at <= ? ORDER BY revision_order", rs -> {
            List<String> values = strings(rs.getString("values_json"));
            String qid = rs.getString("question_id");
            corrections.put(qid, new Revision(rs.getString("id"), qid, values, null, null,
                    CanonicalJson.hash(map("question_id", qid, "values", values, "reason", rs.getString("reason"), "actor", rs.getString("actor"))), null));
        }, responseId, timestamp);
        List<Revision> origins = jdbc.query("SELECT * FROM wrangling_origin_classification WHERE response_id = ? AND created_at <= ? ORDER BY revision_order",
                (rs, n) -> new Revision(rs.getString("id"), null, List.of(), rs.getString("origin"), null,
                        CanonicalJson.hash(map("origin", rs.getString("origin"), "reason", rs.getString("reason"), "actor", rs.getString("actor"))), null), responseId, timestamp);
        Map<String, Revision> coding = new TreeMap<>();
        jdbc.query("SELECT * FROM wrangling_text_coding WHERE response_id = ? AND created_at <= ? ORDER BY revision_order", rs -> {
            List<String> themes = strings(rs.getString("themes_json"));
            String qid = rs.getString("question_id");
            coding.put(qid, new Revision(rs.getString("id"), qid, themes, null, rs.getString("codebook_version"),
                    CanonicalJson.hash(map("question_id", qid, "themes", themes, "codebook_version", rs.getString("codebook_version"),
                            "source_value_hash", rs.getString("source_value_hash"),
                            "reason", rs.getString("reason"), "actor", rs.getString("actor"))), rs.getString("source_value_hash")));
        }, responseId, timestamp);
        return new RevisionSet(corrections, origins.isEmpty() ? null : origins.getLast(), coding);
    }

    private Map<String, List<String>> corrected(Input input, RevisionSet revisions) {
        Map<String, List<String>> values = new LinkedHashMap<>(input.answers());
        revisions.corrections().forEach((qid, revision) -> values.put(qid, revision.values()));
        return values;
    }

    private Map<String, Object> revisionMetadata(RevisionSet revisions) {
        return map("corrections", revisions.corrections().values().stream().map(r -> map("id", r.id(), "question_id", r.questionId(), "hash", r.hash())).toList(),
                "origin", revisions.origin() == null ? null : map("id", revisions.origin().id(), "hash", revisions.origin().hash()),
                "coding", revisions.coding().values().stream().map(r -> map("id", r.id(), "question_id", r.questionId(), "hash", r.hash(), "codebook_version", r.codebookVersion())).toList());
    }

    private Question question(Input input, String id) {
        if (input.definition() == null || !input.hasSnapshot()) throw new IllegalArgumentException("Esta versión histórica carece de un snapshot verificable para revisar el campo.");
        return input.definition().questions().stream().filter(q -> q.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La pregunta no pertenece a esta versión del instrumento."));
    }

    private RunSummary summary(ResultSet rs) throws SQLException {
        return new RunSummary(rs.getString("id"), rs.getString("requested_origin"), rs.getString("publication_mode"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant().toString(), rs.getInt("input_count"), rs.getInt("included_count"), rs.getString("logical_hash"));
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(String json) {
        return (List<String>) CanonicalJson.read(json, List.class);
    }

    private static Instant instant(String value) {
        return OffsetDateTime.parse(value).toInstant();
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String text(String value, int max, String error) {
        if (value == null || value.isBlank() || value.length() > max || value.indexOf('\0') >= 0) throw new IllegalArgumentException(error);
        return value.strip();
    }

    private static void require(Set<String> allowed, String value, String message) {
        if (value == null || !allowed.contains(value)) throw new IllegalArgumentException(message);
    }
}

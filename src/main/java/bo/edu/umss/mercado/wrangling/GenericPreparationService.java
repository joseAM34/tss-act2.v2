package bo.edu.umss.mercado.wrangling;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static bo.edu.umss.mercado.wrangling.WranglingEngine.map;

/** Preparación general separada del protocolo UMSS. No infiere privacidad o escala por el control HTML. */
@Service
public class GenericPreparationService {
    public static final String RULES = "PREPARACION_GENERAL_V1";
    public static final String TOTAL_RULES = "RESULTADOS_TOTALES_V1";
    public static boolean supportedRules(Object rules) { return RULES.equals(rules) || TOTAL_RULES.equals(rules); }
    private static final ZoneId ZONE = ZoneId.of("America/La_Paz");
    private final JdbcTemplate jdbc;
    private final SurveyPublicationService publications;
    private final CaptureStore captures;
    public GenericPreparationService(JdbcTemplate jdbc, SurveyPublicationService publications, CaptureStore captures) {
        this.jdbc = jdbc; this.publications = publications; this.captures = captures;
    }
    public record Field(String questionId, String treatment, String unit) {}
    public record Config(String versionId, String publicationHash, List<Field> fields) {}
    public record Configuration(String id, int revision, String hash, Config content) {}
    public record Summary(String id, String title, String version, String createdAt) {}

    @Transactional
    public String selectCompatible(String versionId,int expectedRevision,String actor) {
        var version=publications.lockForCapture(versionId);
        var current=configuration(versionId);
        if(current.revision()!=expectedRevision) throw new ResponseStatusException(HttpStatus.CONFLICT,"La configuración cambió. Abre de nuevo la pantalla.");
        var fields=new ArrayList<Field>();
        var catalog=new bo.edu.umss.mercado.survey.SurveyCatalog();
        var units=Map.of("edad","años completos","anio_egreso","año calendario","meses_vida_profesional","meses completos","meses_desempleado","meses completos");
        for(int i=0;i<version.content().definition().questions().size();i++) {
            var q=version.content().definition().questions().get(i); var previous=current.content().fields().get(i);
            if(Set.of("radio","select","checkbox").contains(q.type())) fields.add(new Field(q.id(),"NOMINAL",""));
            else if(q.type().equals("number")) {
                String unit=previous.treatment().equals("MAGNITUD_ENTERA")?previous.unit():"";
                if(unit.isBlank() && catalog.all().stream().flatMap(s->s.questions().stream()).anyMatch(q::equals)) unit=units.getOrDefault(q.id(),"");
                fields.add(unit.isBlank()?new Field(q.id(),"EXCLUIR",""):new Field(q.id(),"MAGNITUD_ENTERA",unit));
            } else fields.add(new Field(q.id(),"EXCLUIR",""));
        }
        return configure(versionId,expectedRevision,fields,actor);
    }

    public Configuration configuration(String versionId) {
        var v = publications.get(versionId);
        var rows = jdbc.queryForList("SELECT * FROM survey_preparation_config WHERE version_id = ? ORDER BY revision DESC LIMIT 1", versionId);
        if (rows.isEmpty()) return new Configuration("", 0, "", new Config(versionId, v.hash(),
                v.content().definition().questions().stream().map(q -> new Field(q.id(), "EXCLUIR", "")).toList()));
        return configurationRow(rows.getFirst(), v);
    }
    private Configuration configurationRow(Map<String, Object> row, SurveyPublicationService.Version version) {
        Config content = CanonicalJson.read((String) row.get("config_json"), Config.class);
        if (!CanonicalJson.hash(content).equals(row.get("config_hash")) || !content.versionId().equals(version.id())
                || !content.publicationHash().equals(version.hash())) throw corrupt();
        validate(version, content.fields());
        return new Configuration((String) row.get("id"), ((Number) row.get("revision")).intValue(), (String) row.get("config_hash"), content);
    }
    @Transactional
    public String configure(String versionId, int expectedRevision, List<Field> fields, String actor) {
        var version = publications.lockForCapture(versionId);
        var previous = configuration(versionId);
        if (previous.revision() != expectedRevision) throw new ResponseStatusException(HttpStatus.CONFLICT, "La configuración cambió. Abre de nuevo la pantalla.");
        validate(version, fields); actor = actor(actor);
        var content = new Config(versionId, version.hash(), List.copyOf(fields));
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO survey_preparation_config (id, version_id, revision, actor, created_at, config_json, config_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, versionId, Math.addExact(expectedRevision, 1), actor, OffsetDateTime.now(ZoneOffset.UTC), CanonicalJson.json(content), CanonicalJson.hash(content));
        return id;
    }
    private void validate(SurveyPublicationService.Version version, List<Field> fields) {
        var questions = version.content().definition().questions();
        if (fields == null || fields.size() != questions.size()) throw invalid("Configura todas las preguntas de esta versión.");
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i); Field f = fields.get(i);
            if (f == null || !q.id().equals(f.questionId()) || f.unit() == null || f.unit().length() > 80 || f.unit().chars().anyMatch(Character::isISOControl)) throw invalid("La configuración no corresponde al orden de esta versión.");
            if (f.treatment() == null || !Set.of("EXCLUIR", "NOMINAL", "MAGNITUD_ENTERA").contains(f.treatment())) throw invalid("Tratamiento no admitido.");
            if (f.treatment().equals("NOMINAL") && !Set.of("radio", "select", "checkbox").contains(q.type())) throw invalid("Solo las opciones controladas admiten tratamiento nominal.");
            if (f.treatment().equals("MAGNITUD_ENTERA") && (!q.type().equals("number") || f.unit().isBlank())) throw invalid("Una magnitud requiere pregunta numérica y unidad explícita.");
            if (!f.treatment().equals("MAGNITUD_ENTERA") && !f.unit().isEmpty()) throw invalid("La unidad solo corresponde a magnitudes enteras.");
        }
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public String prepare(String versionId, String configId, String origin, String mode, LocalDate from, LocalDate until, String actor) {
        return persist(build(versionId,configId,origin,mode,from,until,actor));
    }

    /** Una captura consistente por consulta; no escribe conjuntos ni configura variables. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Map<String,Object> current(String versionId,String origin,LocalDate from,LocalDate until,String actor) {
        var config=configuration(versionId);
        if(config.revision()==0 || config.content().fields().stream().allMatch(f->f.treatment().equals("EXCLUIR")))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Elige las variables de esta versión una vez para habilitar sus resultados actualizados.");
        return build(versionId,config.id(),origin,"BORRADOR",from,until,actor);
    }

    private Map<String,Object> build(String versionId,String configId,String origin,String mode,LocalDate from,LocalDate until,String actor) {
        return buildAt(versionId,configId,origin,mode,from,until,actor,OffsetDateTime.now(ZoneOffset.UTC));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ,propagation=org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public Map<String,Object> atCutoff(String versionId,String origin,LocalDate from,LocalDate until,String cutoff,String actor) {
        var date=OffsetDateTime.parse(cutoff);
        if(date.isAfter(OffsetDateTime.now(ZoneOffset.UTC)))throw invalid("El corte no puede estar en el futuro.");
        var config=configuration(versionId);
        var bundle=buildAt(versionId,config.id(),origin,"BORRADOR",from,until,actor,date);
        bundle.put("created_at",OffsetDateTime.now(ZoneOffset.UTC).toInstant().toString());return bundle;
    }
    private Map<String,Object> buildAt(String versionId,String configId,String origin,String mode,LocalDate from,LocalDate until,String actor,OffsetDateTime now) {
        var version = publications.get(versionId);
        var configRows = jdbc.queryForList("SELECT * FROM survey_preparation_config WHERE id = ? AND version_id = ?", configId, versionId);
        if (configRows.isEmpty()) throw invalid("Guarda primero la configuración de esta versión.");
        var config = configurationRow(configRows.getFirst(), version);
        if (!Set.of("REAL", "SINTETICO", "NO_DECLARADO").contains(origin) || !Set.of("BORRADOR", "OFICIAL").contains(mode)) throw invalid("Origen o modo no válido.");
        if (from != null && until != null && from.isAfter(until)) throw invalid("La fecha inicial no puede superar la final.");
        if (mode.equals("OFICIAL") && !origin.equals("REAL")) throw invalid("El modo oficial exige origen real.");
        if (config.content().fields().stream().allMatch(f -> f.treatment().equals("EXCLUIR"))) throw invalid("Selecciona al menos una variable sin identificadores para preparar.");
        actor = actor(actor);
        var dictionary = new ArrayList<Map<String, Object>>();
        var excluded = new ArrayList<String>();
        var selected = new LinkedHashMap<String, Field>();
        var questions = version.content().definition().questions();
        for (int i = 0; i < questions.size(); i++) {
            var q = questions.get(i); var f = config.content().fields().get(i);
            if (f.treatment().equals("EXCLUIR")) { excluded.add(q.id()); continue; }
            selected.put(q.id(), f);
            dictionary.add(map("question_id", q.id(), "label", q.label(), "type", q.type(), "treatment", f.treatment(),
                    "unit", f.unit(), "privacy", "SIN_IDENTIFICADORES_DECLARADO_POR_ADMIN", "options", q.options(),
                    "depends_on", q.dependsOn(), "depends_value", q.dependsValue(), "required", q.required(), "min", q.min(), "max", q.max()));
        }
        var members = new ArrayList<Map<String, Object>>();
        var matrix = new ArrayList<Map<String, Object>>();
        var selections = new ArrayList<Map<String, Object>>();
        var quality = new LinkedHashMap<String, int[]>();
        selected.keySet().forEach(q -> quality.put(q, new int[4])); // total, con valor, sin respuesta, no aplica
        List<String> ids = jdbc.queryForList("SELECT id FROM survey_response WHERE publication_version_id = ? AND submitted_at <= ? ORDER BY id", String.class, versionId, now);
        for (String responseId : ids) {
            var input = captures.input(responseId);
            if (!input.hasSnapshot() || !input.definition().equals(version.content().definition()) || !input.studyId().equals(version.content().studyId())) throw corrupt();
            LocalDate date = Instant.parse(input.submittedAt()).atZone(ZONE).toLocalDate();
            if (from != null && date.isBefore(from) || until != null && date.isAfter(until)) continue;
            var origins = jdbc.queryForList("SELECT * FROM wrangling_origin_classification WHERE response_id = ? AND created_at <= ? ORDER BY revision_order DESC LIMIT 1", responseId, now);
            Map<String, Object> revision = null;
            String actualOrigin = "NO_DECLARADO";
            if (!origins.isEmpty()) {
                var o = origins.getFirst(); actualOrigin = (String) o.get("origin");
                revision = map("id", o.get("id"), "hash", CanonicalJson.hash(map("origin", actualOrigin, "reason", o.get("reason"), "actor", o.get("actor"))));
            }
            boolean included = actualOrigin.equals(origin);
            if (included && mode.equals("OFICIAL") && !captures.approved(input)) throw invalid("El estudio y esta versión deben estar aprobados para modo oficial.");
            members.add(map("response_id", responseId, "submitted_at", input.submittedAt(), "capture_hash", input.captureHash(), "capture_provenance", input.provenance(),
                    "import_reference", bo.edu.umss.mercado.importing.CsvImportEvidence.verify(jdbc,responseId,versionId,input.captureHash()),
                    "origin", actualOrigin, "origin_revision", revision, "included", included, "exclusion_reason", included ? "" : "ORIGEN_DISTINTO"));
            if (!included) continue;
            var values = new LinkedHashMap<String, Object>(); var states = new LinkedHashMap<String, String>();
            for (var q : questions) {
                var field = selected.get(q.id()); if (field == null) continue;
                String state = input.states().get(q.id()); var answers = input.answers().getOrDefault(q.id(), List.of());
                if (!Set.of("CON_VALOR", "SIN_RESPUESTA", "NO_APLICA").contains(state == null ? "" : state)
                        || state.equals("CON_VALOR") == answers.isEmpty() || state.equals("NO_APLICA") == q.applicable(input.answers())) throw corrupt();
                var count = quality.get(q.id()); count[0]++; count[state.equals("CON_VALOR") ? 1 : state.equals("SIN_RESPUESTA") ? 2 : 3]++;
                states.put(q.id(), state);
                Object value = null;
                if (state.equals("CON_VALOR")) {
                    if (field.treatment().equals("MAGNITUD_ENTERA")) {
                        if (answers.size() != 1) throw corrupt();
                        int number;
                        try { number = Integer.parseInt(answers.getFirst()); } catch (NumberFormatException ex) { throw corrupt(); }
                        if (q.min() != null && number < q.min() || q.max() != null && number > q.max()) throw corrupt();
                        value = number;
                    } else {
                        var allowed = q.options().stream().map(Question.Option::value).toList();
                        if (!allowed.containsAll(answers) || new HashSet<>(answers).size() != answers.size()) throw corrupt();
                        if (q.type().equals("checkbox")) {
                            value = answers.stream().sorted().toList();
                            for (String answer : answers.stream().sorted().toList()) selections.add(map("response_id", responseId, "question_id", q.id(), "option_code", answer));
                        } else { if (answers.size() != 1) throw corrupt(); value = answers.getFirst(); }
                    }
                }
                values.put(q.id(), value);
            }
            matrix.add(map("response_id", responseId, "submitted_at", input.submittedAt(), "origin", actualOrigin, "values", values, "states", states));
        }
        if (mode.equals("OFICIAL") && matrix.isEmpty()) throw invalid("No hay envíos reales aprobados en la selección.");
        var coverage = new ArrayList<Map<String, Object>>();
        quality.forEach((q, n) -> coverage.add(map("question_id", q, "total_submissions", n[0], "with_value", n[1], "without_answer", n[2], "not_applicable", n[3], "applicable_base", n[0] - n[3])));
        var study = jdbc.queryForMap("SELECT id, name, reference_year, status FROM survey_study WHERE id = ?", version.content().studyId());
        String instrumentStatus = jdbc.queryForObject("SELECT status FROM instrument_snapshot WHERE survey_slug = ? AND instrument_version = ?", String.class, version.slug(), version.code());
        var logical = map("rules_version", RULES, "study_id", version.content().studyId(),
                "study", map("id", study.get("id"), "name", study.get("name"), "reference_year", study.get("reference_year"), "status", study.get("status")),
                "instrument_status", instrumentStatus, "definition_hash", CanonicalJson.hash(version.content().definition()), "survey_id", version.surveyId(),
                "version_id", versionId, "survey_slug", version.slug(), "instrument_version", version.code(), "publication_hash", version.hash(),
                "definition", version.content().definition(), "configuration", config.content(), "config_id", config.id(), "config_revision", config.revision(), "config_hash", config.hash(),
                "origin", origin, "mode", mode, "from_date", from == null ? null : from.toString(), "until_date", until == null ? null : until.toString(), "timezone", ZONE.getId(),
                "members", members, "dictionary", dictionary, "excluded_questions", excluded, "matrix", matrix, "selections", selections, "coverage", coverage,
                "unit_of_analysis", "UN_ENVIO", "privacy", "Sin contactos, organización ni texto original. La clasificación administrativa no garantiza anonimización.");
        String id = UUID.randomUUID().toString(), hash = CanonicalJson.hash(logical);
        var bundle = map("run_id", id, "created_at", now.toInstant().toString(), "cutoff_utc", now.toInstant().toString(), "actor", actor, "logical_hash", hash, "logical", logical);
        return bundle;
    }

    /** Uso interno desde el guardado protegido; los bundles históricos nunca se reescriben. */
    @Transactional
    @SuppressWarnings("unchecked")
    public String persist(Map<String,Object> bundle) {
        var logical=(Map<String,Object>)bundle.get("logical");
        if(!supportedRules(logical.get("rules_version")) || !CanonicalJson.hash(logical).equals(bundle.get("logical_hash")))throw corrupt();
        String id=(String)bundle.get("run_id");
        jdbc.update("INSERT INTO survey_preparation_run (id, version_id, config_id, actor, created_at, bundle_json, bundle_hash, logical_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id,logical.get("version_id"),logical.get("config_id"),actor((String)bundle.get("actor")),OffsetDateTime.parse((String)bundle.get("created_at")),CanonicalJson.json(bundle),CanonicalJson.hash(bundle),bundle.get("logical_hash"));
        return id;
    }
    @SuppressWarnings("unchecked")
    public Map<String, Object> bundle(String id) {
        var rows = jdbc.queryForList("SELECT * FROM survey_preparation_run WHERE id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conjunto no encontrado.");
        var row = rows.getFirst(); Map<String, Object> bundle = CanonicalJson.read((String) row.get("bundle_json"), Map.class);
        if (!CanonicalJson.hash(bundle).equals(row.get("bundle_hash")) || !id.equals(bundle.get("run_id"))
                || !CanonicalJson.hash(bundle.get("logical")).equals(row.get("logical_hash")) || !row.get("logical_hash").equals(bundle.get("logical_hash"))) throw corrupt();
        Map<String, Object> logical = (Map<String, Object>) bundle.get("logical");
        if (!supportedRules(logical.get("rules_version")) || !row.get("version_id").equals(logical.get("version_id")) || !row.get("config_id").equals(logical.get("config_id"))) throw corrupt();
        return bundle;
    }
    public List<Summary> recent() {
        return jdbc.query("SELECT r.id, v.title, v.version_code, r.created_at FROM survey_preparation_run r JOIN survey_version v ON v.id = r.version_id ORDER BY r.created_at DESC, r.id DESC LIMIT 100",
                (rs, n) -> new Summary(rs.getString(1), rs.getString(2), rs.getString(3), rs.getObject(4, OffsetDateTime.class).toInstant().toString()));
    }
    public List<Summary> recentForVersion(String versionId) {
        return jdbc.query("SELECT r.id, v.title, v.version_code, r.created_at FROM survey_preparation_run r JOIN survey_version v ON v.id = r.version_id WHERE r.version_id = ? ORDER BY r.created_at DESC, r.id DESC LIMIT 100",
                (rs,n)->new Summary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class).toInstant().toString()),versionId);
    }
    private String actor(String actor) { if (actor == null || actor.isBlank() || actor.length() > 100) throw invalid("Administrador no válido."); return actor; }
    private IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private IllegalStateException corrupt() { return new IllegalStateException("La captura, configuración o conjunto no coincide con su definición o huella."); }
}

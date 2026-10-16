package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Descriptivos generales v1: no asigna puntajes nominales ni ejecuta inferencias. */
@Service
public class GenericAnalysisService {
    public static final String PROTOCOL = "DESCRIPTIVOS_GENERALES_V1";
    private final JdbcTemplate jdbc;
    private final GenericPreparationService preparation;
    public GenericAnalysisService(JdbcTemplate jdbc, GenericPreparationService preparation) { this.jdbc = jdbc; this.preparation = preparation; }
    @Transactional
    public String create(String preparationId, String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 100) throw new IllegalArgumentException("Administrador no válido.");
        var bundle = preparation.bundle(preparationId);
        var result = calculate(logical(bundle));
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO survey_general_analysis (id, preparation_id, actor, created_at, source_hash, result_json, result_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, preparationId, actor, OffsetDateTime.now(ZoneOffset.UTC), bundle.get("logical_hash"), CanonicalJson.json(result), CanonicalJson.hash(result));
        return id;
    }
    @SuppressWarnings("unchecked")
    private Map<String, Object> logical(Map<String, Object> bundle) { return (Map<String, Object>) bundle.get("logical"); }
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String id) {
        var rows = jdbc.queryForList("SELECT * FROM survey_general_analysis WHERE id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Análisis no encontrado.");
        var row = rows.getFirst(); var source = preparation.bundle((String) row.get("preparation_id"));
        Map<String, Object> result = CanonicalJson.read((String) row.get("result_json"), Map.class);
        if (!source.get("logical_hash").equals(row.get("source_hash")) || !CanonicalJson.hash(result).equals(row.get("result_hash"))
                || !CanonicalJson.hash(calculate(logical(source))).equals(row.get("result_hash"))) throw new IllegalStateException("El análisis no coincide con sus huellas o su conjunto fuente.");
        return map("id", id, "preparation_id", row.get("preparation_id"), "actor", row.get("actor"), "created_at", row.get("created_at").toString(),
                "source_hash", row.get("source_hash"), "result_hash", row.get("result_hash"), "result", result);
    }
    public record Summary(String id, String preparationId, String createdAt, String title, String version, String origin) {}
    public List<Summary> recent() {
        return jdbc.query("SELECT id, preparation_id, created_at, result_json FROM survey_general_analysis ORDER BY created_at DESC, id DESC LIMIT 100",
                (rs, n) -> {
                    var result = CanonicalJson.read(rs.getString(4), Map.class);
                    return new Summary(rs.getString(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class).toInstant().toString(),
                            (String)result.get("title"), (String)result.get("instrument_version"), (String)result.get("origin"));
                });
    }
    @SuppressWarnings("unchecked")
    public Map<String, Object> calculate(Map<String, Object> logical) {
        if (!GenericPreparationService.RULES.equals(logical.get("rules_version"))) throw new IllegalArgumentException("Contrato de preparación no compatible.");
        var matrix = (List<Map<String, Object>>) logical.get("matrix");
        var dictionary = (List<Map<String, Object>>) logical.get("dictionary");
        var variables = new ArrayList<Map<String, Object>>();
        for (var q : dictionary) {
            String key = (String) q.get("question_id");
            int answered = 0, missing = 0, notApplicable = 0;
            var numbers = new ArrayList<Double>(); var frequencies = new LinkedHashMap<String, Integer>();
            var options = (List<Map<String, Object>>) q.get("options");
            options.forEach(o -> frequencies.put((String) o.get("value"), 0));
            int mentions = 0;
            for (var row : matrix) {
                var states = (Map<String, String>) row.get("states"); var values = (Map<String, Object>) row.get("values");
                String state = states.get(key); Object value = values.get(key);
                if ("NO_APLICA".equals(state)) { if (value != null) throw invalid(); notApplicable++; continue; }
                if ("SIN_RESPUESTA".equals(state)) { if (value != null) throw invalid(); missing++; continue; }
                if (!"CON_VALOR".equals(state) || value == null) throw invalid();
                answered++;
                if ("MAGNITUD_ENTERA".equals(q.get("treatment"))) {
                    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue()) || number.doubleValue() != Math.rint(number.doubleValue())) throw invalid();
                    numbers.add(number.doubleValue());
                } else if ("NOMINAL".equals(q.get("treatment"))) {
                    List<?> codes = "checkbox".equals(q.get("type")) ? (List<?>) value : List.of(value);
                    if (codes.isEmpty() || new HashSet<>(codes).size() != codes.size()) throw invalid();
                    for (Object code : codes) { if (!frequencies.containsKey(code)) throw invalid(); frequencies.compute((String) code, (k, v) -> v + 1); mentions++; }
                } else throw invalid();
            }
            var distribution = new ArrayList<Map<String, Object>>();
            for (var option : options) {
                int count = frequencies.get(option.get("value"));
                distribution.add(map("code", option.get("value"), "label", option.get("label"), "count", count,
                        "respondent_base", answered, "respondent_percent", percent(count, answered),
                        "mention_base", mentions, "mention_percent", percent(count, mentions)));
            }
            variables.add(map("question_id", key, "label", q.get("label"), "type", q.get("type"), "treatment", q.get("treatment"), "unit", q.get("unit"),
                    "total", matrix.size(), "answered", answered, "missing", missing, "not_applicable", notApplicable,
                    "applicable_base", answered + missing, "response_percent", percent(answered, answered + missing),
                    "mentions", mentions, "distribution", distribution, "numeric", "MAGNITUD_ENTERA".equals(q.get("treatment")) ? StatisticalMath.numeric(numbers) : null));
        }
        return map("protocol", PROTOCOL, "preparation_rules", logical.get("rules_version"), "title", ((Map<String, Object>) logical.get("definition")).get("title"),
                "study_id", logical.get("study_id"), "survey_id", logical.get("survey_id"), "version_id", logical.get("version_id"),
                "instrument_version", logical.get("instrument_version"), "publication_hash", logical.get("publication_hash"),
                "config_id", logical.get("config_id"), "config_hash", logical.get("config_hash"), "origin", logical.get("origin"), "mode", logical.get("mode"),
                "from_date", logical.get("from_date"), "until_date", logical.get("until_date"), "timezone", logical.get("timezone"),
                "submission_count", matrix.size(), "variables", variables, "scope", "Descriptivos de envíos seleccionados; sin inferencia, índices ni comparabilidad automática.");
    }
    private Double percent(int count, int base) { return base == 0 ? null : 100d * count / base; }
    private static Map<String, Object> map(Object... pairs) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < pairs.length; i += 2) result.put((String) pairs[i], pairs[i + 1]);
        return result;
    }
    private IllegalStateException invalid() { return new IllegalStateException("Valores o estados incompatibles con el diccionario congelado."); }
}

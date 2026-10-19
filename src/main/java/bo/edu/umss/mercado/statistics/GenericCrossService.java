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
import static bo.edu.umss.mercado.statistics.StatisticalMath.map;

@Service
public class GenericCrossService {
    public static final String PROTOCOL = "CRUCE_GENERAL_V1";
    public record Spec(String rowQuestion, String columnQuestion, double alpha, boolean independenceDocumented,
                       String unit, String evidence, boolean structuralZeros) {}
    public record Summary(String id, String preparationId, String createdAt) {}
    private final JdbcTemplate jdbc;
    private final GenericPreparationService preparation;
    public GenericCrossService(JdbcTemplate jdbc, GenericPreparationService preparation) { this.jdbc = jdbc; this.preparation = preparation; }
    @SuppressWarnings("unchecked")
    public Map<String, Object> source(String id) { return (Map<String, Object>) preparation.bundle(id).get("logical"); }
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> choices(String id) {
        return ((List<Map<String, Object>>) source(id).get("dictionary")).stream().filter(this::eligible).toList();
    }
    private boolean eligible(Map<String, Object> field) { return "NOMINAL".equals(field.get("treatment")) && Set.of("radio", "select").contains(field.get("type")); }
    @Transactional
    @SuppressWarnings("unchecked")
    public String create(String preparationId, Spec spec, String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 100) throw new IllegalArgumentException("Administrador no válido.");
        var bundle = preparation.bundle(preparationId); var result = calculate((Map<String, Object>) bundle.get("logical"), spec);
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO survey_general_cross (id, preparation_id, actor, created_at, source_hash, spec_json, spec_hash, result_json, result_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, preparationId, actor, OffsetDateTime.now(ZoneOffset.UTC), bundle.get("logical_hash"), CanonicalJson.json(spec), CanonicalJson.hash(spec), CanonicalJson.json(result), CanonicalJson.hash(result));
        return id;
    }
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String id) {
        var rows = jdbc.queryForList("SELECT * FROM survey_general_cross WHERE id = ?", id);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cruce no encontrado.");
        var row = rows.getFirst(); var bundle = preparation.bundle((String) row.get("preparation_id"));
        Spec spec = CanonicalJson.read((String) row.get("spec_json"), Spec.class);
        Map<String, Object> result = CanonicalJson.read((String) row.get("result_json"), Map.class);
        if (!bundle.get("logical_hash").equals(row.get("source_hash")) || !CanonicalJson.hash(spec).equals(row.get("spec_hash"))
                || !CanonicalJson.hash(result).equals(row.get("result_hash")) || !CanonicalJson.hash(calculate((Map<String, Object>) bundle.get("logical"), spec)).equals(row.get("result_hash"))) throw new IllegalStateException("El cruce no coincide con sus huellas o su conjunto fuente.");
        return map("id", id, "preparation_id", row.get("preparation_id"), "created_at", row.get("created_at").toString(), "actor", row.get("actor"),
                "source_hash", row.get("source_hash"), "spec_hash", row.get("spec_hash"), "result_hash", row.get("result_hash"), "spec", spec, "result", result);
    }
    public List<Summary> recent() { return jdbc.query("SELECT id, preparation_id, created_at FROM survey_general_cross ORDER BY created_at DESC, id DESC LIMIT 100",
            (rs, n) -> new Summary(rs.getString(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class).toInstant().toString())); }
    public List<Summary> forPreparation(String preparationId) { return jdbc.query("SELECT id, preparation_id, created_at FROM survey_general_cross WHERE preparation_id = ? ORDER BY created_at DESC, id DESC LIMIT 100",
            (rs,n)->new Summary(rs.getString(1),rs.getString(2),rs.getObject(3,OffsetDateTime.class).toInstant().toString()),preparationId); }
    @SuppressWarnings("unchecked")
    public Map<String, Object> calculate(Map<String, Object> logical, Spec spec) {
        if (!GenericPreparationService.supportedRules(logical.get("rules_version"))) throw new IllegalArgumentException("Preparación no compatible.");
        boolean totals=GenericPreparationService.TOTAL_RULES.equals(logical.get("rules_version"));
        if (spec == null || spec.rowQuestion() == null || spec.columnQuestion() == null || spec.rowQuestion().equals(spec.columnQuestion())) throw new IllegalArgumentException("Selecciona dos preguntas distintas.");
        if(totals && spec.independenceDocumented())throw new IllegalArgumentException("El total es descriptivo. Para inferencia prepara un único estrato y revisa su independencia por separado.");
        if (!Double.isFinite(spec.alpha()) || spec.alpha() <= 0 || spec.alpha() >= 1) throw new IllegalArgumentException("Alpha debe estar entre cero y uno.");
        if (spec.unit() == null || spec.evidence() == null || spec.unit().length() > 120 || spec.evidence().length() > 2000) throw new IllegalArgumentException("Unidad o evidencia no válida.");
        if (spec.independenceDocumented() && (spec.unit().isBlank() || spec.evidence().isBlank())) throw new IllegalArgumentException("Documenta la unidad y evidencia antes de declarar independencia.");
        var dictionary = (List<Map<String, Object>>) logical.get("dictionary");
        var row = dictionary.stream().filter(q -> spec.rowQuestion().equals(q.get("question_id")) && eligible(q)).findFirst().orElseThrow(() -> new IllegalArgumentException("La pregunta de filas debe ser nominal y de selección única incluida en este conjunto."));
        var column = dictionary.stream().filter(q -> spec.columnQuestion().equals(q.get("question_id")) && eligible(q)).findFirst().orElseThrow(() -> new IllegalArgumentException("La pregunta de columnas debe ser nominal y de selección única incluida en este conjunto."));
        var rowOptions = (List<Map<String, Object>>) row.get("options"); var columnOptions = (List<Map<String, Object>>) column.get("options");
        if ((long) rowOptions.size() * columnOptions.size() > 2500) throw new IllegalArgumentException("El cruce admite hasta 2500 celdas. Revisa el número de categorías.");
        var rowCodes = rowOptions.stream().map(o -> o.get("value")).toList(); var columnCodes = columnOptions.stream().map(o -> o.get("value")).toList();
        long[][] counts = new long[rowCodes.size()][columnCodes.size()];
        var states = new TreeMap<String, Integer>(); int excluded = 0;
        var matrix = (List<Map<String, Object>>) logical.get("matrix");
        for (var member : matrix) {
            var answerStates = (Map<String, String>) member.get("states"); var values = (Map<String, Object>) member.get("values");
            String a = answerStates.get(spec.rowQuestion()), b = answerStates.get(spec.columnQuestion());
            if(totals && (a==null || b==null)){excluded++;states.merge("NO_RECOLECTADA_EN_VERSION",1,Integer::sum);continue;}
            if (!Set.of("CON_VALOR", "SIN_RESPUESTA", "NO_APLICA").contains(a == null ? "" : a) || !Set.of("CON_VALOR", "SIN_RESPUESTA", "NO_APLICA").contains(b == null ? "" : b)) throw new IllegalStateException("Estados no compatibles con el conjunto.");
            states.merge(a + "|" + b, 1, Integer::sum);
            if (!"CON_VALOR".equals(a) || !"CON_VALOR".equals(b)) { excluded++; continue; }
            int r = rowCodes.indexOf(values.get(spec.rowQuestion())), c = columnCodes.indexOf(values.get(spec.columnQuestion()));
            if (r < 0 || c < 0) throw new IllegalStateException("Categoría ajena al diccionario congelado.");
            counts[r][c]++;
        }
        var table = StatisticalMath.contingency(counts, spec.independenceDocumented(), spec.structuralZeros(), spec.alpha());
        return map("protocol", PROTOCOL, "title", ((Map<String, Object>) logical.get("definition")).get("title"),
                "study_id", logical.get("study_id"), "survey_id", logical.get("survey_id"), "version_id", logical.get("version_id"), "instrument_version", logical.get("instrument_version"),
                "publication_hash", logical.get("publication_hash"), "config_id", logical.get("config_id"), "config_hash", logical.get("config_hash"), "origin", logical.get("origin"), "mode", logical.get("mode"),
                "from_date", logical.get("from_date"), "until_date", logical.get("until_date"), "timezone", logical.get("timezone"),
                "row_question", row.get("question_id"), "row_label", row.get("label"), "column_question", column.get("question_id"), "column_label", column.get("label"),
                "row_options", rowOptions, "column_options", columnOptions, "total_submissions", matrix.size(), "excluded_pairs", excluded, "pair_states", states,
                "table", table, "multiplicity", "Un contraste por cruce; sin ajuste global entre cruces exploratorios.",
                "scope", "Descriptivo de pares con valor. Declarar independencia no demuestra elegibilidad ni aprobación institucional.");
    }
}

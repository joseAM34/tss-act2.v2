package bo.edu.umss.mercado.visualization;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.AnalysisPlan;
import bo.edu.umss.mercado.statistics.AnalysisService;
import bo.edu.umss.mercado.statistics.StatisticalEngine;
import bo.edu.umss.mercado.wrangling.WranglingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Selecciona exclusivamente miembros del conjunto congelado que originó el análisis. */
@Service
public class DashboardDataService {
    private static final ZoneId STUDY_ZONE = ZoneId.of("America/La_Paz");
    private static final String CONFIG_VERSION = "SELECCION_VISUAL_V1";
    private static final Map<String, String> GRADUATE_FILTERS = Map.of(
            "employment", "estado_laboral", "graduateSector", "sector", "training", "formacion_complementaria", "interest", "interes_posgrado");
    private static final Map<String, String> EMPLOYER_FILTERS = Map.of(
            "organizationType", "tipo_organizacion", "organizationSize", "tamano_organizacion", "organizationSector", "sector");
    private record Row(String slug, Map<String, Object> values) {}
    private final AnalysisService analyses;
    private final WranglingService wrangling;
    private final StatisticalEngine engine;

    public DashboardDataService(AnalysisService analyses, WranglingService wrangling, StatisticalEngine engine) {
        this.analyses = analyses; this.wrangling = wrangling; this.engine = engine;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> data(String id, DashboardSelection selection) {
        if (selection == null) throw new IllegalArgumentException("Se requiere una selección.");
        Map<String, Object> analysis = analyses.detail(id);
        Map<String, Object> manifest = object(analysis.get("manifest"));
        String runId = string(manifest, "source_run_id");
        Map<String, Object> source = CanonicalJson.read(wrangling.bundle(runId), Map.class);
        Map<String, Object> sourceManifest = object(source.get("manifest"));
        if (!manifest.get("source_logical_hash").equals(sourceManifest.get("logical_hash"))
                || !manifest.get("source_bundle_hash").equals(CanonicalJson.hash(source)))
            throw new IllegalStateException("El conjunto no corresponde a las huellas de origen del análisis.");
        List<Row> allRows = rows(source);
        validateMetadata(selection, allRows, sourceManifest);
        List<Row> scope = allRows.stream().filter(row -> metadataMatches(row, selection)).toList();
        Map<String, Object> applied = CanonicalJson.read(CanonicalJson.json(selection), Map.class);
        // Jackson reconoce isEmpty como propiedad; no es un filtro aplicado.
        applied.remove("empty");
        Map<String, List<Map<String, Object>>> filters = filters(source, allRows, scope, sourceManifest, selection);
        validateCategories(applied, filters, source, scope);
        Map<String, Map<String, Object>> members = members(source);
        boolean dateAvailable = scope.stream().allMatch(row -> hasDate(members.get(string(row.values(), "response_id"))));
        if ((!selection.from().isEmpty() || !selection.to().isEmpty()) && !dateAvailable)
            throw new IllegalArgumentException("Este conjunto no conserva las fechas de envío. Prepare un conjunto nuevo para filtrar por período.");
        Instant from = selection.from().isEmpty() ? null : LocalDate.parse(selection.from()).atStartOfDay(STUDY_ZONE).toInstant();
        Instant until = selection.to().isEmpty() ? null : LocalDate.parse(selection.to()).plusDays(1).atStartOfDay(STUDY_ZONE).toInstant();
        List<Row> selected = scope.stream().filter(row -> valueMatches(row, selection, applied, source))
                .filter(row -> dateMatches(row, members, from, until)).toList();
        AnalysisPlan plan = CanonicalJson.read(CanonicalJson.json(analysis.get("plan")), AnalysisPlan.class);
        Map<String, Object> result;
        if (selection.isEmpty()) result = object(analysis.get("result"));
        else {
            requireSupportedProtocol(manifest);
            result = engine.analyze(subset(source, selected), plan);
        }
        var comparison = new ArrayList<Map<String, Object>>();
        if (!selection.compareBy().isEmpty()) {
            requireSupportedProtocol(manifest);
            for (String category : List.of(selection.groupA(), selection.groupB())) {
                List<Row> groupRows = selected.stream().filter(row -> matches(row, selection.comparisonVariable(), category, source)).toList();
                Map<String, Object> groupResult = engine.analyze(subset(source, groupRows), plan);
                comparison.add(map("code", category, "label", optionLabel(filters.get("groupA"), category),
                        "variable", selection.comparisonVariable(), "filters", map("compareBy", selection.compareBy(), "category", category),
                        "n_selected", groupRows.size(), "result", groupResult, "result_hash", CanonicalJson.hash(groupResult)));
            }
        }
        String selectionHash = CanonicalJson.hash(map("version", CONFIG_VERSION, "source_logical_hash", manifest.get("source_logical_hash"),
                "analysis_logical_hash", manifest.get("logical_hash"), "selection", applied));
        Map<String, Object> selectedSource = subset(source, selected);
        Map<String, Object> comparisonCatalog = map(
                "tipo_organizacion", categoryOptions(source, scope, "empleadores", "tipo_organizacion"),
                "tamano_organizacion", categoryOptions(source, scope, "empleadores", "tamano_organizacion"),
                "estado_laboral", categoryOptions(source, scope, "titulados", "estado_laboral"),
                "sector_laboral", categoryOptions(source, scope, "titulados", "sector"));
        var context = map("analysis_id", id, "source_run_id", runId, "protocol_version", manifest.get("protocol_version"),
                "analysis_logical_hash", manifest.get("logical_hash"), "source_logical_hash", manifest.get("source_logical_hash"),
                "source_bundle_hash", manifest.get("source_bundle_hash"), "origin", manifest.get("source_origin"),
                "mode", manifest.get("source_mode"), "cutoff_utc", manifest.get("source_cutoff_utc"),
                "created_at", manifest.get("created_at"), "status", "BORRADOR", "unit", "ENVIO",
                "timezone", STUDY_ZONE.getId(), "applied_selection", applied, "selection_version", CONFIG_VERSION,
                "selection_hash", selectionHash, "query_id", selectionHash, "result_hash", CanonicalJson.hash(result),
                "n_selected", selected.size(), "n_base", allRows.size(), "date_available", dateAvailable,
                "comparison_catalog", comparisonCatalog,
                "quality_summary", selectedSource.get("quality"), "issue_summary", issueSummary(list(selectedSource, "issues")),
                "issue_count", list(selectedSource, "issues").size(),
                "comparison_exploratory", true, "global_familywise_control", false,
                "version_policy", "Los resultados de estudios y versiones permanecen separados; no se aprueba su comparabilidad.");
        return map("context", context, "result", result, "comparison", comparison, "filters", filters);
    }

    private static void requireSupportedProtocol(Map<String, Object> manifest) {
        if (!AnalysisPlan.PROTOCOL_VERSION.equals(manifest.get("protocol_version")))
            throw new IllegalArgumentException("Este protocolo no admite recálculo de selecciones con el motor actual.");
    }

    private static List<Row> rows(Map<String, Object> source) {
        var rows = new ArrayList<Row>();
        for (String key : List.of("graduates", "employers")) {
            String slug = key.equals("graduates") ? "titulados" : "empleadores";
            list(source, key).forEach(row -> rows.add(new Row(slug, row)));
        }
        return rows;
    }

    private static boolean metadataMatches(Row row, DashboardSelection selection) {
        return (selection.study().isEmpty() || selection.study().equals(row.values().get("study_id")))
                && (selection.survey().isEmpty() || selection.survey().equals(row.slug()))
                && (selection.version().isEmpty() || selection.version().equals(row.values().get("instrument_version")));
    }

    private static void validateMetadata(DashboardSelection selection, List<Row> rows, Map<String, Object> manifest) {
        Set<String> studies = new LinkedHashSet<>(), versions = new LinkedHashSet<>();
        rows.forEach(row -> { studies.add(string(row.values(), "study_id")); versions.add(string(row.values(), "instrument_version")); });
        list(manifest, "studies").forEach(study -> studies.add(string(study, "id")));
        list(manifest, "instruments").forEach(instrument -> versions.add(string(instrument, "instrument_version")));
        if (!selection.study().isEmpty() && !studies.contains(selection.study()))
            throw new IllegalArgumentException("El estudio seleccionado no pertenece a este conjunto.");
        if (!selection.version().isEmpty() && !versions.contains(selection.version()))
            throw new IllegalArgumentException("La versión seleccionada no pertenece a este conjunto.");
    }

    private static Map<String, List<Map<String, Object>>> filters(Map<String, Object> source, List<Row> all,
                                                               List<Row> scope, Map<String, Object> manifest,
                                                               DashboardSelection selection) {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        var studies = new TreeMap<String, String>();
        all.forEach(row -> studies.put(string(row.values(), "study_id"), string(row.values(), "study_id")));
        list(manifest, "studies").forEach(study -> studies.put(string(study, "id"),
                string(study, "name").isEmpty() ? string(study, "id") : string(study, "name")));
        result.put("study", options(studies));
        result.put("survey", List.of(map("code", "titulados", "label", "Titulados"), map("code", "empleadores", "label", "Empleadores")));
        var versions = new TreeMap<String, String>();
        all.stream().filter(row -> (selection.study().isEmpty() || selection.study().equals(row.values().get("study_id")))
                && (selection.survey().isEmpty() || selection.survey().equals(row.slug())))
                .forEach(row -> versions.put(string(row.values(), "instrument_version"), string(row.values(), "instrument_version")));
        result.put("version", options(versions));
        var years = new TreeMap<Integer, String>();
        scope.stream().filter(row -> row.slug().equals("titulados") && "CON_VALOR".equals(row.values().get("anio_egreso__estado")))
                .forEach(row -> { if (row.values().get("anio_egreso") instanceof Number n) years.put(n.intValue(), Integer.toString(n.intValue())); });
        result.put("year", years.entrySet().stream().map(year -> map("code", year.getValue(), "label", year.getValue())).toList());
        GRADUATE_FILTERS.forEach((field, variable) -> result.put(field, categoryOptions(source, scope, "titulados", variable)));
        EMPLOYER_FILTERS.forEach((field, variable) -> result.put(field, categoryOptions(source, scope, "empleadores", variable)));
        result.put("compareBy", List.of(map("code", "estado_laboral", "label", "Situación laboral de titulados"),
                map("code", "sector_laboral", "label", "Sector laboral de titulados"),
                map("code", "tipo_organizacion", "label", "Tipo de organización"),
                map("code", "tamano_organizacion", "label", "Tamaño de organización")));
        List<Map<String, Object>> groups = selection.compareBy().isEmpty() ? List.of() : categoryOptions(source, scope,
                Set.of("estado_laboral", "sector_laboral").contains(selection.compareBy()) ? "titulados" : "empleadores", selection.comparisonVariable());
        result.put("groupA", groups); result.put("groupB", groups);
        return result;
    }

    private static List<Map<String, Object>> categoryOptions(Map<String, Object> source, List<Row> rows, String slug, String variable) {
        Set<String> versions = new LinkedHashSet<>();
        rows.stream().filter(row -> row.slug().equals(slug)).forEach(row -> versions.add(string(row.values(), "instrument_version")));
        var choices = new LinkedHashMap<String, String>();
        for (Map<String, Object> definition : list(source, "dictionary"))
            if (slug.equals(definition.get("survey_slug")) && variable.equals(definition.get("variable"))
                    && versions.contains(string(definition, "instrument_version")) && analytic(definition))
                for (Map<String, Object> option : list(definition, "catalog")) choices.putIfAbsent(string(option, "code"), string(option, "label"));
        return choices.entrySet().stream().map(option -> map("code", option.getKey(), "label", option.getValue())).toList();
    }

    private static void validateCategories(Map<String, Object> selection, Map<String, List<Map<String, Object>>> filters,
                                           Map<String, Object> source, List<Row> scope) {
        var allowed = new LinkedHashSet<String>(GRADUATE_FILTERS.keySet()); allowed.addAll(EMPLOYER_FILTERS.keySet());
        allowed.addAll(List.of("groupA", "groupB"));
        for (String field : allowed) {
            String selected = string(selection, field);
            if (!selected.isEmpty() && filters.getOrDefault(field, List.of()).stream().noneMatch(option -> selected.equals(option.get("code"))))
                throw new IllegalArgumentException("La categoría seleccionada no pertenece al catálogo congelado de " + field + ".");
        }
        String year = string(selection, "year");
        if (!year.isEmpty()) {
            int value = Integer.parseInt(year);
            boolean allowedYear = scope.stream().filter(row -> row.slug().equals("titulados"))
                    .map(row -> definition(source, row, "anio_egreso")).filter(java.util.Objects::nonNull)
                    .anyMatch(definition -> "ENTERO".equals(definition.get("type")) && analytic(definition)
                            && (!(definition.get("min") instanceof Number min) || value >= min.intValue())
                            && (!(definition.get("max") instanceof Number max) || value <= max.intValue()));
            if (!allowedYear) throw new IllegalArgumentException("El año de egreso no está dentro de la definición congelada disponible.");
        }
    }

    private static boolean valueMatches(Row row, DashboardSelection selection, Map<String, Object> values, Map<String, Object> source) {
        Map<String, String> fields = row.slug().equals("titulados") ? GRADUATE_FILTERS : EMPLOYER_FILTERS;
        for (var field : fields.entrySet()) {
            String code = string(values, field.getKey());
            if (!code.isEmpty() && !matches(row, field.getValue(), code, source)) return false;
        }
        if (row.slug().equals("titulados") && !selection.year().isEmpty()) {
            Map<String, Object> definition = definition(source, row, "anio_egreso");
            if (definition == null || !analytic(definition) || !"CON_VALOR".equals(row.values().get("anio_egreso__estado"))
                    || !(row.values().get("anio_egreso") instanceof Number n) || n.intValue() != Integer.parseInt(selection.year())) return false;
        }
        return true;
    }

    private static boolean matches(Row row, String variable, String code, Map<String, Object> source) {
        Map<String, Object> definition = definition(source, row, variable);
        return definition != null && analytic(definition) && "CON_VALOR".equals(row.values().get(variable + "__estado"))
                && code.equals(row.values().get(variable)) && list(definition, "catalog").stream().anyMatch(option -> code.equals(option.get("code")));
    }

    private static Map<String, Object> definition(Map<String, Object> source, Row row, String variable) {
        return list(source, "dictionary").stream().filter(definition -> row.slug().equals(definition.get("survey_slug"))
                && row.values().get("instrument_version").equals(definition.get("instrument_version"))
                && variable.equals(definition.get("variable"))).findFirst().orElse(null);
    }

    private static boolean analytic(Map<String, Object> definition) {
        return Boolean.TRUE.equals(definition.get("included_in_matrix")) && "ANALITICA".equals(definition.get("privacy"));
    }

    private static Map<String, Map<String, Object>> members(Map<String, Object> source) {
        var members = new LinkedHashMap<String, Map<String, Object>>();
        list(source, "members").forEach(member -> members.put(string(member, "response_id"), member));
        return members;
    }

    private static boolean hasDate(Map<String, Object> member) {
        if (member == null || string(member, "submitted_at").isEmpty()) return false;
        try { Instant.parse(string(member, "submitted_at")); return true; }
        catch (DateTimeParseException ex) { return false; }
    }

    private static boolean dateMatches(Row row, Map<String, Map<String, Object>> members, Instant from, Instant until) {
        if (from == null && until == null) return true;
        Instant value = Instant.parse(string(members.get(string(row.values(), "response_id")), "submitted_at"));
        return (from == null || !value.isBefore(from)) && (until == null || value.isBefore(until));
    }

    private static Map<String, Object> subset(Map<String, Object> source, List<Row> selected) {
        Set<String> ids = new LinkedHashSet<>(); selected.forEach(row -> ids.add(string(row.values(), "response_id")));
        var result = new LinkedHashMap<String, Object>(source);
        result.put("graduates", selected.stream().filter(row -> row.slug().equals("titulados")).map(Row::values).toList());
        result.put("employers", selected.stream().filter(row -> row.slug().equals("empleadores")).map(Row::values).toList());
        for (String product : List.of("coverage", "coding", "text_reviews", "issues", "selections", "members"))
            if (source.containsKey(product)) result.put(product, list(source, product).stream().filter(row -> ids.contains(string(row, "response_id"))).toList());
        var counts = new TreeMap<String, Integer>();
        for (Row row : selected) for (String key : row.values().keySet()) if (key.endsWith("__estado"))
            counts.merge(row.slug() + "\t" + key.substring(0, key.length() - 8) + "\t" + row.values().get(key), 1, Integer::sum);
        var quality = new ArrayList<Map<String, Object>>();
        counts.forEach((key, count) -> { String[] parts = key.split("\t", -1); quality.add(map("survey_slug", parts[0], "question_id", parts[1], "state", parts[2], "count", count)); });
        result.put("quality", quality);
        return result;
    }

    private static List<Map<String, Object>> issueSummary(List<Map<String, Object>> issues) {
        var counts = new TreeMap<String, Integer>();
        issues.forEach(issue -> counts.merge(string(issue, "rule_code") + "\t" + string(issue, "severity"), 1, Integer::sum));
        return counts.entrySet().stream().map(entry -> { String[] parts = entry.getKey().split("\t", -1);
            return map("rule_code", parts[0], "severity", parts[1], "count", entry.getValue()); }).toList();
    }

    private static List<Map<String, Object>> options(Map<String, String> values) {
        return values.entrySet().stream().map(entry -> map("code", entry.getKey(), "label", entry.getValue())).toList();
    }

    private static String optionLabel(List<Map<String, Object>> options, String code) {
        return options.stream().filter(option -> code.equals(option.get("code"))).map(option -> string(option, "label")).findFirst().orElse(code);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) { return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> value, String key) {
        return value.get(key) instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }
    private static String string(Map<String, Object> value, String key) { return value == null || value.get(key) == null ? "" : value.get(key).toString(); }
    private static Map<String, Object> map(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) result.put(entries[i].toString(), entries[i + 1]);
        return result;
    }
}

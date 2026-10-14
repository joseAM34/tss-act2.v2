package bo.edu.umss.mercado.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

import static bo.edu.umss.mercado.statistics.StatisticalMath.map;

/** Consume únicamente productos congelados. Cada tipo, estudio y versión conserva su propio estrato. */
@Component
public class StatisticalEngine {
    private record Stratum(String id, String slug, String version, String study, List<Map<String, Object>> rows) {}
    private static final Map<String, List<String>> CROSSES = Map.of(
            "B01", List.of("financiamiento_futuro", "nivel_interes"),
            "B02", List.of("financiamiento_cursado", "nivel_cursado"),
            "B03", List.of("estado_laboral", "interes_posgrado"),
            "B04", List.of("tipo_organizacion", "probabilidad_contratacion"),
            "B05", List.of("tamano_organizacion", "probabilidad_contratacion"));

    public Map<String, Object> analyze(Map<String, Object> sourceBundle, AnalysisPlan plan) {
        if (sourceBundle == null || plan == null) throw new IllegalArgumentException("Se requiere un conjunto y un plan.");
        List<Map<String, Object>> dictionary = list(sourceBundle, "dictionary");
        List<Map<String, Object>> coverage = list(sourceBundle, "coverage");
        List<Map<String, Object>> reviews = list(sourceBundle, "text_reviews");
        List<Map<String, Object>> coding = list(sourceBundle, "coding");
        var strata = new TreeMap<String, Stratum>();
        var responseIds = new HashSet<String>();
        for (String product : List.of("graduates", "employers")) {
            for (Map<String, Object> row : list(sourceBundle, product)) {
                String responseId = string(row, "response_id");
                if (responseId.isBlank() || !responseIds.add(responseId)) throw new IllegalArgumentException("Cada envío debe aparecer una sola vez.");
                String slug = product.equals("graduates") ? "titulados" : "empleadores";
                String version = string(row, "instrument_version"), study = string(row, "study_id");
                String key = slug + "|" + version + "|" + study;
                strata.computeIfAbsent(key, ignored -> new Stratum(key, slug, version, study, new ArrayList<>())).rows().add(row);
            }
        }
        var outputs = new ArrayList<Map<String, Object>>();
        var contrasts = new ArrayList<Map<String, Object>>();
        for (Stratum stratum : strata.values()) {
            stratum.rows().sort(Comparator.comparing(row -> string(row, "response_id")));
            var definitions = new TreeMap<String, Map<String, Object>>();
            dictionary.stream().filter(d -> stratum.slug().equals(d.get("survey_slug")) && stratum.version().equals(d.get("instrument_version")))
                    .forEach(d -> definitions.put(string(d, "variable"), d));
            var frequencies = new ArrayList<Map<String, Object>>();
            var numbers = new ArrayList<Map<String, Object>>();
            var ordinals = new ArrayList<Map<String, Object>>();
            var multiples = new ArrayList<Map<String, Object>>();
            var indicators = new ArrayList<Map<String, Object>>();
            var coverageResults = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> definition : definitions.values()) {
                if (!Boolean.TRUE.equals(definition.get("included_in_matrix")) || !"ANALITICA".equals(definition.get("privacy"))) continue;
                String variable = string(definition, "variable");
                Map<String, Object> counts = coverage(stratum, variable, definition, coverage, sourceBundle.containsKey("coverage"));
                coverageResults.add(map("variable", variable, "counts", counts));
                List<Object> usable = validValues(stratum, definition);
                String kind = string(definition, "type");
                if (kind.equals("ENTERO")) {
                    var numeric = new LinkedHashMap<>(StatisticalMath.numeric(usable.stream().map(v -> ((Number) v).doubleValue()).toList()));
                    numeric.put("variable", variable); numeric.put("source_id", definition.get("source_id"));
                    numeric.put("label", definition.get("label"));
                    numeric.put("unit", definition.get("unit")); numeric.put("coverage", counts);
                    numbers.add(numeric);
                    if (variable.equals("anio_egreso")) frequencies.add(numericFrequencies(definition, usable, counts));
                } else if (kind.equals("MULTIPLE")) {
                    multiples.add(multiple(definition, usable, counts));
                } else {
                    frequencies.add(frequency(definition, usable, counts));
                    if (kind.startsWith("ORDINAL") || Set.of("nivel_interes", "tiempo_primer_empleo").contains(variable))
                        ordinals.add(ordinal(definition, usable, counts));
                }
                indicators.addAll(indicators(variable, usable));
            }
            Set<String> described = coverageResults.stream().map(c -> string(c, "variable")).collect(java.util.stream.Collectors.toSet());
            var absentDefinitions = new java.util.TreeSet<String>();
            for (Map<String, Object> row : stratum.rows()) for (String key : row.keySet())
                if (key.endsWith("__estado")) absentDefinitions.add(key.substring(0, key.length() - 8));
            for (String variable : absentDefinitions) if (!described.contains(variable)) {
                Map<String, Object> counts = coverage(stratum, variable, null, coverage, sourceBundle.containsKey("coverage"));
                coverageResults.add(map("variable", variable, "status", "PENDIENTE_DEFINICION", "counts", counts));
            }
            outputs.add(map("stratum_id", stratum.id(), "survey_slug", stratum.slug(), "instrument_version", stratum.version(),
                    "study_id", stratum.study(), "unit", "ENVIO", "n_base", stratum.rows().size(),
                    "frequencies", frequencies, "numeric", numbers, "ordinal", ordinals, "multiple", multiples,
                    "texts", texts(stratum, definitions, coding, reviews, coverage, sourceBundle.containsKey("text_reviews"), sourceBundle.containsKey("coverage")),
                    "indicators", indicators, "coverage", coverageResults));
            for (String id : plan.contrasts()) {
                if (relevant(id, stratum.slug())) contrasts.add(contrast(id, stratum, definitions, plan));
            }
        }
        var families = families(contrasts, plan.alpha());
        Map<String, Object> manifest = object(sourceBundle.get("manifest"));
        return map("protocol_version", AnalysisPlan.PROTOCOL_VERSION, "status", "BORRADOR", "source_logical_hash", manifest.get("logical_hash"),
                "strata", outputs, "contrasts", contrasts, "families", families,
                "independence_documented", plan.independenceDocumented(), "unit", "ENVIO",
                "limitations", List.of("La clasificación real y un UUID distinto no acreditan elegibilidad, representatividad ni independencia.",
                        "Cada estudio y versión se describe por separado; no se aprueba su comparabilidad automáticamente.",
                        "Las medias ordinales son medias de puntajes codificados del protocolo de borrador, no magnitudes físicas.",
                        "No se deriva antigüedad, no se calcula una brecha de competencias ni se deduce causalidad desde una asociación.",
                        "La metadata de cobertura ausente permanece desconocida; no se reconstruye desde el catálogo actual.",
                        "B01 a B05 son familias exploratorias individuales: no controlan conjuntamente el error de todas las comparaciones.",
                        "La probabilidad de contratación conserva modalidades nominales; no se les asigna una probabilidad numérica."));
    }

    private Map<String, Object> coverage(Stratum group, String variable, Map<String, Object> definition,
                                          List<Map<String, Object>> inventory, boolean hasCoverage) {
        Map<String, Map<String, Object>> byResponse = new LinkedHashMap<>();
        inventory.stream().filter(c -> variable.equals(c.get("question_id")) && group.slug().equals(c.get("survey_slug"))
                && group.version().equals(c.get("instrument_version"))).forEach(c -> byResponse.put(string(c, "response_id"), c));
        Map<String, Long> states = new TreeMap<>();
        int present = 0, applicable = 0, conValue = 0, unknownPresent = 0, unknownApplicable = 0, unknownCon = 0;
        for (Map<String, Object> row : group.rows()) {
            states.merge(string(row, variable + "__estado").isBlank() ? "SIN_METADATA" : string(row, variable + "__estado"), 1L, Long::sum);
            Map<String, Object> meta = byResponse.get(string(row, "response_id"));
            Object p = meta == null ? null : meta.get("present");
            Object a = meta == null ? null : meta.get("applicable");
            Object cv = meta == null ? null : meta.get("con_value_before_quality");
            if (p == null) unknownPresent++; else if (Boolean.TRUE.equals(p)) present++;
            if (a == null) unknownApplicable++; else if (Boolean.TRUE.equals(a)) applicable++;
            if (cv == null) unknownCon++; else if (Boolean.TRUE.equals(cv)) conValue++;
        }
        List<Object> usable = definition == null ? List.of() : validValues(group, definition);
        long noSabe = usable.stream().filter("NO_SABE"::equals).count();
        long noRate = usable.stream().filter("NO_PUEDE_VALORAR"::equals).count();
        return map("n_base", group.rows().size(), "n_present", hasCoverage && unknownPresent == 0 ? present : null,
                "n_applicable", hasCoverage && unknownApplicable == 0 ? applicable : null,
                "n_con_value", hasCoverage && unknownCon == 0 ? conValue : null, "n_validos", usable.size(),
                "n_no_sabe", noSabe, "n_no_puede_valorar", noRate, "n_sustantivos", usable.size() - noSabe - noRate,
                "state_counts", states, "unknown_present_count", unknownPresent, "unknown_applicable_count", unknownApplicable,
                "unknown_con_value_count", unknownCon, "coverage_status", hasCoverage && unknownPresent + unknownApplicable + unknownCon == 0 ? "REGISTRADA" : "METADATA_DESCONOCIDA");
    }

    private List<Object> validValues(Stratum group, Map<String, Object> definition) {
        String variable = string(definition, "variable"), type = string(definition, "type");
        List<String> catalog = codes(definition);
        var values = new ArrayList<Object>();
        for (Map<String, Object> row : group.rows()) {
            if (!"CON_VALOR".equals(row.get(variable + "__estado"))) continue;
            Object value = row.get(variable);
            if (type.equals("ENTERO")) {
                if (value instanceof Number n && Double.isFinite(n.doubleValue())) values.add(value);
            } else if (type.equals("MULTIPLE")) {
                if (value instanceof List<?> options && !options.isEmpty() && options.stream().allMatch(catalog::contains)) {
                    values.add(new LinkedHashSet<>(options).stream().map(Object::toString).sorted().toList());
                }
            } else if (value instanceof String code && catalog.contains(code)) values.add(code);
        }
        return values;
    }

    private Map<String, Object> frequency(Map<String, Object> definition, List<Object> values, Map<String, Object> coverage) {
        var rows = new ArrayList<Map<String, Object>>();
        long highest = values.isEmpty() ? 0 : -1;
        var modes = new ArrayList<String>();
        for (Map<String, Object> option : list(definition, "catalog")) {
            String code = string(option, "code");
            long count = values.stream().filter(code::equals).count();
            rows.add(map("code", code, "label", option.get("label"), "count", count, "denominator", values.size(),
                    "relative_frequency", relative(count, values.size()), "percentage", percent(count, values.size())));
            if (!values.isEmpty()) {
                if (count > highest) { highest = count; modes.clear(); }
                if (count == highest) modes.add(code);
            }
        }
        return map("variable", definition.get("variable"), "source_id", definition.get("source_id"), "label", definition.get("label"),
                "status", values.isEmpty() ? "SIN_DATOS" : "CALCULADO", "counts", rows, "modas", modes, "coverage", coverage);
    }

    private Map<String, Object> numericFrequencies(Map<String, Object> definition, List<Object> values, Map<String, Object> coverage) {
        Map<Integer, Long> counts = new TreeMap<>();
        values.forEach(value -> counts.merge(((Number) value).intValue(), 1L, Long::sum));
        return map("variable", definition.get("variable"), "source_id", definition.get("source_id"), "label", definition.get("label"),
                "status", values.isEmpty() ? "SIN_DATOS" : "CALCULADO", "coverage", coverage,
                "counts", counts.entrySet().stream().map(e -> map("code", e.getKey(), "count", e.getValue(),
                        "denominator", values.size(), "relative_frequency", relative(e.getValue(), values.size()),
                        "percentage", percent(e.getValue(), values.size()))).toList());
    }

    private Map<String, Object> multiple(Map<String, Object> definition, List<Object> values, Map<String, Object> coverage) {
        Map<String, Long> counts = new LinkedHashMap<>();
        codes(definition).forEach(code -> counts.put(code, 0L));
        for (Object value : values) for (Object code : (List<?>) value) counts.merge(code.toString(), 1L, Long::sum);
        long mentions = counts.values().stream().mapToLong(Long::longValue).sum();
        return map("variable", definition.get("variable"), "source_id", definition.get("source_id"), "label", definition.get("label"),
                "status", values.isEmpty() ? "SIN_DATOS" : "CALCULADO", "n_validos", values.size(), "mention_count", mentions,
                "coverage", coverage, "options", counts.entrySet().stream().map(e -> map("code", e.getKey(), "label", label(definition, e.getKey()), "count", e.getValue(),
                        "denominator", values.size(), "participant_percentage", percent(e.getValue(), values.size()),
                        "mention_denominator", mentions, "mention_percentage", percent(e.getValue(), mentions))).toList());
    }

    private Map<String, Object> ordinal(Map<String, Object> definition, List<Object> values, Map<String, Object> coverage) {
        String variable = string(definition, "variable");
        Map<String, Integer> configuredScores = scores(variable);
        Map<String, Integer> scores = new TreeMap<>(configuredScores);
        scores.keySet().retainAll(codes(definition));
        List<String> order = new ArrayList<>(codes(definition));
        order.removeAll(List.of("NO_SABE", "NO_PUEDE_VALORAR"));
        if (!configuredScores.isEmpty()) {
            order.removeIf(code -> !scores.containsKey(code));
            order.sort(Comparator.comparingInt(scores::get));
        }
        if (variable.equals("tamano_organizacion")) order = orderedSubset(definition, List.of("MICRO", "PEQUENA", "MEDIANA", "GRANDE"));
        if (variable.equals("nivel_interes")) order = orderedSubset(definition, List.of("ESPECIALIDAD", "MAESTRIA", "DOCTORADO"));
        if (variable.equals("tiempo_primer_empleo")) order = orderedSubset(definition, List.of("ANTES_TITULACION", "MENOS_1_MES",
                "ENTRE_1_4_MESES", "ENTRE_4_8_MESES", "ENTRE_8_12_MESES", "MAS_12_MESES"));
        List<String> finalOrder = order;
        List<String> sorted = values.stream().filter(v -> finalOrder.contains(v)).map(Object::toString)
                .sorted(Comparator.comparingInt(finalOrder::indexOf)).toList();
        long unordered = values.stream().filter(v -> !Set.of("NO_SABE", "NO_PUEDE_VALORAR").contains(v)
                && !finalOrder.contains(v)).count();
        var central = new ArrayList<String>();
        if (unordered == 0 && !sorted.isEmpty()) {
            central.add(sorted.get((sorted.size() - 1) / 2));
            if (sorted.size() % 2 == 0 && !central.getFirst().equals(sorted.get(sorted.size() / 2))) central.add(sorted.get(sorted.size() / 2));
        }
        List<Double> coded = values.stream().filter(scores::containsKey).map(v -> scores.get(v).doubleValue()).toList();
        return map("variable", variable, "source_id", definition.get("source_id"), "label", definition.get("label"), "coverage", coverage,
                "status", unordered > 0 ? "PENDIENTE_REGLA" : sorted.isEmpty() ? "SIN_DATOS" : "CALCULADO",
                "reason", unordered > 0 ? "CATEGORIAS_SIN_REGLA_DE_ORDEN" : sorted.isEmpty() ? "SIN_CATEGORIAS_ORDINALES_UTILIZABLES" : "",
                "n_ordered", sorted.size(), "n_unordered", unordered, "ordering_complete", unordered == 0,
                "institutional_approval", false,
                "semantic_order", order, "semantic_order_labels", order.stream().map(c -> label(definition, c)).toList(),
                "central_categories", central, "central_labels", central.stream().map(c -> label(definition, c)).toList(), "score_map", scores,
                "score_summary", configuredScores.isEmpty() ? null : StatisticalMath.numeric(coded), "n_scored", coded.size(),
                "score_label", "Media de puntajes codificados; convención de borrador sin distancias iguales demostradas.");
    }

    private Map<String, Integer> scores(String variable) {
        if (Set.of("opinion_posgrado_fcyt", "satisfaccion_formacion", "concordancia_formacion").contains(variable))
            return Map.of("TOTALMENTE_DESACUERDO", 1, "DESACUERDO", 2, "ACUERDO", 3, "TOTALMENTE_ACUERDO", 4);
        if (variable.equals("satisfaccion_emprendimiento")) return Map.of("INSATISFECHO", 1, "ALGO_SATISFECHO", 2, "SATISFECHO", 3);
        if (variable.equals("importancia_formacion_negocio")) return Map.of("NO_IMPORTANTE", 1, "POCO_IMPORTANTE", 2, "IMPORTANTE", 3, "MUY_IMPORTANTE", 4);
        if (variable.matches("criterio_(0[1-9]|10)")) return Map.of("TOTALMENTE_DESACUERDO", 1, "PARCIALMENTE_ACUERDO", 2, "TOTALMENTE_ACUERDO", 3);
        if (variable.matches("competencia_(tecnica|transversal)_0[1-6]")) return Map.of("ADECUACION_1", 1, "ADECUACION_2", 2, "ADECUACION_3", 3, "ADECUACION_4", 4, "ADECUACION_5", 5);
        return Map.of();
    }

    private List<Map<String, Object>> indicators(String variable, List<Object> values) {
        var result = new ArrayList<Map<String, Object>>();
        if (Set.of("formacion_complementaria", "interes_posgrado", "interes_red_contactos", "contratacion_reciente").contains(variable)) {
            result.add(indicator(variable, "PROPORCION_SI", values.stream().filter("SI"::equals).count(), values.size()));
        }
        if (variable.equals("estado_laboral")) {
            result.add(indicator(variable, "OCUPACION_DECLARADA", values.stream().filter(v -> Set.of("EMPLEADO", "EMPRENDIMIENTO").contains(v)).count(), values.size()));
            result.add(indicator(variable, "NO_TRABAJA_DECLARADO", values.stream().filter("DESEMPLEADO"::equals).count(), values.size()));
        }
        if (variable.matches("criterio_(0[1-9]|10)")) {
            long agreement = values.stream().filter(v -> Set.of("TOTALMENTE_ACUERDO", "PARCIALMENTE_ACUERDO").contains(v)).count();
            long substantive = values.stream().filter(v -> !v.equals("NO_SABE")).count();
            result.add(indicator(variable, "ACUERDO_SOBRE_SUSTANTIVOS", agreement, substantive));
            result.add(indicator(variable, "ACUERDO_SOBRE_VALIDOS", agreement, values.size()));
        }
        return result;
    }

    private Map<String, Object> indicator(String variable, String name, long numerator, long denominator) {
        return map("variable", variable, "indicator", name, "numerator", numerator, "denominator", denominator,
                "percentage", percent(numerator, denominator), "status", denominator == 0 ? "SIN_DATOS" : "CALCULADO");
    }

    private List<Map<String, Object>> texts(Stratum stratum, Map<String, Map<String, Object>> definitions,
                                          List<Map<String, Object>> coding, List<Map<String, Object>> reviews,
                                          List<Map<String, Object>> coverage, boolean hasReviews, boolean hasCoverage) {
        Set<String> ids = stratum.rows().stream().map(r -> string(r, "response_id")).collect(java.util.stream.Collectors.toSet());
        var books = new TreeMap<String, String[]>();
        for (Map<String, Object> item : reviews) if (ids.contains(string(item, "response_id"))) {
            books.put(string(item, "question_id") + "|" + string(item, "codebook_version"), new String[]{string(item, "question_id"), string(item, "codebook_version")});
        }
        for (Map<String, Object> item : coding) if (ids.contains(string(item, "response_id"))) {
            books.put(string(item, "question_id") + "|" + string(item, "codebook_version"), new String[]{string(item, "question_id"), string(item, "codebook_version")});
        }
        for (var field : definitions.entrySet()) if ("TEXTO_LIBRE".equals(field.getValue().get("privacy"))
                && books.values().stream().noneMatch(b -> b[0].equals(field.getKey()))) {
            books.put(field.getKey() + "|", new String[]{field.getKey(), ""});
        }
        var result = new ArrayList<Map<String, Object>>();
        for (String[] book : books.values()) {
            Map<String, Object> definition = definitions.get(book[0]);
            if (definition == null || !"TEXTO_LIBRE".equals(definition.get("privacy"))) continue;
            Set<String> validRevisions = reviews.stream().filter(r -> ids.contains(string(r, "response_id"))
                    && book[0].equals(r.get("question_id")) && book[1].equals(r.get("codebook_version")) && Boolean.TRUE.equals(r.get("valid")))
                    .map(r -> string(r, "revision_id")).collect(java.util.stream.Collectors.toSet());
            long reviewed = reviews.stream().filter(r -> validRevisions.contains(string(r, "revision_id")))
                    .map(r -> string(r, "response_id")).distinct().count();
            Set<String> textInventory = coverage.stream().filter(c -> ids.contains(string(c, "response_id")) && book[0].equals(c.get("question_id")))
                    .map(c -> string(c, "response_id")).collect(java.util.stream.Collectors.toSet());
            boolean textMetadataKnown = hasCoverage && textInventory.size() == ids.size();
            Set<String> validTexts = coverage.stream().filter(c -> ids.contains(string(c, "response_id")) && book[0].equals(c.get("question_id"))
                    && "CON_VALOR".equals(c.get("final_state"))).map(c -> string(c, "response_id")).collect(java.util.stream.Collectors.toSet());
            Set<String> reviewedAnyBook = reviews.stream().filter(r -> ids.contains(string(r, "response_id"))
                    && book[0].equals(r.get("question_id")) && Boolean.TRUE.equals(r.get("valid")))
                    .map(r -> string(r, "response_id")).collect(java.util.stream.Collectors.toSet());
            long unreviewed = validTexts.stream().filter(r -> !reviewedAnyBook.contains(r)).count();
            Map<String, Set<String>> counts = new TreeMap<>();
            for (Map<String, Object> item : coding) {
                if (!ids.contains(string(item, "response_id")) || !book[0].equals(item.get("question_id")) || !book[1].equals(item.get("codebook_version"))) continue;
                if (hasReviews && !validRevisions.contains(string(item, "revision_id"))) continue;
                counts.computeIfAbsent(string(item, "theme"), ignored -> new HashSet<>()).add(string(item, "response_id"));
            }
            result.add(map("variable", book[0], "question_id", book[0], "source_id", definition.get("source_id"), "label", definition.get("label"),
                    "codebook_version", book[1].isBlank() ? null : book[1],
                    "status", !hasReviews || !textMetadataKnown ? "METADATA_DESCONOCIDA" : reviewed > 0 ? "CALCULADO"
                            : !validTexts.isEmpty() ? "PENDIENTE_CODIFICACION" : "SIN_DATOS",
                    "reviewed_count", hasReviews ? reviewed : null, "valid_text_count", textMetadataKnown ? validTexts.size() : null,
                    "unreviewed_count", hasReviews && textMetadataKnown ? unreviewed : null,
                    "themes", counts.entrySet().stream().map(e -> map("theme", e.getKey(), "count", e.getValue().size(),
                            "denominator", hasReviews ? reviewed : null, "percentage", hasReviews ? percent(e.getValue().size(), reviewed) : null,
                            "participant_percentage", hasReviews ? percent(e.getValue().size(), reviewed) : null)).toList()));
        }
        return result;
    }

    private boolean relevant(String id, String slug) {
        return slug.equals("empleadores") ? id.equals("B04") || id.equals("B05") || id.startsWith("B06_")
                : id.equals("B01") || id.equals("B02") || id.equals("B03") || id.equals("B07") || id.equals("B08");
    }

    private Map<String, Object> contrast(String id, Stratum group, Map<String, Map<String, Object>> definitions, AnalysisPlan plan) {
        var result = map("id", id, "instance_id", group.id() + "/" + id, "stratum_id", group.id(), "survey_slug", group.slug(),
                "instrument_version", group.version(), "study_id", group.study(), "family_id", null, "p_adjusted", null, "reject_h0_adjusted", null);
        if (id.equals("B07")) {
            result.putAll(map("title", "Antigüedad desde el egreso y estado laboral", "status", "PENDIENTE_DEFINICION", "reason", "ANTIGUEDAD_SIN_REFERENCIA_APROBADA", "variables", List.of("antiguedad_egreso", "estado_laboral"), "table", null));
            return result;
        }
        if (id.equals("B08")) {
            var pairs = new ArrayList<Map<String, Object>>();
            for (Map<String, Object> row : group.rows()) if ("CON_VALOR".equals(row.get("meses_vida_profesional__estado"))
                    && "CON_VALOR".equals(row.get("meses_desempleado__estado")) && row.get("meses_vida_profesional") instanceof Number x
                    && row.get("meses_desempleado") instanceof Number y && Double.isFinite(x.doubleValue()) && Double.isFinite(y.doubleValue())) {
                pairs.add(map("response_id", row.get("response_id"), "x", x.doubleValue(), "y", y.doubleValue()));
            }
            result.putAll(map("title", "Vida profesional y desempleo en meses completos", "status", pairs.isEmpty() ? "SIN_DATOS" : "DESCRIPTIVO", "reason", "PARES_NUMERICOS_SIN_CONTRASTE_CATEGORICO",
                    "variables", List.of("meses_vida_profesional", "meses_desempleado"), "unit", "MESES_COMPLETOS", "n_pairs", pairs.size(),
                    "pairs", pairs, "x_summary", StatisticalMath.numeric(pairs.stream().map(p -> (Double) p.get("x")).toList()),
                    "y_summary", StatisticalMath.numeric(pairs.stream().map(p -> (Double) p.get("y")).toList()),
                    "group_summaries", groupedNumbers(group, definitions), "table", null));
            return result;
        }
        List<String> variables = id.startsWith("B06_") ? List.of("tipo_organizacion", "criterio_" + id.substring(4)) : CROSSES.get(id);
        result.put("variables", variables);
        result.put("row_variable", variables.getFirst()); result.put("column_variable", variables.getLast());
        Map<String, Object> x = definitions.get(variables.getFirst()), y = definitions.get(variables.getLast());
        result.put("title", (x == null ? variables.getFirst() : x.get("label")) + " × " + (y == null ? variables.getLast() : y.get("label")));
        if (x == null || y == null || codes(x).isEmpty() || codes(y).isEmpty()
                || "MULTIPLE".equals(x.get("type")) || "MULTIPLE".equals(y.get("type"))) {
            result.putAll(map("status", "PENDIENTE_DEFINICION", "reason", "VARIABLES_SIN_CATALOGO_UNICO_VERIFICABLE", "table", null));
            return result;
        }
        List<String> rows = codes(x), columns = new ArrayList<>(codes(y));
        if (id.startsWith("B06_")) columns.remove("NO_SABE");
        long[][] observed = new long[rows.size()][columns.size()];
        var noSabe = new TreeMap<String, Long>();
        long pairs = 0, excluded = 0;
        for (Map<String, Object> row : group.rows()) {
            Object xv = row.get(variables.getFirst()), yv = row.get(variables.getLast());
            boolean validX = "CON_VALOR".equals(row.get(variables.getFirst() + "__estado")) && rows.contains(xv);
            boolean validY = "CON_VALOR".equals(row.get(variables.getLast() + "__estado")) && columns.contains(yv);
            if (validX && id.startsWith("B06_") && "NO_SABE".equals(yv)
                    && "CON_VALOR".equals(row.get(variables.getLast() + "__estado"))) noSabe.merge(xv.toString(), 1L, Long::sum);
            if (validX && validY) { observed[rows.indexOf(xv)][columns.indexOf(yv)]++; pairs++; } else excluded++;
        }
        Map<String, Object> table = StatisticalMath.contingency(observed, plan.independenceDocumented(), plan.structuralZeros(), plan.alpha());
        result.putAll(map("status", table.get("status"), "reason", table.get("reason"), "rows", rows, "columns", columns,
                "row_labels", rows.stream().map(c -> label(x, c)).toList(), "column_labels", columns.stream().map(c -> label(y, c)).toList(),
                "n_pairs", pairs, "exclusions", excluded, "no_sabe_by_row", noSabe, "table", table));
        return result;
    }

    private List<Map<String, Object>> groupedNumbers(Stratum group, Map<String, Map<String, Object>> definitions) {
        var result = new ArrayList<Map<String, Object>>();
        for (String grouping : List.of("estado_laboral", "sector")) {
            Map<String, Object> groupingDefinition = definitions.get(grouping);
            if (groupingDefinition == null || codes(groupingDefinition).isEmpty()) continue;
            for (String variable : List.of("meses_vida_profesional", "meses_desempleado")) {
                Map<String, Object> numericDefinition = definitions.get(variable);
                if (numericDefinition == null || !"ENTERO".equals(numericDefinition.get("type"))) continue;
                for (String category : codes(groupingDefinition)) {
                    List<Double> values = group.rows().stream().filter(row -> "CON_VALOR".equals(row.get(grouping + "__estado"))
                                    && category.equals(row.get(grouping)) && "CON_VALOR".equals(row.get(variable + "__estado")))
                            .map(row -> row.get(variable)).filter(v -> v instanceof Number n && Double.isFinite(n.doubleValue()))
                            .map(v -> ((Number) v).doubleValue()).toList();
                    result.add(map("group_variable", grouping, "group_source_id", groupingDefinition.get("source_id"),
                            "group_label", groupingDefinition.get("label"), "numeric_variable", variable,
                            "source_id", numericDefinition.get("source_id"), "label", numericDefinition.get("label"),
                            "unit", numericDefinition.get("unit"), "category", category, "category_label", label(groupingDefinition, category),
                            "summary", StatisticalMath.numeric(values)));
                }
            }
        }
        return result;
    }

    private List<Map<String, Object>> families(List<Map<String, Object>> contrasts, double alpha) {
        Map<String, List<Map<String, Object>>> groups = new TreeMap<>();
        for (Map<String, Object> contrast : contrasts) {
            String id = string(contrast, "id");
            if (id.equals("B07") || id.equals("B08")) continue;
            String family = id.startsWith("B06_") ? "CRITERIOS_EMPLEADORES|" + contrast.get("stratum_id") : id + "|" + contrast.get("stratum_id");
            contrast.put("family_id", family);
            groups.computeIfAbsent(family, ignored -> new ArrayList<>()).add(contrast);
        }
        var results = new ArrayList<Map<String, Object>>();
        for (var family : groups.entrySet()) {
            var instances = family.getValue();
            List<Double> ps = instances.stream().map(c -> object(c.get("table")).get("p"))
                    .map(p -> p instanceof Number n ? n.doubleValue() : null).toList();
            int planned = family.getKey().startsWith("CRITERIOS_EMPLEADORES|") ? 10 : instances.size();
            Map<String, Object> adjusted = StatisticalMath.holm(ps, planned, alpha);
            List<?> values = (List<?>) adjusted.get("adjusted_p"), decisions = (List<?>) adjusted.get("reject_h0");
            for (int i = 0; i < instances.size(); i++) {
                instances.get(i).put("p_adjusted", values.get(i)); instances.get(i).put("reject_h0_adjusted", decisions.get(i));
            }
            adjusted.put("family_id", family.getKey());
            adjusted.put("scope", family.getKey().startsWith("CRITERIOS_EMPLEADORES|") ? "DIEZ_CRITERIOS_DEL_ESTRATO" : "CRUCE_EXPLORATORIO_INDIVIDUAL");
            adjusted.put("global_familywise_control", false);
            adjusted.put("contrast_instances", instances.stream().map(c -> c.get("instance_id")).toList());
            results.add(adjusted);
        }
        return results;
    }

    private List<String> codes(Map<String, Object> definition) { return list(definition, "catalog").stream().map(o -> string(o, "code")).toList(); }
    private String label(Map<String, Object> definition, String code) {
        return list(definition, "catalog").stream().filter(o -> code.equals(o.get("code")))
                .map(o -> string(o, "label")).findFirst().orElse(code);
    }
    private List<String> orderedSubset(Map<String, Object> definition, List<String> order) {
        return new ArrayList<>(order.stream().filter(codes(definition)::contains).toList());
    }
    private static Double relative(long numerator, long denominator) { return denominator == 0 ? null : (double) numerator / denominator; }
    private static Double percent(long numerator, long denominator) { return denominator == 0 ? null : 100d * numerator / denominator; }
    private static String string(Map<String, Object> value, String key) { return value.get(key) == null ? "" : value.get(key).toString(); }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of(); }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> list(Map<String, Object> value, String key) { return value.get(key) instanceof List<?> l ? (List<Map<String, Object>>) l : List.of(); }

    public Map<String, Object> numeric(List<Double> values) { return StatisticalMath.numeric(values); }
    public Map<String, Object> contingency(long[][] values, boolean independence, boolean structuralZeros, double alpha) { return StatisticalMath.contingency(values, independence, structuralZeros, alpha); }
    public Map<String, Object> holm(List<Double> values, int plannedCount, double alpha) { return StatisticalMath.holm(values, plannedCount, alpha); }
}

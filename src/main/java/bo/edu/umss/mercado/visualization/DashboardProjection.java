package bo.edu.umss.mercado.visualization;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Presentación de resultados del protocolo; no accede a respuestas ni cambia decisiones. */
@Component
public class DashboardProjection {
    public static final String VERSION = "VISUALIZACION_V1";
    private static final List<String> TITLES = List.of("Resumen del estudio", "Titulados: perfil y trayectoria",
            "Formación continua", "Empleadores y demanda", "Valoración de la formación",
            "Cruces y contrastes", "Calidad y cobertura");

    @SuppressWarnings("unchecked")
    public Map<String, Object> project(Map<String, Object> input) {
        // El motor devuelve arrays numéricos; las lecturas guardadas, listas JSON.
        Map<String, Object> data = CanonicalJson.read(CanonicalJson.json(input), Map.class);
        var sections = new LinkedHashMap<String, Map<String, Object>>();
        for (int i = 0; i < TITLES.size(); i++) sections.put("V0" + (i + 1), map("id", "V0" + (i + 1),
                "title", TITLES.get(i), "description", "Resultados del conjunto aplicado. La unidad es un envío.", "charts", new ArrayList<Map<String, Object>>()));
        Map<String, Object> context = object(data.get("context"));
        Map<String, Object> result = object(data.get("result"));
        List<Map<String, Object>> comparisons = list(data, "comparison");
        for (Map<String, Object> stratum : list(result, "strata")) {
            String prefix = text(stratum, "stratum_id");
            var base = chart(prefix + "/base", "indicator", "Respuestas recibidas: " + surveyName(stratum), stratum);
            base.put("available", number(stratum.get("n_base")) > 0);
            base.put("metrics", map("value", stratum.get("n_base"), "n_base", stratum.get("n_base")));
            base.put("table", table(List.of("Cuestionario", "Envíos"), List.of(row(surveyName(stratum), stratum.get("n_base")))));
            add(sections, "V01", base);
            for (Map<String, Object> item : list(stratum, "indicators")) {
                if (text(item, "variable").startsWith("criterio_")) continue;
                var card = chart(prefix + "/indicator/" + item.get("variable") + "/" + item.get("indicator"), "indicator", indicatorTitle(item), stratum);
                card.put("status", item.get("status")); card.put("available", number(item.get("denominator")) > 0);
                card.put("reason", "El porcentaje utiliza respuestas válidas de esta pregunta; no es una tasa poblacional oficial.");
                card.put("unit", "%"); card.put("metrics", map("value", item.get("percentage"), "numerator", item.get("numerator"),
                        "denominator", item.get("denominator"), "n_base", stratum.get("n_base")));
                card.put("table", table(List.of("Numerador", "Denominador válido", "Porcentaje", "Base del estrato"),
                        List.of(row(item.get("numerator"), item.get("denominator"), item.get("percentage"), stratum.get("n_base")))));
                add(sections, "V01", card);
            }
            for (Map<String, Object> item : list(stratum, "frequencies")) {
                add(sections, section(stratum, item), frequency(stratum, item));
                if (!comparisons.isEmpty() && !text(item, "variable").startsWith("criterio_")) {
                    Map<String, Object> compared = comparisonBar(stratum, item, comparisons);
                    if (compared != null) add(sections, section(stratum, item), compared);
                }
            }
            for (Map<String, Object> item : list(stratum, "numeric")) add(sections, section(stratum, item), box(stratum, item));
            for (Map<String, Object> item : list(stratum, "multiple")) add(sections, section(stratum, item), multiple(stratum, item));
            for (Map<String, Object> item : list(stratum, "texts")) add(sections, section(stratum, item), themes(stratum, item));
            for (Map<String, Object> item : list(stratum, "coverage")) add(sections, "V07", coverage(stratum, item));
            if ("empleadores".equals(stratum.get("survey_slug"))) {
                for (int first : List.of(1, 6)) add(sections, "V05", radar(stratum, first, comparisons));
            }
        }
        for (Map<String, Object> cross : list(result, "contrasts")) {
            Map<String, Object> visual = cross(cross);
            Map<String, Object> family = find(list(result, "families"), "family_id", cross.get("family_id"));
            if (!family.isEmpty()) {
                Map<String, Object> metrics = new LinkedHashMap<>(object(visual.get("metrics")));
                metrics.put("M planificado", family.get("planned_count"));
                metrics.put("Contrastes ejecutados", family.get("executed_count"));
                metrics.put("Ajuste", family.get("method")); visual.put("metrics", metrics);
            }
            add(sections, "V06", visual);
            for (var group : list(cross, "group_summaries")) {
                var summary = new LinkedHashMap<>(object(group.get("summary")));
                summary.put("variable", group.get("numeric_variable") + "/" + group.get("group_variable") + "/" + group.get("category"));
                summary.put("label", group.get("label") + " · " + group.get("group_label") + ": " + group.get("category_label"));
                summary.put("unit", group.get("unit"));
                add(sections, "V06", box(cross, summary));
            }
            for (String axis : List.of("x_summary", "y_summary")) if (cross.get(axis) instanceof Map<?, ?>) {
                var summary = new LinkedHashMap<>(object(cross.get(axis)));
                summary.put("variable", cross.get("instance_id") + "/" + axis);
                summary.put("label", axis.equals("x_summary") ? "Vida profesional: muestra pareada" : "Desempleo: muestra pareada");
                summary.put("unit", "Meses completos"); add(sections, "V06", box(cross, summary));
            }
        }
        var pending = chart("antiguedad", "table", "Antigüedad y cohortes", Map.of());
        pending.put("available", false); pending.put("status", "PENDIENTE_DEFINICION");
        pending.put("reason", "La referencia para antigüedad y la agrupación por cohortes requieren una definición aprobada. Se muestran años de egreso sin derivar esos indicadores.");
        add(sections, "V02", pending);
        List<Map<String, Object>> issues = list(context, "issue_summary");
        var quality = chart("incidencias", "table", "Incidencias de calidad", Map.of());
        quality.put("reason", "Una respuesta puede tener varias incidencias: estos conteos no representan categorías disjuntas de participantes.");
        quality.put("table", table(List.of("Regla", "Estado", "Incidencias"), issues.stream()
                .map(i -> row(i.get("rule_code"), i.get("severity"), i.get("count"))).toList()));
        add(sections, "V07", quality);
        return map("context", context, "filters", data.get("filters"), "visual_config_version", VERSION,
                "sections", new ArrayList<>(sections.values()), "limitations", result.getOrDefault("limitations", List.of()));
    }

    private Map<String, Object> frequency(Map<String, Object> stratum, Map<String, Object> item) {
        String variable = text(item, "variable");
        Map<String, Object> cov = object(item.get("coverage"));
        var categories = orderedCategories(stratum, item);
        var chart = chart(text(stratum, "stratum_id") + "/frequency/" + variable,
                variable.startsWith("criterio_") ? "stacked" : "bar", label(item), stratum);
        chart.put("status", item.get("status")); chart.put("available", number(cov.get("n_validos")) > 0);
        var counts = categories.stream().map(c -> c.get("count")).toList();
        var percentages = categories.stream().map(c -> c.get("percentage")).toList();
        List<List<Object>> rows = categories.stream().map(c -> row(categoryLabel(c), c.get("count"), c.get("denominator"), c.get("percentage"))).toList();
        Map<String, Object> table = table(List.of("Categoría", "Envíos", "Respuestas válidas", "% de válidos"), rows);
        chart.put("table", table); chart.put("categories", categories.stream().map(this::categoryLabel).toList());
        chart.put("series", List.of(map("name", "Envíos", "values", counts)));
        chart.put("modes", List.of(mode("counts", "Conteos", "Envíos", counts, table, null),
                mode("percentage", "% de respuestas válidas", "%", percentages, table, 100)));
        chart.put("reason", "Base válida: " + cov.get("n_validos") + ". No sabe: " + cov.get("n_no_sabe")
                + ". No puedo valorar: " + cov.get("n_no_puede_valorar") + ". Las ausencias se muestran en Calidad y cobertura.");
        if (variable.startsWith("criterio_")) {
            long substantive = number(cov.get("n_sustantivos"));
            var series = new ArrayList<Map<String, Object>>();
            var completeRows = new ArrayList<List<Object>>();
            for (Map<String, Object> category : categories) {
                boolean noSabe = "NO_SABE".equals(category.get("code"));
                Double percentage = noSabe ? null : percentage(number(category.get("count")), substantive);
                completeRows.add(row(categoryLabel(category), category.get("count"), category.get("denominator"), category.get("percentage"),
                        noSabe ? null : substantive, percentage));
                if (!noSabe) series.add(map("name", categoryLabel(category), "values", row(percentage)));
            }
            chart.put("categories", List.of("Respuestas sustantivas")); chart.put("series", series); chart.put("modes", List.of());
            chart.put("min", 0); chart.put("max", 100); chart.put("unit", "%"); chart.put("available", substantive > 0);
            chart.put("status", substantive > 0 ? "LISTO" : "SIN_DATOS");
            chart.put("reason", substantive == 0 && number(cov.get("n_no_sabe")) > 0
                    ? "Todas las respuestas válidas son No sabe. No hay distribución sustantiva."
                    : "Distribución sobre " + substantive + " respuestas sustantivas. No sabe: " + cov.get("n_no_sabe") + " de " + cov.get("n_validos") + " válidas.");
            chart.put("table", table(List.of("Categoría", "Envíos", "N válidos", "% de válidos", "N sustantivos", "% sustantivo"), completeRows));
        }
        return chart;
    }

    private List<Map<String, Object>> orderedCategories(Map<String, Object> stratum, Map<String, Object> item) {
        var categories = new ArrayList<>(list(item, "counts"));
        Map<String, Object> ordinal = find(list(stratum, "ordinal"), "variable", item.get("variable"));
        Object rawOrder = ordinal.get("semantic_order");
        if (rawOrder instanceof List<?> order) categories.sort(Comparator.comparingInt(c -> {
            int index = order.indexOf(c.get("code")); return index < 0 ? order.size() : index;
        }));
        return categories;
    }

    private Map<String, Object> multiple(Map<String, Object> stratum, Map<String, Object> item) {
        var chart = chart(text(stratum, "stratum_id") + "/multiple/" + item.get("variable"), "bar", label(item), stratum);
        var options = list(item, "options");
        var table = table(List.of("Opción", "Envíos", "N participantes", "% participantes", "Total menciones", "% menciones"),
                options.stream().map(o -> row(categoryLabel(o), o.get("count"), o.get("denominator"), o.get("participant_percentage"),
                        o.get("mention_denominator"), o.get("mention_percentage"))).toList());
        chart.put("categories", options.stream().map(this::categoryLabel).toList());
        chart.put("series", List.of(map("name", "% participantes", "values", options.stream().map(o -> o.get("participant_percentage")).toList())));
        chart.put("modes", List.of(mode("participants", "% participantes", "%", options.stream().map(o -> o.get("participant_percentage")).toList(), table, 100),
                mode("counts", "Conteos", "Envíos", options.stream().map(o -> o.get("count")).toList(), table, null),
                mode("mentions", "% menciones", "%", options.stream().map(o -> o.get("mention_percentage")).toList(), table, 100)));
        chart.put("table", table); chart.put("status", item.get("status")); chart.put("available", number(item.get("n_validos")) > 0);
        chart.put("reason", "Selección múltiple: los porcentajes de participantes pueden sumar más del 100 %. Cada opción cuenta una vez por envío. Participantes: "
                + item.get("n_validos") + "; menciones: " + item.get("mention_count") + ".");
        return chart;
    }

    private Map<String, Object> box(Map<String, Object> stratum, Map<String, Object> item) {
        var chart = chart(text(stratum, "stratum_id") + "/numeric/" + item.get("variable"), "boxplot", label(item), stratum);
        long n = number(item.get("n"));
        if (!stratum.containsKey("n_base")) chart.put("n_base", n);
        chart.put("status", item.get("status")); chart.put("available", n >= 2); chart.put("unit", item.get("unit")); chart.put("n", n);
        chart.put("categories", List.of(label(item))); chart.put("summary", item);
        chart.put("reason", n == 1 ? "Un único valor: " + item.get("min") + ". No se estima dispersión muestral."
                : n == 0 ? "No hay respuestas numéricas válidas." : "Bigotes: mínimo y máximo. Cuartiles tipo 7; no se clasifican valores atípicos.");
        chart.put("series", n >= 2 ? List.of(map("name", "Resumen numérico", "values", List.of(row(item.get("min"), item.get("q1"), item.get("median"), item.get("q3"), item.get("max"))))) : List.of());
        chart.put("table", table(List.of("n", "Media", "Mediana", "Desv. muestral", "Mínimo", "Q1", "Q3", "Máximo", "Unidad"),
                List.of(row(n, item.get("mean"), item.get("median"), item.get("sample_standard_deviation"), item.get("min"), item.get("q1"), item.get("q3"), item.get("max"), item.get("unit")))));
        return chart;
    }

    private Map<String, Object> themes(Map<String, Object> stratum, Map<String, Object> item) {
        var chart = chart(text(stratum, "stratum_id") + "/themes/" + item.get("variable") + "/" + item.get("codebook_version"), "bar", label(item), stratum);
        var themes = list(item, "themes");
        chart.put("categories", themes.stream().map(t -> text(t, "theme")).toList());
        chart.put("series", List.of(map("name", "% textos revisados", "values", themes.stream().map(t -> t.get("percentage")).toList())));
        chart.put("unit", "%"); chart.put("min", 0); chart.put("max", 100);
        chart.put("status", item.get("status")); chart.put("available", number(item.get("reviewed_count")) > 0 && !themes.isEmpty());
        chart.put("reason", "Libro de códigos: " + item.get("codebook_version") + ". Textos revisados: " + item.get("reviewed_count")
                + "; válidos: " + item.get("valid_text_count") + ". Los temas cuentan envíos, no menciones repetidas; un texto puede tener varios temas.");
        chart.put("table", table(List.of("Tema", "Envíos", "Base revisada", "%"),
                themes.stream().map(t -> row(t.get("theme"), t.get("count"), t.get("denominator"), t.get("percentage"))).toList()));
        return chart;
    }

    private Map<String, Object> coverage(Map<String, Object> stratum, Map<String, Object> item) {
        var counts = object(item.get("counts"));
        var chart = chart(text(stratum, "stratum_id") + "/coverage/" + item.get("variable"), "bar", "Cobertura: " + item.get("variable"), stratum);
        Map<String, Object> states = object(counts.get("state_counts"));
        var rows = new ArrayList<List<Object>>();
        states.forEach((state, count) -> rows.add(row(stateName(state), count, counts.get("n_base"))));
        chart.put("categories", states.keySet().stream().map(this::stateName).toList());
        chart.put("series", List.of(map("name", "Envíos", "values", new ArrayList<>(states.values()))));
        chart.put("table", table(List.of("Estado final", "Envíos", "Base"), rows)); chart.put("available", number(counts.get("n_base")) > 0);
        chart.put("metrics", map("Base", counts.get("n_base"), "Presente", counts.get("n_present"), "Aplicable", counts.get("n_applicable"),
                "Con valor antes de calidad", counts.get("n_con_value"), "Válidos", counts.get("n_validos")));
        chart.put("reason", "Las bases desconocidas se muestran como ausentes. No sabe y No puedo valorar siguen siendo respuestas con valor.");
        return chart;
    }

    private Map<String, Object> radar(Map<String, Object> stratum, int first, List<Map<String, Object>> comparisons) {
        String panel = first == 1 ? "R01" : "R02";
        var chart = chart(text(stratum, "stratum_id") + "/" + panel, "radar", "Valoraciones de empleadores: criterios " + first + "–" + (first + 4), stratum);
        var axes = new ArrayList<Map<String, Object>>();
        for (int i = first; i < first + 5; i++) {
            String variable = "criterio_%02d".formatted(i);
            Map<String, Object> frequency = find(list(stratum, "frequencies"), "variable", variable);
            axes.add(map("name", frequency.isEmpty() ? "Criterio " + i : label(frequency), "min", 0, "max", 100));
        }
        var groups = new ArrayList<Map<String, Object>>();
        if (comparisons.isEmpty()) groups.add(map("label", "Selección aplicada", "stratum", stratum));
        else for (var comparison : comparisons) {
            Map<String, Object> group = find(list(object(comparison.get("result")), "strata"), "stratum_id", stratum.get("stratum_id"));
            groups.add(map("label", comparison.get("label"), "stratum", group));
        }
        var rows = new ArrayList<List<Object>>();
        var series = new ArrayList<Map<String, Object>>();
        boolean complete = true;
        for (var group : groups) {
            Map<String, Object> source = object(group.get("stratum"));
            var values = new ArrayList<Object>(); var denominators = new ArrayList<Object>();
            for (int i = first; i < first + 5; i++) {
                String variable = "criterio_%02d".formatted(i);
                Map<String, Object> indicator = list(source, "indicators").stream()
                        .filter(c -> variable.equals(c.get("variable")) && "ACUERDO_SOBRE_SUSTANTIVOS".equals(c.get("indicator"))).findFirst().orElse(Map.of());
                Map<String, Object> frequency = find(list(source, "frequencies"), "variable", variable);
                Map<String, Object> cov = object(frequency.get("coverage"));
                Object value = indicator.get("percentage"), denominator = indicator.get("denominator");
                complete &= value instanceof Number n && Double.isFinite(n.doubleValue()) && n.doubleValue() >= 0 && n.doubleValue() <= 100 && number(denominator) > 0;
                values.add(value); denominators.add(denominator);
                rows.add(row(group.get("label"), axes.get(i - first).get("name"), indicator.get("numerator"), denominator,
                        cov.get("n_no_sabe"), cov.get("n_validos"), value));
            }
            series.add(map("name", group.get("label"), "values", values, "denominators", denominators));
        }
        chart.put("axes", axes); chart.put("series", complete ? series : List.of()); chart.put("available", complete);
        chart.put("status", complete ? "LISTO" : "SIN_DATOS"); chart.put("unit", "% de acuerdo sustantivo");
        chart.put("reason", complete ? "Acuerdo parcial o total sobre respuestas sustantivas; No sabe queda fuera del denominador. Cada criterio conserva su base. El área del polígono no es un índice."
                : "Falta una base sustantiva válida en al menos un criterio o grupo. No se completa el radar con ceros ni con promedios.");
        chart.put("table", table(List.of("Grupo", "Criterio", "Acuerdo parcial/total", "N sustantivos", "No sabe", "N válidos", "% acuerdo"), rows));
        return chart;
    }

    private Map<String, Object> comparisonBar(Map<String, Object> stratum, Map<String, Object> item, List<Map<String, Object>> comparisons) {
        var categories = orderedCategories(stratum, item);
        var series = new ArrayList<Map<String, Object>>(); var rows = new ArrayList<List<Object>>(); boolean available = true;
        for (var comparison : comparisons) {
            Map<String, Object> group = find(list(object(comparison.get("result")), "strata"), "stratum_id", stratum.get("stratum_id"));
            var frequency = find(list(group, "frequencies"), "variable", item.get("variable"));
            available &= number(object(frequency.get("coverage")).get("n_validos")) > 0;
            var values = new ArrayList<Object>();
            for (var category : categories) {
                var count = find(list(frequency, "counts"), "code", category.get("code"));
                if (count.isEmpty() && "anio_egreso".equals(item.get("variable")) && !frequency.isEmpty()
                        && number(object(frequency.get("coverage")).get("n_validos")) > 0)
                    count = map("count", 0, "denominator", object(frequency.get("coverage")).get("n_validos"), "percentage", 0.0);
                values.add(count.get("percentage"));
                rows.add(row(comparison.get("label"), categoryLabel(category), count.get("count"), count.get("denominator"), count.get("percentage")));
            }
            series.add(map("name", comparison.get("label"), "values", values));
        }
        if (series.isEmpty()) return null;
        var chart = chart(text(stratum, "stratum_id") + "/comparison/" + item.get("variable"), "bar", "Comparación: " + label(item), stratum);
        chart.put("available", available); chart.put("status", available ? "LISTO" : "SIN_DATOS"); chart.put("series", available ? series : List.of());
        chart.put("categories", categories.stream().map(this::categoryLabel).toList()); chart.put("min", 0); chart.put("max", 100); chart.put("unit", "%");
        chart.put("table", table(List.of("Grupo", "Categoría", "Envíos", "N válidos del grupo", "%"), rows));
        chart.put("reason", available ? "Los grupos conservan bases propias y las mismas categorías y escala. Comparación descriptiva y exploratoria."
                : "Una de las series carece de respuestas válidas. Se conservan las tablas de cada grupo.");
        return chart;
    }

    private Map<String, Object> cross(Map<String, Object> cross) {
        var chart = chart(text(cross, "instance_id"), "table", text(cross, "title").isBlank() ? text(cross, "id") : text(cross, "title"), cross);
        chart.put("status", cross.get("status")); chart.put("reason", reason(text(cross, "status")));
        chart.put("n_pairs", cross.get("n_pairs"));
        chart.put("n_base", cross.get("n_pairs"));
        Map<String, Object> matrix = object(cross.get("table"));
        if (!matrix.isEmpty()) {
            chart.put("type", "heatmap"); chart.put("available", number(cross.get("n_pairs")) > 0);
            List<String> rows = labels(cross, "row_labels", "rows"), columns = labels(cross, "column_labels", "columns");
            chart.put("rows", rows); chart.put("columns", columns);
            var modes = new ArrayList<Map<String, Object>>();
            List<String> fields = List.of("observed", "row_percentages", "column_percentages", "total_percentages", "expected");
            List<String> ids = List.of("observed", "row", "column", "total", "expected");
            List<String> names = List.of("Conteos observados", "% dentro de cada fila", "% dentro de cada columna", "% de pares válidos", "Frecuencia esperada bajo independencia");
            for (int i = 0; i < fields.size(); i++) if (matrix.get(fields.get(i)) instanceof List<?> values) {
                var headers = new ArrayList<>(List.of("Filas / columnas")); headers.addAll(columns); headers.add("Base de fila");
                var tableRows = new ArrayList<List<Object>>();
                for (int r = 0; r < rows.size(); r++) {
                    var row = new ArrayList<Object>(); row.add(rows.get(r)); row.addAll((List<?>) values.get(r));
                    row.add(((List<?>) matrix.get("row_totals")).get(r)); tableRows.add(row);
                }
                var total = new ArrayList<Object>(); total.add("Base de columna"); total.addAll((List<?>) matrix.get("column_totals")); total.add(matrix.get("n")); tableRows.add(total);
                var table = table(headers, tableRows);
                Number max = i > 0 && i < 4 ? 100 : maximum(values);
                var mode = mode(ids.get(i), names.get(i), i > 0 && i < 4 ? "%" : i == 4 ? "Frecuencia esperada" : "Envíos", values, table, max);
                var denominators = new ArrayList<List<Object>>();
                for (int r = 0; r < rows.size(); r++) {
                    var bases = new ArrayList<Object>();
                    for (int c = 0; c < columns.size(); c++) bases.add(i == 1 ? ((List<?>) matrix.get("row_totals")).get(r)
                            : i == 2 ? ((List<?>) matrix.get("column_totals")).get(c) : matrix.get("n"));
                    denominators.add(bases);
                }
                mode.put("denominators", denominators);
                mode.put("base_description", i == 1 ? "Margen válido de cada fila" : i == 2 ? "Margen válido de cada columna" : "N pares válidos: " + cross.get("n_pairs"));
                modes.add(mode);
            }
            chart.put("modes", modes); chart.put("table", modes.isEmpty() ? table(List.of(), List.of()) : modes.getFirst().get("table"));
            chart.put("metrics", map("status", cross.get("status"), "Pares válidos", cross.get("n_pairs"), "Exclusiones", cross.get("exclusions"),
                    "Menor esperada", matrix.get("min_expected"), "Esperadas menores a 5", matrix.get("below_five_count"), "Chi-cuadrado", matrix.get("chi_square"),
                    "Grados de libertad", matrix.get("df"), "p original", matrix.get("p"), "p ajustada", cross.get("p_adjusted"), "Alpha", matrix.get("alpha"),
                    "V de Cramér", matrix.get("cramers_v"), "Familia", cross.get("family_id"), "Decisión ajustada", decision(cross.get("reject_h0_adjusted"))));
        } else if (cross.get("pairs") instanceof List<?> pairs) {
            var points = pairs.stream().map(p -> object(p)).map(p -> row(p.get("x"), p.get("y"))).toList();
            chart.put("type", "scatter"); chart.put("available", !pairs.isEmpty());
            chart.put("series", List.of(map("name", "Pares de duraciones", "values", points)));
            chart.put("x_label", "Vida profesional"); chart.put("y_label", "Tiempo desempleado"); chart.put("x_unit", "meses"); chart.put("y_unit", "meses"); chart.put("unit", "Meses completos");
            chart.put("reason", "Pares con ambas duraciones válidas. La dispersión es descriptiva, sin prueba de correlación ni interpretación causal.");
            chart.put("table", table(List.of("Vida profesional (meses)", "Desempleo (meses)"), points));
        }
        return chart;
    }

    private String section(Map<String, Object> stratum, Map<String, Object> item) {
        String source = text(item, "source_id"), variable = text(item, "variable");
        if ("empleadores".equals(stratum.get("survey_slug"))) return variable.startsWith("criterio_") ? "V05" : "V04";
        if (source.matches("T(03|04|05|06|07|08|09|10|37|38).*")) return "V03";
        if (source.matches("T(11|23|24|25|26|27|28|29|30|39|40).*")) return "V05";
        return "V02";
    }

    private Map<String, Object> chart(String id, String type, String title, Map<String, Object> stratum) {
        return map("id", id, "type", type, "title", title, "subtitle", stratum.isEmpty() ? "" : surveyName(stratum) + " · Versión "
                + text(stratum, "instrument_version") + " · Estudio " + text(stratum, "study_id"),
                "status", "LISTO", "available", false, "reason", "", "n_base", stratum.get("n_base"), "unit", "Envíos",
                "table", table(List.of(), List.of()));
    }

    private String reason(String status) {
        return switch (status) {
            case "CALCULADO" -> "Pearson calculado. La decisión corresponde al backend y conserva precisión completa; no demuestra causalidad ni representatividad poblacional.";
            case "FRECUENCIAS_INSUFICIENTES" -> "No se ejecutó el contraste: frecuencias esperadas insuficientes. Se conserva la descripción.";
            case "SIN_VARIACION" -> "La selección no tiene suficientes categorías activas para este contraste.";
            case "INDEPENDENCIA_NO_DOCUMENTADA" -> "No se ejecutó el contraste: falta documentar unidad de análisis e independencia de los envíos.";
            case "PENDIENTE_DEFINICION" -> "El indicador aún no tiene una definición disponible.";
            case "ESTRUCTURA_INCOMPATIBLE" -> "La estructura de este cruce requiere otro análisis; los ceros estructurales declarados no tienen identificación por celda.";
            case "SIN_DATOS" -> "No hay pares válidos para esta selección.";
            default -> "Resultado descriptivo del conjunto seleccionado: " + stateName(status);
        };
    }

    private String stateName(String state) {
        return switch (state) {
            case "CON_VALOR" -> "Con valor"; case "SIN_RESPUESTA" -> "Sin respuesta"; case "NO_APLICA" -> "No aplica";
            case "NO_RECOLECTADA" -> "No recolectada en la versión"; case "VALOR_INVALIDO" -> "Valor excluido por calidad";
            case "SIN_ESTADO_HISTORICO" -> "Estado histórico desconocido"; default -> state;
        };
    }
    private String indicatorTitle(Map<String, Object> item) {
        return switch (text(item, "indicator")) {
            case "OCUPACION_DECLARADA" -> "Ocupación declarada"; case "NO_TRABAJA_DECLARADO" -> "Desempleo declarado";
            default -> switch (text(item, "variable")) {
                case "formacion_complementaria" -> "Formación complementaria declarada"; case "interes_posgrado" -> "Interés en posgrado";
                case "interes_red_contactos" -> "Interés en red profesional"; case "contratacion_reciente" -> "Contratación reciente declarada";
                default -> text(item, "variable");
            };
        };
    }
    private String decision(Object value) { return value == null ? "Prueba sin ejecutar" : Boolean.TRUE.equals(value) ? "Rechazo de H0" : "No rechazo de H0"; }
    private Number maximum(List<?> matrix) {
        double max = 0; for (Object r : matrix) if (r instanceof List<?> cells) for (Object cell : cells)
            if (cell instanceof Number n && Double.isFinite(n.doubleValue())) max = Math.max(max, n.doubleValue());
        return max;
    }
    private Map<String, Object> mode(String id, String label, String unit, Object values, Map<String, Object> table, Number max) {
        return map("id", id, "label", label, "unit", unit, "values", values, "table", table, "min", 0, "max", max);
    }
    private static Map<String, Object> table(List<String> columns, List<List<Object>> rows) { return map("columns", columns, "rows", rows); }
    private static List<String> labels(Map<String, Object> source, String preferred, String fallback) {
        Object value = source.get(preferred) instanceof List<?> ? source.get(preferred) : source.get(fallback);
        return value instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of();
    }
    private String surveyName(Map<String, Object> s) { return "titulados".equals(s.get("survey_slug")) ? "Titulados" : "Empleadores"; }
    private String label(Map<String, Object> item) { return item.get("label") == null ? text(item, "variable") : item.get("label").toString(); }
    private String categoryLabel(Map<String, Object> item) { return item.get("label") == null ? text(item, "code") : item.get("label").toString(); }
    private static String text(Map<String, Object> map, String key) { return map.get(key) == null ? "" : map.get(key).toString(); }
    private static long number(Object n) { return n instanceof Number number ? number.longValue() : 0; }
    private static Double percentage(long count, long base) { return base == 0 ? null : 100.0 * count / base; }
    private static List<Object> row(Object... cells) { return new ArrayList<>(java.util.Arrays.asList(cells)); }
    @SuppressWarnings("unchecked") private static Map<String, Object> object(Object value) { return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    @SuppressWarnings("unchecked") private static List<Map<String, Object>> list(Map<String, Object> source, String key) { return source.get(key) instanceof List<?> l ? (List<Map<String, Object>>) l : List.of(); }
    private static Map<String, Object> find(List<Map<String, Object>> values, String key, Object value) { return values.stream().filter(v -> java.util.Objects.equals(v.get(key), value)).findFirst().orElse(Map.of()); }
    @SuppressWarnings("unchecked") private static void add(Map<String, Map<String, Object>> sections, String section, Map<String, Object> chart) { ((List<Map<String, Object>>) sections.get(section).get("charts")).add(chart); }
    private static Map<String, Object> map(Object... entries) { var result = new LinkedHashMap<String, Object>(); for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]); return result; }
}

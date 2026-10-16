package bo.edu.umss.mercado.wrangling;

import bo.edu.umss.mercado.data.CaptureStore.Input;
import bo.edu.umss.mercado.survey.Question;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Preparación determinista con la definición de cada captura, sin consultar el catálogo vigente. */
final class WranglingEngine {
    static final String RULE_VERSION = "2026-10-wrangling-4";
    private static final Set<String> CAPTURE_STATES = Set.of("CON_VALOR", "SIN_RESPUESTA", "NO_APLICA");
    private static final List<String> T_IDS = List.of("edad", "genero", "formacion_complementaria", "nivel_cursado",
            "institucion_cursada", "financiamiento_cursado", "programa_cursado", "interes_posgrado", "area_interes",
            "institucion_interes", "opinion_posgrado_fcyt", "meses_vida_profesional", "meses_desempleado", "estado_laboral",
            "origen_emprendimiento", "entregable_emprendimiento", "financiamiento_emprendimiento", "satisfaccion_emprendimiento",
            "importancia_formacion_negocio", "primer_empleo_actual", "tiempo_primer_empleo", "cantidad_empleos",
            "satisfaccion_formacion", "concordancia_formacion", "aspectos_utiles", "aspectos_mejorar", "asignaturas_ventaja",
            "otra_asignatura_ventaja", "asignaturas_poca_utilidad", "otra_asignatura_poca_utilidad", "interes_red_contactos",
            "email_contacto", "telefono_contacto", "linkedin", "anio_egreso", "sector", "nivel_interes", "financiamiento_futuro");
    private static final List<String> E_IDS = List.of("email_contacto", "nombre_organizacion", "tipo_organizacion",
            "tamano_organizacion", "sector", "contratacion_reciente", "probabilidad_contratacion", "nivel_formacion_demandado",
            "medio_convocatoria", "cargos_desempenados", "areas_emergentes", "herramientas_tecnologicas", "competencias_requeridas");

    record Prepared(Input input, String origin, Map<String, List<String>> answers, Map<String, String> states,
                    Set<String> correctedQuestions, List<Map<String, Object>> coding,
                    List<Map<String, Object>> textReviews) {}
    record Product(List<Map<String, Object>> dictionary, List<Map<String, Object>> graduates,
                   List<Map<String, Object>> employers, List<Map<String, Object>> selections,
                   List<Map<String, Object>> coding, List<Map<String, Object>> issues,
                   List<Map<String, Object>> quality, List<Map<String, Object>> coverage,
                   List<Map<String, Object>> textReviews) {}

    Product prepare(List<Prepared> inputs) {
        var schemas = new TreeMap<String, Map<String, Question>>();
        var optionSchemas = new TreeMap<String, Set<String>>();
        var dictionary = new ArrayList<Map<String, Object>>();
        var dictionaryKeys = new HashSet<String>();
        for (Prepared p : inputs) {
            if (p.input().definition() == null) continue;
            var schema = schemas.computeIfAbsent(p.input().slug(), ignored -> new TreeMap<>());
            for (Question q : p.input().definition().questions()) {
                schema.putIfAbsent(q.id(), q);
                if (q.type().equals("checkbox")) {
                    var options = optionSchemas.computeIfAbsent(p.input().slug() + "/" + q.id(), ignored -> new TreeSet<>());
                    q.options().forEach(option -> options.add(option.value()));
                }
                String key = p.input().slug() + "/" + p.input().version() + "/" + q.id();
                if (dictionaryKeys.add(key)) dictionary.add(dictionary(p.input(), q));
            }
        }
        dictionary.sort(java.util.Comparator.comparing(d -> d.get("survey_slug") + "/" + d.get("instrument_version") + "/" + d.get("variable")));
        var graduates = new ArrayList<Map<String, Object>>();
        var employers = new ArrayList<Map<String, Object>>();
        var selections = new ArrayList<Map<String, Object>>();
        var coding = new ArrayList<Map<String, Object>>();
        var issues = new ArrayList<Map<String, Object>>();
        var qualityCounts = new TreeMap<String, Integer>();
        var coverage = new ArrayList<Map<String, Object>>();
        var textReviews = new ArrayList<Map<String, Object>>();
        for (Prepared p : inputs) {
            Input in = p.input();
            var row = map("response_id", in.responseId(), "instrument_version", in.version(), "study_id", in.studyId(),
                    "origin", p.origin(), "capture_provenance", in.provenance());
            Map<String, Question> own = new TreeMap<>();
            if (in.definition() != null) in.definition().questions().forEach(q -> own.put(q.id(), q));
            Map<String, String> resolvedStates = new TreeMap<>();
            Map<String, Object> resolvedValues = new TreeMap<>();
            Map<String, String> beforeQuality = new TreeMap<>();
            if (in.definition() == null) {
                issues.add(issue(in, null, "DEFINICION_HISTORICA_NO_REGISTRADA", "PENDIENTE_REGLA",
                        "No existe una definición congelada para interpretar esta versión."));
            }
            for (Question q : own.values()) {
                if (privacy(in.slug(), q).equals("PRIVACIDAD_NO_REVISADA")) {
                    issues.add(issue(in, q.id(), "PENDIENTE_REVISION_PRIVACIDAD", "PENDIENTE_REGLA",
                            "Esta variable no pertenece a la lista explícita autorizada para salidas analíticas."));
                }
                List<String> values = p.answers().getOrDefault(q.id(), List.of());
                String state = p.states().get(q.id());
                if (p.correctedQuestions().contains(q.id())
                        || (q.dependsOn() != null && p.correctedQuestions().contains(q.dependsOn()))) {
                    state = !q.applicable(p.answers()) ? "NO_APLICA" : values.isEmpty() ? "SIN_RESPUESTA" : "CON_VALOR";
                }
                Object value = null;
                beforeQuality.put(q.id(), state);
                if (state == null) {
                    state = "SIN_ESTADO_HISTORICO";
                    issues.add(issue(in, q.id(), "ESTADO_HISTORICO_NO_REGISTRADO", "PENDIENTE_REGLA",
                            "No se infiere un estado de captura que no fue registrado."));
                } else if (!CAPTURE_STATES.contains(state)) {
                    state = "VALOR_INVALIDO";
                    issues.add(issue(in, q.id(), "ESTADO_FUERA_DOMINIO", "ERROR_DOMINIO", "Estado no admitido por la captura."));
                } else if (state.equals("CON_VALOR")) {
                    String error = validate(q, values);
                    if (!q.applicable(p.answers())) error = "La respuesta tiene valor en una pregunta no aplicable.";
                    if (error != null) {
                        state = "VALOR_INVALIDO";
                        issues.add(issue(in, q.id(), "VALOR_FUERA_DOMINIO", "ERROR_DOMINIO", error));
                    } else value = converted(q, values);
                } else if ((state.equals("NO_APLICA") && q.applicable(p.answers()))
                        || (state.equals("SIN_RESPUESTA") && !q.applicable(p.answers()))) {
                    state = "VALOR_INVALIDO";
                    issues.add(issue(in, q.id(), "ESTADO_APLICABILIDAD_INCOHERENTE", "ERROR_DOMINIO", "Estado y condición de esta versión no coinciden."));
                } else if (state.equals("SIN_RESPUESTA") && q.required() && q.applicable(p.answers())) {
                    issues.add(issue(in, q.id(), "OBLIGATORIA_SIN_RESPUESTA", "ERROR_DOMINIO", "Falta una respuesta obligatoria aplicable."));
                } else if (!values.isEmpty()
                        && !(q.dependsOn() != null && p.correctedQuestions().contains(q.dependsOn()))) {
                    state = "VALOR_INVALIDO";
                    issues.add(issue(in, q.id(), "VALOR_ESTADO_INCOHERENTE", "ERROR_DOMINIO", "Valor y estado registrados no coinciden."));
                }
                resolvedStates.put(q.id(), state);
                resolvedValues.put(q.id(), value);
            }
            validateRelations(in, resolvedStates, resolvedValues, issues);
            validateDependencies(in, own, resolvedStates, resolvedValues, issues);
            for (Question schemaQuestion : schemas.getOrDefault(in.slug(), Map.of()).values()) {
                if (!Set.of("ANALITICA", "TEXTO_LIBRE").contains(privacy(in.slug(), schemaQuestion))) continue;
                Question q = own.get(schemaQuestion.id());
                if (q != null && !Set.of("ANALITICA", "TEXTO_LIBRE").contains(privacy(in.slug(), q))) continue;
                Boolean present = null;
                Boolean applicable = null;
                if (in.definition() != null) present = q != null;
                if (Boolean.FALSE.equals(present)) applicable = false;
                if (q != null) {
                    if (q.dependsOn() == null) applicable = true;
                    else if (CAPTURE_STATES.contains(resolvedStates.getOrDefault(q.dependsOn(), "")))
                        applicable = q.applicable(p.answers());
                }
                String recorded = q == null ? null : beforeQuality.get(q.id());
                Boolean conValueBeforeQuality = null;
                if (Boolean.FALSE.equals(present)) conValueBeforeQuality = false;
                else if (recorded != null && CAPTURE_STATES.contains(recorded))
                    conValueBeforeQuality = "CON_VALOR".equals(recorded);
                coverage.add(map("response_id", in.responseId(), "survey_slug", in.slug(), "instrument_version", in.version(),
                        "question_id", schemaQuestion.id(), "present", present,
                        "applicable", applicable, "con_value_before_quality", conValueBeforeQuality,
                        "final_state", q == null ? (in.definition() == null ? "SIN_ESTADO_HISTORICO" : "NO_RECOLECTADA") : resolvedStates.get(q.id())));
            }
            for (Map<String, Object> review : p.textReviews()) {
                var frozenReview = new LinkedHashMap<>(review);
                frozenReview.put("valid", Boolean.TRUE.equals(review.get("valid")) && "CON_VALOR".equals(resolvedStates.get(review.get("question_id"))));
                textReviews.add(frozenReview);
            }
            for (Question schemaQuestion : schemas.getOrDefault(in.slug(), Map.of()).values()) {
                if (!privacy(in.slug(), schemaQuestion).equals("ANALITICA")) continue;
                Question q = own.get(schemaQuestion.id());
                if (q != null && !privacy(in.slug(), q).equals("ANALITICA")) continue;
                String state = q == null ? (in.definition() == null ? "SIN_ESTADO_HISTORICO" : "NO_RECOLECTADA")
                        : resolvedStates.get(q.id());
                Object value = q == null ? null : resolvedValues.get(q.id());
                row.put(schemaQuestion.id(), value);
                row.put(schemaQuestion.id() + "__estado", state);
                qualityCounts.merge(in.slug() + "\t" + schemaQuestion.id() + "\t" + state, 1, Integer::sum);
                if (schemaQuestion.type().equals("checkbox")) {
                    @SuppressWarnings("unchecked") List<String> selected = value instanceof List<?> ? (List<String>) value : List.of();
                    Set<String> ownOptions = q == null || !q.type().equals("checkbox") ? Set.of()
                            : new HashSet<>(q.options().stream().map(Question.Option::value).toList());
                    for (String option : optionSchemas.getOrDefault(in.slug() + "/" + schemaQuestion.id(), Set.of())) {
                        Integer indicator = "CON_VALOR".equals(state) && ownOptions.contains(option)
                                ? (selected.contains(option) ? 1 : 0) : null;
                        row.put(schemaQuestion.id() + "__" + option, indicator);
                    }
                    if ("CON_VALOR".equals(state) && q != null && q.type().equals("checkbox")) {
                        for (String option : selected) selections.add(map("response_id", in.responseId(), "survey_slug", in.slug(),
                                "instrument_version", in.version(), "question_id", schemaQuestion.id(), "option_code", option));
                    }
                }
            }
            p.coding().stream().filter(code -> "CON_VALOR".equals(resolvedStates.get(code.get("question_id"))))
                    .forEach(coding::add);
            if (in.slug().equals("titulados")) graduates.add(row); else employers.add(row);
        }
        var quality = new ArrayList<Map<String, Object>>();
        qualityCounts.forEach((key, count) -> {
            String[] parts = key.split("\t");
            quality.add(map("survey_slug", parts[0], "question_id", parts[1], "state", parts[2], "count", count));
        });
        return new Product(dictionary, graduates, employers, selections, coding, issues, quality, coverage, textReviews);
    }

    static String validate(Question q, List<String> values) {
        if (values == null || values.isEmpty()) return "La respuesta no contiene un valor.";
        if (!q.type().equals("checkbox") && values.size() != 1) return "La pregunta exige un único valor.";
        if (values.size() < q.minSelections() || values.size() > q.maxSelections()) return "Cantidad de selecciones fuera del contrato.";
        if (new HashSet<>(values).size() != values.size()) return "Hay selecciones repetidas.";
        if (values.stream().anyMatch(v -> v == null || v.isBlank() || v.indexOf('\0') >= 0
                || v.length() > (q.type().equals("textarea") ? 2000 : 1000))) return "Longitud o formato de valor inválido.";
        if (!q.options().isEmpty() && !q.options().stream().map(Question.Option::value).toList().containsAll(values)) {
            return "Un código no pertenece al catálogo de esta versión.";
        }
        String value = values.getFirst();
        if (q.type().equals("number")) {
            if (!value.matches("[0-9]{1,9}")) return "Se requiere un entero sin separadores.";
            int number = Integer.parseInt(value);
            if ((q.min() != null && number < q.min()) || (q.max() != null && number > q.max())) return "Número fuera del rango de esta versión.";
        }
        if (q.type().equals("email") && !value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) return "Formato de correo inválido.";
        if (q.type().equals("tel") && (!value.matches("^[+()0-9 .-]{5,30}$")
                || value.replaceAll("[^0-9]", "").length() < 5)) return "Formato de teléfono inválido.";
        if (q.type().equals("url")) {
            try {
                URI uri = URI.create(value);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null || uri.getUserInfo() != null) return "Formato de URL inválido.";
            } catch (IllegalArgumentException ex) { return "Formato de URL inválido."; }
        }
        return null;
    }

    private static Object converted(Question q, List<String> values) {
        if (q.type().equals("number")) return Integer.valueOf(values.getFirst());
        if (q.type().equals("checkbox")) return values.stream().sorted().toList();
        return values.getFirst();
    }

    private void validateRelations(Input in, Map<String, String> states, Map<String, Object> values, List<Map<String, Object>> issues) {
        if (!in.slug().equals("titulados")) return;
        Object life = values.get("meses_vida_profesional");
        Object unemployment = values.get("meses_desempleado");
        if (unemployment instanceof Integer unemployed && (!(life instanceof Integer) || unemployed > (Integer) life)) {
            invalidate(in, "meses_desempleado", states, values, issues, "DESEMPLEO_SUPERA_VIDA_PROFESIONAL",
                    "El desempleo requiere vida profesional en meses compatibles y no puede superarla.");
            if (life instanceof Integer) invalidate(in, "meses_vida_profesional", states, values, issues,
                    "DESEMPLEO_SUPERA_VIDA_PROFESIONAL", "La relación temporal es inconsistente; no se adivina cuál dato debe corregirse.");
        }
        if ("SIN_EXPERIENCIA".equals(values.get("experiencia_laboral"))
                && Set.of("EMPLEADO", "EMPRENDIMIENTO").contains(String.valueOf(values.get("estado_laboral")))) {
            invalidate(in, "experiencia_laboral", states, values, issues, "EXPERIENCIA_SITUACION_INCOHERENTE",
                    "Sin experiencia no es compatible con trabajo o emprendimiento actual.");
            invalidate(in, "estado_laboral", states, values, issues, "EXPERIENCIA_SITUACION_INCOHERENTE",
                    "La situación actual y la experiencia requieren revisión conjunta.");
        }
        if (values.get("estado_laboral") != null && values.get("sector") != null
                && "DESEMPLEADO".equals(values.get("estado_laboral")) != "NO_APLICA".equals(values.get("sector"))) {
            invalidate(in, "sector", states, values, issues, "SECTOR_SITUACION_INCOHERENTE", "El sector no corresponde a la situación actual.");
        }
    }

    private void invalidate(Input in, String qid, Map<String, String> states, Map<String, Object> values,
                            List<Map<String, Object>> issues, String code, String message) {
        states.put(qid, "VALOR_INVALIDO");
        values.put(qid, null);
        issues.add(issue(in, qid, code, "ERROR_RELACION", message));
    }

    private void validateDependencies(Input in, Map<String, Question> questions, Map<String, String> states,
                                      Map<String, Object> values, List<Map<String, Object>> issues) {
        Set<String> reported = new HashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Question q : questions.values()) {
                if (q.dependsOn() == null) continue;
                String parentState = states.get(q.dependsOn());
                if (!"VALOR_INVALIDO".equals(parentState) && !"SIN_ESTADO_HISTORICO".equals(parentState)) continue;
                if (!"VALOR_INVALIDO".equals(states.get(q.id()))) changed = true;
                states.put(q.id(), "VALOR_INVALIDO");
                values.put(q.id(), null);
                if (reported.add(q.id())) issues.add(issue(in, q.id(), "APLICABILIDAD_NO_VERIFICABLE", "PENDIENTE_REGLA",
                        "La respuesta de control no permite verificar la aplicabilidad de esta pregunta; se conserva el estado original en la captura."));
            }
        } while (changed);
    }

    static String privacy(String slug, Question q) {
        if (Set.of("email", "tel", "url").contains(q.type())) return "CONTACTO";
        if (q.id().equals("nombre_organizacion")) return "IDENTIFICACION";
        if (Set.of("text", "textarea").contains(q.type())) return "TEXTO_LIBRE";
        boolean reviewed = slug.equals("titulados")
                ? T_IDS.contains(q.id()) || q.id().equals("anios_vida_profesional") || q.id().equals("experiencia_laboral")
                    || q.id().matches("competencia_(tecnica|transversal)_0[1-6]")
                : E_IDS.contains(q.id()) || q.id().equals("otro_cargo") || q.id().matches("criterio_(0[1-9]|10)");
        if (!reviewed) return "PRIVACIDAD_NO_REVISADA";
        return "ANALITICA";
    }

    private Map<String, Object> dictionary(Input in, Question q) {
        String privacy = privacy(in.slug(), q);
        return map("survey_slug", in.slug(), "instrument_version", in.version(), "variable", q.id(),
                "source_id", source(in.slug(), q.id()), "source", "Instrumento de captura versionado; correspondencia de fase 2",
                "label", q.label(), "type", kind(in.slug(), q),
                "unit", unit(q.id()), "privacy", privacy, "included_in_matrix", privacy.equals("ANALITICA"),
                "required", q.required(), "depends_on", q.dependsOn(), "depends_value", q.dependsValue(),
                "min", q.min(), "max", q.max(), "min_selections", q.minSelections(), "max_selections", q.maxSelections(),
                "catalog", q.options().stream().map(o -> map("code", o.value(), "label", o.label(),
                        "nominal_without_score", Set.of("NO_SABE", "NO_PUEDE_VALORAR", "PREFIERO_NO_RESPONDER").contains(o.value()))).toList(),
                "transformation", "Validación con snapshot y conversión de entero cuando corresponda; sin imputación ni puntajes.");
    }

    private String kind(String slug, Question q) {
        if (q.type().equals("number")) return "ENTERO";
        if (q.type().equals("checkbox")) return "MULTIPLE";
        if (!privacy(slug, q).equals("ANALITICA")) return "TEXTO_RESTRINGIDO";
        if (q.id().startsWith("criterio_") || q.id().startsWith("competencia_")) return "ORDINAL_CON_CATEGORIA_NOMINAL";
        if (Set.of("nivel_cursado", "opinion_posgrado_fcyt", "satisfaccion_emprendimiento", "importancia_formacion_negocio",
                "cantidad_empleos", "satisfaccion_formacion", "concordancia_formacion", "tamano_organizacion",
                "nivel_formacion_demandado").contains(q.id())) return "ORDINAL";
        return "NOMINAL";
    }

    static String source(String slug, String id) {
        if (slug.equals("titulados")) {
            if (id.startsWith("competencia_tecnica_")) return "T39." + id.substring(id.length() - 2);
            if (id.startsWith("competencia_transversal_")) return "T40." + id.substring(id.length() - 2);
            if (id.equals("experiencia_laboral")) return "T41";
            if (id.equals("anios_vida_profesional")) return "T12";
            int index = T_IDS.indexOf(id);
            return index < 0 ? "SIN_CORRESPONDENCIA" : "T%02d".formatted(index + 1);
        }
        if (id.equals("otro_cargo")) return "E09_OTRO";
        if (id.startsWith("criterio_")) return "E%02d".formatted(12 + Integer.parseInt(id.substring(id.length() - 2)));
        int index = E_IDS.indexOf(id);
        return index < 0 ? "SIN_CORRESPONDENCIA" : "E%02d".formatted(index);
    }

    private String unit(String id) {
        if (id.equals("edad")) return "AÑOS_COMPLETOS";
        if (id.equals("meses_vida_profesional") || id.equals("meses_desempleado")) return "MESES_COMPLETOS";
        if (id.equals("anio_egreso")) return "AÑO_CALENDARIO";
        if (id.equals("anios_vida_profesional")) return "AÑOS_ORIGINALES_SIN_CONVERSION";
        return null;
    }

    static Map<String, Object> issue(Input input, String qid, String code, String severity, String message) {
        return map("response_id", input.responseId(), "question_id", qid, "rule_code", code,
                "severity", severity, "message", message);
    }

    static Map<String, Object> map(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
}

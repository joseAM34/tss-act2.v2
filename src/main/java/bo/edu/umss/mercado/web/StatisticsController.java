package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.AnalysisPlan;
import bo.edu.umss.mercado.statistics.AnalysisService;
import bo.edu.umss.mercado.wrangling.WranglingService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Administración y exportaciones sujetas a ADMIN y CSRF en SecurityConfig. */
@Controller
public class StatisticsController {
    private final AnalysisService analyses;
    private final WranglingService wrangling;
    private static final Map<String, String> PRODUCTS = Map.of(
            "frecuencias", "frequencies", "numericos", "numeric", "ordinales", "ordinal", "multiples", "multiple",
            "textos", "texts", "indicadores", "indicators", "cobertura", "coverage", "cruces", "contrasts", "familias", "families");
    public StatisticsController(AnalysisService analyses, WranglingService wrangling) {
        this.analyses = analyses; this.wrangling = wrangling;
    }
    @GetMapping("/admin/estadisticas")
    String index(Model model) { model.addAttribute("analyses", analyses.recent()); return "statistics-list"; }
    @GetMapping("/admin/datos/{id}/analisis")
    String form(@PathVariable String id, Model model) {
        model.addAttribute("run", wrangling.detail(id));
        model.addAttribute("analyses", analyses.recent().stream().filter(a -> a.sourceRunId().equals(id)).toList());
        model.addAttribute("contrasts", AnalysisPlan.CONTRASTS);
        return "analysis-form";
    }
    @PostMapping("/admin/datos/{id}/analisis")
    String create(@PathVariable String id, @RequestParam(defaultValue = "") String independenceUnit,
                  @RequestParam(defaultValue = "") String independenceEvidence,
                  @RequestParam(defaultValue = "false") boolean structuralZeros,
                  @RequestParam(defaultValue = "0.05") double alpha,
                  @RequestParam(required = false) List<String> contrasts, Principal actor,
                  Model model, HttpServletResponse response) {
        try {
            AnalysisPlan plan = new AnalysisPlan(contrasts == null ? AnalysisPlan.CONTRASTS : contrasts,
                    independenceUnit, independenceEvidence, structuralZeros, alpha);
            return "redirect:/admin/estadisticas/" + analyses.create(id, plan, actor.getName());
        } catch (IllegalArgumentException ex) {
            response.setStatus(400); model.addAttribute("error", ex.getMessage());
            model.addAttribute("independenceUnit", independenceUnit); model.addAttribute("independenceEvidence", independenceEvidence);
            model.addAttribute("alpha", alpha); return form(id, model);
        }
    }
    @GetMapping("/admin/estadisticas/{id}")
    String detail(@PathVariable String id, Model model) {
        model.addAttribute("analysis", analyses.detail(id)); model.addAttribute("formatter", this); return "statistics";
    }
    @GetMapping("/admin/estadisticas/{id}/analisis.json")
    void json(@PathVariable String id, HttpServletResponse response) throws IOException {
        String json = analyses.bundle(id);
        download(response, "application/json;charset=UTF-8", "analisis-" + safeId(id) + ".json"); response.getWriter().write(json);
    }
    @SuppressWarnings("unchecked")
    @GetMapping("/admin/estadisticas/{id}/{product}.csv")
    void csv(@PathVariable String id, @PathVariable String product, HttpServletResponse response) throws IOException {
        String key = PRODUCTS.get(product);
        if (key == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto desconocido.");
        Map<String, Object> result = (Map<String, Object>) analyses.detail(id).get("result");
        var rows = new ArrayList<Map<String, Object>>();
        if (key.equals("contrasts") || key.equals("families")) rows.addAll((List<Map<String, Object>>) result.getOrDefault(key, List.of()));
        else for (Map<String, Object> stratum : (List<Map<String, Object>>) result.getOrDefault("strata", List.of())) {
            for (Map<String, Object> value : (List<Map<String, Object>>) stratum.getOrDefault(key, List.of())) {
                var row = new LinkedHashMap<String, Object>();
                row.put("survey_slug", stratum.get("survey_slug")); row.put("instrument_version", stratum.get("instrument_version"));
                row.put("study_id", stratum.get("study_id")); row.putAll(value); rows.add(row);
            }
        }
        var headers = new TreeSet<String>(); rows.forEach(row -> headers.addAll(row.keySet()));
        var content = new StringBuilder("\ufeff");
        if (!headers.isEmpty()) {
            content.append(headers.stream().map(StatisticsController::cell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
            for (var row : rows) content.append(headers.stream().map(h -> cell(row.get(h))).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        }
        download(response, "text/csv;charset=UTF-8", product + "-" + safeId(id) + ".csv"); response.getWriter().write(content.toString());
    }
    public String number(Object value) {
        if (!(value instanceof Number n)) return value == null ? "—" : value.toString();
        return String.format(Locale.forLanguageTag("es-BO"), "%.2f", n.doubleValue());
    }
    public String pvalue(Object value) {
        if (!(value instanceof Number n)) return "—";
        return n.doubleValue() < 0.001 ? "< 0,001" : String.format(Locale.forLanguageTag("es-BO"), "%.4f", n.doubleValue());
    }
    public String display(Object value) {
        if (value == null) return "—";
        if (value instanceof Map<?, ?> || value instanceof List<?>) return CanonicalJson.json(value);
        return value instanceof Double || value instanceof Float ? number(value) : value.toString();
    }
    public String label(Object value) {
        if (value == null) return "";
        return switch (value.toString()) {
            case "CALCULADO" -> "Calculado";
            case "SIN_DATOS", "SIN_PARES_VALIDOS" -> "Sin datos válidos";
            case "SIN_VARIACION", "MENOS_DE_DOS_CATEGORIAS_ACTIVAS" -> "Sin variación suficiente";
            case "INDEPENDENCIA_NO_DOCUMENTADA", "FALTA_UNIDAD_Y_EVIDENCIA_DE_INDEPENDENCIA" -> "Independencia sin documentar";
            case "FRECUENCIAS_INSUFICIENTES", "ALGUNA_ESPERADA_MENOR_5" -> "Frecuencias esperadas insuficientes";
            case "PENDIENTE_DEFINICION" -> "Definición metodológica pendiente";
            case "ESTRUCTURA_INCOMPATIBLE", "CEROS_ESTRUCTURALES_DECLARADOS" -> "Combinaciones estructuralmente imposibles";
            case "OCUPACION_DECLARADA" -> "Declara empleo o emprendimiento";
            case "NO_TRABAJA_DECLARADO" -> "Declara desempleo";
            case "PROPORCION_SI" -> "Respuestas afirmativas";
            case "ACUERDO_SOBRE_SUSTANTIVOS" -> "Acuerdo entre respuestas con valoración";
            case "ACUERDO_SOBRE_VALIDOS" -> "Acuerdo entre todas las respuestas válidas";
            default -> value.toString().replace('_', ' ');
        };
    }
    private static String cell(Object value) {
        String text = value == null ? "" : value instanceof Map<?, ?> || value instanceof List<?> ? CanonicalJson.json(value) : value.toString();
        if (!text.isEmpty() && "=+-@\t\r\n".indexOf(text.charAt(0)) >= 0) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
    private static String safeId(String id) {
        if (!id.matches("[a-fA-F0-9-]{36}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return id;
    }
    private static void download(HttpServletResponse response, String type, String filename) {
        response.setContentType(type); response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setHeader("Cache-Control", "no-store");
    }
}

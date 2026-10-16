package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.wrangling.WranglingService;
import bo.edu.umss.mercado.wrangling.RunDetail;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Todas estas rutas están protegidas por ADMIN en SecurityConfig; POST conserva CSRF. */
@Controller
public class DataController {
    private final WranglingService wrangling;
    private final CaptureStore captures;

    public DataController(WranglingService wrangling, CaptureStore captures) {
        this.wrangling = wrangling;
        this.captures = captures;
    }

    @GetMapping("/admin/datos")
    String datasets(Model model) {
        model.addAttribute("runs", wrangling.recent());
        return "datasets";
    }

    @PostMapping("/admin/datos")
    String create(@RequestParam String origin, @RequestParam(defaultValue = "BORRADOR") String mode,
                  @RequestParam(defaultValue = "") String cutoff, Principal actor,
                  Model model, HttpServletResponse response) {
        try {
            Instant until;
            if (cutoff.isBlank()) until = Instant.now();
            else {
                try { until = Instant.parse(cutoff); }
                catch (DateTimeParseException ex) {
                    until = LocalDateTime.parse(cutoff).atZone(ZoneId.of("America/La_Paz")).toInstant();
                }
            }
            String id = wrangling.create(origin, mode, until, actor.getName());
            return "redirect:/admin/datos/" + id;
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            response.setStatus(400);
            model.addAttribute("error", ex.getMessage());
            return datasets(model);
        }
    }

    @GetMapping("/admin/datos/{id}")
    String dataset(@PathVariable String id, Model model) {
        model.addAttribute("run", wrangling.detail(id));
        return "dataset";
    }

    @GetMapping("/admin/datos/{id}/conjunto.json")
    void json(@PathVariable String id, HttpServletResponse response) throws IOException {
        String content = wrangling.bundle(id);
        download(response, "application/json;charset=UTF-8", "conjunto-" + safeId(id) + ".json");
        response.getWriter().write(content);
    }

    @GetMapping("/admin/datos/{id}/{product}.csv")
    void csv(@PathVariable String id, @PathVariable String product, HttpServletResponse response) throws IOException {
        var run = wrangling.detail(id);
        var rows = products(run).get(product);
        if (rows == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto desconocido");
        download(response, "text/csv;charset=UTF-8", product + "-" + safeId(id) + ".csv");
        writeCsv(response.getWriter(), rows);
    }

    @GetMapping("/admin/datos/{id}/conjunto.zip")
    void zip(@PathVariable String id, HttpServletResponse response) throws IOException {
        var run = wrangling.detail(id);
        // Preparar todos los productos antes de escribir la respuesta HTTP.
        var files = new LinkedHashMap<String, byte[]>();
        for (var product : products(run).entrySet()) {
            var writer = new StringWriter();
            writeCsv(writer, product.getValue());
            files.put(product.getKey() + ".csv", writer.toString().getBytes(StandardCharsets.UTF_8));
        }
        files.put("manifiesto.json", CanonicalJson.json(run.manifest()).getBytes(StandardCharsets.UTF_8));
        files.put("conjunto.json", wrangling.bundle(id).getBytes(StandardCharsets.UTF_8));
        download(response, "application/zip", "conjunto-" + safeId(id) + ".zip");
        try (var zip = new ZipOutputStream(response.getOutputStream(), StandardCharsets.UTF_8)) {
            for (var file : files.entrySet()) {
                var entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
    }

    @GetMapping("/admin/respuestas/{id}/revision")
    String review(@PathVariable String id, Model model) {
        var input = captures.input(id);
        model.addAttribute("input", input);
        model.addAttribute("analyticSupported", List.of("titulados", "empleadores").contains(input.slug()));
        model.addAttribute("origin", wrangling.origin(id));
        model.addAttribute("history", wrangling.reviewHistory(id));
        return "data-review";
    }

    @PostMapping("/admin/respuestas/{id}/origen")
    String classify(@PathVariable String id, @RequestParam String origin, @RequestParam String reason,
                    Principal actor, Model model, HttpServletResponse response) {
        return revision(id, model, response, () -> wrangling.classify(id, origin, reason, actor.getName()));
    }

    @PostMapping("/admin/respuestas/{id}/correcciones")
    String correct(@PathVariable String id, @RequestParam String questionId,
                   @RequestParam(defaultValue = "") String values, @RequestParam String reason,
                   Principal actor, Model model, HttpServletResponse response) {
        return revision(id, model, response,
                () -> wrangling.correct(id, questionId, correctionValues(id, questionId, values), reason, actor.getName()));
    }

    @PostMapping("/admin/respuestas/{id}/codificacion")
    String code(@PathVariable String id, @RequestParam String questionId,
                @RequestParam(defaultValue = "") String themes, @RequestParam String codebookVersion,
                @RequestParam String reason, Principal actor, Model model, HttpServletResponse response) {
        return revision(id, model, response,
                () -> wrangling.codeText(id, questionId, lines(themes), codebookVersion, reason, actor.getName()));
    }

    private String revision(String id, Model model, HttpServletResponse response, Runnable action) {
        try {
            action.run();
            return "redirect:/admin/respuestas/" + id + "/revision";
        } catch (IllegalArgumentException ex) {
            response.setStatus(400);
            model.addAttribute("error", ex.getMessage());
            return review(id, model);
        }
    }

    private List<String> lines(String text) {
        return Arrays.stream(text.split("\\R", -1)).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    private List<String> correctionValues(String responseId, String questionId, String value) {
        var input = captures.input(responseId);
        if (input.definition() == null) throw new IllegalArgumentException("La corrección requiere una definición verificable.");
        var question = input.definition().questions().stream().filter(q -> q.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("La pregunta no pertenece a esta versión."));
        if (question.type().equals("checkbox")) return lines(value);
        return value.isBlank() ? List.of() : List.of(value);
    }

    private String safeId(String id) {
        if (!id.matches("[a-fA-F0-9-]{36}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return id;
    }

    private void download(HttpServletResponse response, String type, String name) {
        response.setContentType(type);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
        response.setHeader("Cache-Control", "no-store");
    }

    private Map<String, List<Map<String, Object>>> products(RunDetail run) {
        var result = new LinkedHashMap<String, List<Map<String, Object>>>();
        result.put("titulados", run.graduates());
        result.put("empleadores", run.employers());
        result.put("selecciones", run.selections());
        result.put("codificacion", run.coding());
        result.put("incidencias", run.issues());
        result.put("calidad", run.quality());
        result.put("diccionario", run.dictionary());
        result.put("miembros", run.members());
        return result;
    }

    private void writeCsv(Writer writer, List<Map<String, Object>> rows) throws IOException {
        writer.write('\uFEFF');
        var columns = new TreeSet<String>();
        rows.forEach(row -> columns.addAll(row.keySet()));
        var ordered = List.copyOf(columns);
        csvRow(writer, ordered);
        for (var row : rows) {
            csvRow(writer, ordered.stream().map(column -> {
                Object value = row.get(column);
                if (value == null) return "";
                if (value instanceof Map<?, ?> || value instanceof List<?>) return CanonicalJson.json(value);
                return value.toString();
            }).toList());
        }
    }

    private void csvRow(Writer writer, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) writer.write(',');
            String value = cells.get(i);
            String first = value.stripLeading();
            if ((!first.isEmpty() && "=+-@".indexOf(first.charAt(0)) >= 0)
                    || (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0)) value = "'" + value;
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }
}

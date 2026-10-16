package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Selector, configuración privada y salidas de conjuntos generales; todas las rutas requieren ADMIN. */
@Controller
@RequestMapping("/admin/preparacion")
public class GenericPreparationController {
    private final GenericPreparationService preparation;
    private final SurveyPublicationService publications;
    public GenericPreparationController(GenericPreparationService preparation, SurveyPublicationService publications) {
        this.preparation = preparation; this.publications = publications;
    }
    @GetMapping
    String index(@RequestParam(defaultValue = "") String versionId, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("versions", publications.recent()); model.addAttribute("runs", preparation.recent());
        model.addAttribute("selectedVersion", versionId);
        if (!versionId.isEmpty()) {
            var version = publications.get(versionId); var config = preparation.configuration(versionId);
            model.addAttribute("versions",List.of(new SurveyPublicationService.Summary(version.id(),version.title(),version.code(),version.status())));
            model.addAttribute("runs",preparation.recentForVersion(versionId));
            model.addAttribute("version", version); model.addAttribute("configuration", config);
            var fields = new LinkedHashMap<String, GenericPreparationService.Field>();
            config.content().fields().forEach(f -> fields.put(f.questionId(), f));
            model.addAttribute("fields", fields);
        }
        return "generic-preparation";
    }
    @PostMapping("/{versionId}/configuracion")
    String configure(@PathVariable String versionId, @RequestParam MultiValueMap<String, String> values, Principal principal,
                     Model model, HttpServletResponse response) {
        try {
            var version = publications.get(versionId);
            var fields = new ArrayList<GenericPreparationService.Field>();
            for (var q : version.content().definition().questions()) fields.add(new GenericPreparationService.Field(q.id(), one(values, "treatment." + q.id()), one(values, "unit." + q.id()).strip()));
            preparation.configure(versionId, Integer.parseInt(one(values, "revision")), fields, principal.getName());
            return "redirect:/admin/preparacion?versionId=" + versionId;
        } catch (IllegalArgumentException ex) {
            response.setStatus(400); model.addAttribute("error", ex.getMessage()); return index(versionId, model, response);
        }
    }
    @PostMapping("/{versionId}/incluir-todos")
    String selectCompatible(@PathVariable String versionId,@RequestParam int revision,Principal principal) {
        preparation.selectCompatible(versionId,revision,principal.getName());
        return "redirect:/admin/preparacion?versionId="+versionId+"#preparar-conjunto";
    }
    @PostMapping("/{versionId}/conjuntos")
    String create(@PathVariable String versionId, @RequestParam String configId, @RequestParam String origin,
                  @RequestParam(defaultValue = "BORRADOR") String mode, @RequestParam(defaultValue = "") String from,
                  @RequestParam(defaultValue = "") String until, Principal principal, Model model, HttpServletResponse response) {
        try {
            String id = preparation.prepare(versionId, configId, origin, mode, from.isBlank() ? null : LocalDate.parse(from), until.isBlank() ? null : LocalDate.parse(until), principal.getName());
            return "redirect:/admin/preparacion/conjuntos/" + id;
        } catch (IllegalArgumentException | DateTimeParseException ex) {
            response.setStatus(400); model.addAttribute("error", ex.getMessage()); return index(versionId, model, response);
        }
    }
    @GetMapping("/conjuntos/{id}")
    String detail(@PathVariable String id, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); var bundle = preparation.bundle(id);
        model.addAttribute("bundle", bundle); model.addAttribute("logical", bundle.get("logical")); return "generic-dataset";
    }
    @GetMapping("/conjuntos/{id}/conjunto.json")
    void json(@PathVariable String id, HttpServletResponse response) throws IOException {
        var bundle = preparation.bundle(id); download(response, "application/json;charset=UTF-8", "conjunto-" + id + ".json");
        response.getWriter().write(CanonicalJson.json(bundle));
    }
    @SuppressWarnings("unchecked")
    @GetMapping("/conjuntos/{id}/matriz.csv")
    void csv(@PathVariable String id, HttpServletResponse response) throws IOException {
        var bundle = preparation.bundle(id); Map<String, Object> logical = (Map<String, Object>) bundle.get("logical");
        download(response, "text/csv;charset=UTF-8", "matriz-" + id + ".csv"); var writer = response.getWriter(); writer.write('\uFEFF');
        row(writer, List.of("response_id", "submitted_at", "survey_slug", "instrument_version", "origin", "question_id", "answer_state", "value_json"));
        for (var entry : (List<Map<String, Object>>) logical.get("matrix")) {
            var values = (Map<String, Object>) entry.get("values"); var states = (Map<String, String>) entry.get("states");
            for (String q : values.keySet()) row(writer, List.of(entry.get("response_id").toString(), entry.get("submitted_at").toString(), logical.get("survey_slug").toString(),
                    logical.get("instrument_version").toString(), entry.get("origin").toString(), q, states.get(q), CanonicalJson.json(values.get(q))));
        }
    }
    private void row(java.io.Writer writer, List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) writer.write(','); String value = values.get(i); String first = value.stripLeading();
            if (!first.isEmpty() && "=+-@".indexOf(first.charAt(0)) >= 0 || !value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
            writer.write('"'); writer.write(value.replace("\"", "\"\"")); writer.write('"');
        }
        writer.write("\r\n");
    }
    private String one(MultiValueMap<String, String> values, String name) {
        var entries = values.get(name); if (entries == null || entries.size() != 1) throw new IllegalArgumentException("Falta un valor único para " + name + "."); return entries.getFirst();
    }
    private void download(HttpServletResponse response, String type, String filename) {
        response.setContentType(type); response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename.replaceAll("[^A-Za-z0-9_.-]", "_") + "\"");
    }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException ex, Model model, HttpServletResponse response) {
        response.setStatus(ex.getStatusCode().value()); response.setHeader("Cache-Control", "no-store");
        model.addAttribute("message", ex.getReason()); model.addAttribute("back", "/admin/preparacion"); return "survey-editor-error";
    }
}

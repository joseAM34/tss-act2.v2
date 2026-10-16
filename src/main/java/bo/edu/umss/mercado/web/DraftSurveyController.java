package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.survey.DraftSurveyService;
import bo.edu.umss.mercado.survey.DraftSurveyService.Draft;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/encuestas")
public class DraftSurveyController {
    private final DraftSurveyService drafts;
    private final SurveyCatalog catalog;
    private final SurveyPublicationService publications;
    public DraftSurveyController(DraftSurveyService drafts, SurveyCatalog catalog, SurveyPublicationService publications) {
        this.drafts = drafts; this.catalog = catalog; this.publications = publications;
    }

    @GetMapping
    String list(Model model) {
        model.addAttribute("drafts", drafts.recent());
        model.addAttribute("legacySurveys", catalog.all());
        model.addAttribute("publications", publications.recent());
        return "survey-drafts";
    }

    @PostMapping
    String create(@RequestParam String title, @RequestParam(defaultValue = "") String description,
                  Principal actor, Model model, HttpServletResponse response) {
        try { return redirect(drafts.create(title, description, actor.getName())); }
        catch (IllegalArgumentException ex) {
            response.setStatus(422); model.addAttribute("error", ex.getMessage());
            model.addAttribute("createTitle", title); model.addAttribute("createDescription", description);
            return list(model);
        }
    }

    @PostMapping("/copiar/{slug}")
    String copy(@PathVariable String slug, Principal actor) { return redirect(drafts.copyLegacy(slug, actor.getName())); }

    @GetMapping("/{id}")
    String edit(@PathVariable String id, Model model) {
        if (!drafts.editable(id)) return "redirect:/admin/encuestas/versiones/" + id;
        model.addAttribute("draft", drafts.get(id));
        return "survey-draft";
    }

    @PostMapping("/{id}/duplicar")
    String duplicate(@PathVariable String id, Principal actor) { return redirect(drafts.duplicate(id, actor.getName())); }

    @PostMapping("/{id}/titulo")
    String header(@PathVariable String id, @RequestParam long revision, @RequestParam String title,
                  @RequestParam(defaultValue = "") String description, Principal actor, Model model,
                  HttpServletResponse response) {
        try { drafts.updateHeader(id, revision, title, description, actor.getName()); return redirect(id); }
        catch (IllegalArgumentException ex) {
            response.setStatus(422); model.addAttribute("error", ex.getMessage());
            model.addAttribute("editedTitle", title); model.addAttribute("editedDescription", description);
            return edit(id, model);
        }
    }

    @PostMapping("/{id}/secciones")
    String section(@PathVariable String id, @RequestParam long revision, @RequestParam(defaultValue = "") String sectionId,
                   @RequestParam String title, Principal actor) {
        drafts.section(id, revision, sectionId.isBlank() ? null : sectionId, title, actor.getName());
        return redirect(id);
    }

    @PostMapping("/{id}/secciones/{sectionId}/eliminar")
    String removeSection(@PathVariable String id, @PathVariable String sectionId, @RequestParam long revision, Principal actor) {
        drafts.removeSection(id, revision, sectionId, actor.getName()); return redirect(id);
    }

    @PostMapping("/{id}/secciones/{sectionId}/mover")
    String moveSection(@PathVariable String id, @PathVariable String sectionId, @RequestParam long revision,
                       @RequestParam int direction, Principal actor) {
        drafts.moveSection(id, revision, sectionId, direction, actor.getName()); return redirect(id);
    }

    @GetMapping("/{id}/preguntas/nueva")
    String newQuestion(@PathVariable String id, Model model) {
        Draft d = drafts.get(id);
        var fields = new LinkedHashMap<String, String>();
        fields.put("questionId", "p_" + UUID.randomUUID().toString().replace("-", ""));
        fields.put("sectionId", d.sections().isEmpty() ? "" : d.sections().getFirst().id());
        fields.put("type", "text"); fields.put("minSelections", "0"); fields.put("maxSelections", "1");
        return questionForm(d, fields, true, model);
    }

    @GetMapping("/{id}/preguntas/{questionId}")
    String existingQuestion(@PathVariable String id, @PathVariable String questionId, Model model) {
        Draft d = drafts.get(id);
        var item = d.items().stream().filter(i -> i.question().id().equals(questionId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Pregunta no encontrada."));
        Question q = item.question();
        var fields = new LinkedHashMap<String, String>();
        fields.put("questionId", q.id()); fields.put("sectionId", item.sectionId());
        fields.put("label", q.label()); fields.put("type", q.type()); fields.put("required", String.valueOf(q.required()));
        fields.put("hint", q.hint()); fields.put("min", number(q.min())); fields.put("max", number(q.max()));
        fields.put("minSelections", String.valueOf(q.minSelections())); fields.put("maxSelections", String.valueOf(q.maxSelections()));
        fields.put("options", q.options().stream().map(o -> o.value() + " | " + o.label()).collect(java.util.stream.Collectors.joining("\n")));
        fields.put("dependsOn", q.dependsOn() == null ? "" : q.dependsOn());
        fields.put("dependsValue", q.dependsValue() == null ? "" : q.dependsValue());
        return questionForm(d, fields, false, model);
    }

    @PostMapping("/{id}/preguntas")
    String addQuestion(@PathVariable String id, @RequestParam Map<String, String> fields, Principal actor,
                       Model model, HttpServletResponse response) {
        return saveQuestion(id, fields.get("questionId"), fields, true, actor, model, response);
    }

    @PostMapping("/{id}/preguntas/{questionId}")
    String updateQuestion(@PathVariable String id, @PathVariable String questionId, @RequestParam Map<String, String> fields,
                          Principal actor, Model model, HttpServletResponse response) {
        return saveQuestion(id, questionId, fields, false, actor, model, response);
    }

    @PostMapping("/{id}/preguntas/{questionId}/eliminar")
    String removeQuestion(@PathVariable String id, @PathVariable String questionId, @RequestParam long revision, Principal actor) {
        drafts.removeQuestion(id, revision, questionId, actor.getName()); return redirect(id);
    }

    @PostMapping("/{id}/preguntas/{questionId}/mover")
    String moveQuestion(@PathVariable String id, @PathVariable String questionId, @RequestParam long revision,
                        @RequestParam int direction, Principal actor) {
        drafts.moveQuestion(id, revision, questionId, direction, actor.getName()); return redirect(id);
    }

    private String saveQuestion(String id, String questionId, Map<String, String> fields, boolean adding, Principal actor,
                                Model model, HttpServletResponse response) {
        try {
            Draft d = drafts.get(id);
            String sectionId = field(fields, "sectionId");
            String sectionTitle = d.sections().stream().filter(s -> s.id().equals(sectionId)).map(DraftSurveyService.Section::title)
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Selecciona una sección existente."));
            String optionsText = field(fields, "options");
            if (optionsText.length() > 100000) throw new IllegalArgumentException("El listado de opciones es demasiado largo.");
            var options = new ArrayList<Question.Option>();
            for (String line : optionsText.lines().filter(l -> !l.isBlank()).toList()) {
                String[] pair = line.split("\\|", 2);
                if (pair.length != 2) throw new IllegalArgumentException("Cada opción debe indicar código | texto.");
                options.add(new Question.Option(pair[0].strip(), pair[1].strip()));
            }
            Question q = new Question(questionId, sectionTitle, field(fields, "label"), field(fields, "type"),
                    "true".equals(field(fields, "required")), options, nullable(field(fields, "dependsOn")),
                    nullable(field(fields, "dependsValue")), integer(field(fields, "minSelections"), 0),
                    integer(field(fields, "maxSelections"), 1), field(fields, "hint"),
                    integer(field(fields, "min"), null), integer(field(fields, "max"), null));
            drafts.question(id, Long.parseLong(field(fields, "revision")), sectionId, q, adding, actor.getName());
            return redirect(id);
        } catch (IllegalArgumentException ex) {
            response.setStatus(422);
            model.addAttribute("error", ex instanceof NumberFormatException ? "Revisa los campos numéricos y la revisión del formulario." : ex.getMessage());
            var restored = new LinkedHashMap<>(fields); restored.put("questionId", questionId == null ? "" : questionId);
            return questionForm(drafts.get(id), restored, adding, model);
        }
    }

    private String questionForm(Draft d, Map<String, String> fields, boolean adding, Model model) {
        model.addAttribute("draft", d); model.addAttribute("fields", fields); model.addAttribute("adding", adding);
        Map<String, String> labels = Map.of("radio", "Selección única", "checkbox", "Selección múltiple", "select", "Desplegable",
                "text", "Texto corto", "textarea", "Texto largo", "number", "Número entero", "email", "Correo electrónico", "tel", "Teléfono", "url", "Enlace web");
        model.addAttribute("typeChoices", DraftSurveyService.TYPES.stream().map(type -> new Question.Option(type, labels.get(type))).toList());
        return "survey-question-editor";
    }

    @ExceptionHandler({IllegalArgumentException.class, ResponseStatusException.class})
    String problem(Exception ex, HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(ex instanceof ResponseStatusException status ? status.getStatusCode().value() : 422);
        model.addAttribute("message", ex instanceof ResponseStatusException status ? status.getReason() : ex.getMessage());
        String[] path = request.getRequestURI().split("/");
        model.addAttribute("back", path.length > 3 && path[3].matches("[a-f0-9-]{36}") ? "/admin/encuestas/" + path[3] : "/admin/encuestas");
        return "survey-editor-error";
    }

    private static String redirect(String id) { return "redirect:/admin/encuestas/" + id; }
    private static String field(Map<String, String> fields, String key) { return fields.getOrDefault(key, ""); }
    private static String nullable(String value) { return value.isBlank() ? null : value.strip(); }
    private static Integer integer(String value, Integer fallback) { return value.isBlank() ? fallback : Integer.valueOf(value); }
    private static String number(Integer value) { return value == null ? "" : value.toString(); }
}

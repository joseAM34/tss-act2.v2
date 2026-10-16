package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.survey.DraftSurveyService;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import bo.edu.umss.mercado.survey.SurveyValidator;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SurveyPublicationController {
    private final DraftSurveyService drafts;
    private final SurveyPublicationService publications;
    private final SurveyValidator validator;
    public SurveyPublicationController(DraftSurveyService drafts, SurveyPublicationService publications, SurveyValidator validator) {
        this.drafts = drafts; this.publications = publications; this.validator = validator;
    }

    @GetMapping("/admin/encuestas/{id}/vista-previa")
    String preview(@PathVariable String id, Model model) { return previewForm(drafts.get(id), Map.of(), Map.of(), model); }

    @PostMapping("/admin/encuestas/{id}/vista-previa")
    String trial(@PathVariable String id, @RequestParam long revision, @RequestParam MultiValueMap<String, String> submitted,
                 Model model, HttpServletResponse response) {
        var draft = drafts.get(id);
        if (draft.revision() != revision) throw new ResponseStatusException(HttpStatus.CONFLICT, "El borrador cambió. Abre de nuevo la vista previa.");
        var fields = new LinkedMultiValueMap<String, String>(submitted);
        fields.remove("revision");
        var validation = validator.validate(draft.definition(), fields);
        if (validation.valid()) model.addAttribute("previewSuccess", true); else response.setStatus(422);
        return previewForm(draft, fields, validation.errors(), model);
    }

    private String previewForm(DraftSurveyService.Draft draft, Map<String, ?> values, Map<String, String> errors, Model model) {
        model.addAttribute("survey", draft.definition()); model.addAttribute("values", values); model.addAttribute("errors", errors);
        model.addAttribute("formToken", "preview"); model.addAttribute("preview", true);
        model.addAttribute("previewId", draft.id()); model.addAttribute("previewRevision", draft.revision());
        return "survey";
    }

    @GetMapping("/admin/encuestas/{id}/publicar")
    String confirm(@PathVariable String id, Model model) { model.addAttribute("draft", drafts.get(id)); return "survey-publish-confirm"; }

    @PostMapping("/admin/encuestas/{id}/publicar")
    String publish(@PathVariable String id, @RequestParam long revision, Principal actor) {
        publications.publish(id, revision, actor.getName()); return versionRedirect(id);
    }

    @GetMapping("/admin/encuestas/versiones/{id}")
    String version(@PathVariable String id, Model model) { model.addAttribute("version", publications.get(id)); return "survey-publication"; }

    @GetMapping("/admin/encuestas/versiones/{id}/vista-previa")
    String versionPreview(@PathVariable String id, Model model) {
        var version = publications.get(id);
        model.addAttribute("version", version); model.addAttribute("survey", version.content().definition());
        model.addAttribute("values", Map.of()); model.addAttribute("errors", Map.of()); model.addAttribute("formToken", "preview");
        model.addAttribute("preview", true); model.addAttribute("frozenPreview", true);
        return "survey";
    }

    @PostMapping("/admin/encuestas/versiones/{id}/nueva-version")
    String successor(@PathVariable String id, Principal actor) { return "redirect:/admin/encuestas/" + publications.successor(id, actor.getName()); }

    @PostMapping("/admin/encuestas/versiones/{id}/cerrar")
    String close(@PathVariable String id, @RequestParam long revision, Principal actor) {
        publications.close(id, revision, actor.getName()); return versionRedirect(id);
    }

    @PostMapping("/admin/encuestas/versiones/{id}/activar")
    String activate(@PathVariable String id, @RequestParam long revision, Principal actor) {
        publications.activate(id, revision, actor.getName()); return versionRedirect(id);
    }

    @ExceptionHandler({IllegalArgumentException.class, ResponseStatusException.class})
    String problem(Exception ex, jakarta.servlet.http.HttpServletRequest request, HttpServletResponse response, Model model) {
        response.setStatus(ex instanceof ResponseStatusException status ? status.getStatusCode().value() : 422);
        model.addAttribute("message", ex instanceof ResponseStatusException status ? status.getReason() : ex.getMessage());
        boolean publicError = request.getRequestURI().startsWith("/encuestas/");
        model.addAttribute("publicError", publicError);
        model.addAttribute("back", publicError ? "/" : "/admin/encuestas"); return "survey-editor-error";
    }
    private static String versionRedirect(String id) { return "redirect:/admin/encuestas/versiones/" + id; }
}

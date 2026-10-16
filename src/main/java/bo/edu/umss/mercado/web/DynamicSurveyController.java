package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import bo.edu.umss.mercado.survey.SurveyValidator;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Cada acción de formulario identifica la versión fija, nunca la selección activa del momento. */
@Controller
public class DynamicSurveyController {
    private final SurveyPublicationService publications;
    private final SurveyResponseRepository responses;
    private final SurveyValidator validator;
    public DynamicSurveyController(SurveyPublicationService publications, SurveyResponseRepository responses, SurveyValidator validator) {
        this.publications = publications; this.responses = responses; this.validator = validator;
    }

    @GetMapping("/encuestas/{slug}/versiones/{code}")
    String open(@PathVariable String slug, @PathVariable String code, Model model, HttpSession session, HttpServletResponse response) {
        var version = publications.publicVersion(slug, code);
        response.setHeader("Cache-Control", "no-store");
        if (!"PUBLICADA".equals(version.status())) {
            response.setStatus(410); model.addAttribute("version", version); return "survey-publication-link";
        }
        return form(version, Map.of(), Map.of(), token(session, version.id()), model);
    }

    @PostMapping("/encuestas/{slug}/versiones/{code}")
    String submit(@PathVariable String slug, @PathVariable String code, @RequestParam MultiValueMap<String, String> values,
                  HttpSession session, HttpServletResponse response, Model model) {
        var version = publications.publicVersion(slug, code);
        List<String> received = values.getOrDefault("formToken", List.of());
        String supplied = received.size() == 1 ? received.getFirst() : "";
        if (!owns(session, supplied, version.id())) {
            if (!"PUBLICADA".equals(version.status())) throw closed();
            response.setStatus(422);
            return form(version, values, Map.of("_form", "El formulario expiró o no corresponde a esta versión. Revísalo antes de enviar."), token(session, version.id()), model);
        }
        var validation = validator.validate(version.content().definition(), values);
        if (!validation.valid()) {
            if (!"PUBLICADA".equals(version.status())) throw closed();
            response.setStatus(422); return form(version, values, validation.errors(), supplied, model);
        }
        String reference;
        try {
            try { reference = responses.savePublished(version.id(), supplied, validation); }
            catch (DuplicateKeyException ex) {
                var existing = responses.responseIdForToken(supplied);
                if (existing.isEmpty()) throw ex;
                reference = responses.verifyRetry(existing.get(), version.content().definition(), validation);
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 409) throw ex;
            response.setStatus(409); return form(version, values, Map.of("_form", ex.getReason()), supplied, model);
        }
        session.setAttribute("lastSubmissionReference", reference);
        return "redirect:/gracias";
    }

    private String form(SurveyPublicationService.Version version, Map<String, ?> values, Map<String, String> errors, String token, Model model) {
        model.addAttribute("survey", version.content().definition()); model.addAttribute("dynamicVersion", version);
        model.addAttribute("values", values); model.addAttribute("errors", errors); model.addAttribute("formToken", token);
        return "survey";
    }

    private String token(HttpSession session, String versionId) {
        synchronized (session) {
            Map<String, String> tokens = tokens(session);
            String reusable = tokens.entrySet().stream().filter(e -> e.getValue().equals(versionId) && !responses.submitted(e.getKey()))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
            if (reusable != null) return reusable;
            String token = UUID.randomUUID().toString(); tokens.put(token, versionId);
            while (tokens.size() > 64) tokens.remove(tokens.keySet().iterator().next());
            return token;
        }
    }
    private boolean owns(HttpSession session, String token, String versionId) {
        synchronized (session) { return versionId.equals(tokens(session).get(token)); }
    }
    @SuppressWarnings("unchecked")
    private Map<String, String> tokens(HttpSession session) {
        Object current = session.getAttribute("dynamicSurveyTokens");
        if (current == null) { current = new LinkedHashMap<String, String>(); session.setAttribute("dynamicSurveyTokens", current); }
        return (Map<String, String>) current;
    }
    private static ResponseStatusException closed() { return new ResponseStatusException(HttpStatus.GONE, "Esta versión está cerrada y no admite nuevos envíos."); }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException ex, HttpServletResponse response, Model model) {
        response.setStatus(ex.getStatusCode().value()); response.setHeader("Cache-Control", "no-store");
        model.addAttribute("publicError", true); model.addAttribute("message", ex.getReason()); model.addAttribute("back", "/");
        return "survey-editor-error";
    }
}

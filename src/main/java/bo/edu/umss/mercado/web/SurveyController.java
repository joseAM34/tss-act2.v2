package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.security.AdministratorService;
import bo.edu.umss.mercado.security.AdministratorSetupAccess;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.SurveyValidator;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class SurveyController {
    private final SurveyCatalog catalog;
    private final SurveyValidator validator;
    private final SurveyResponseRepository repository;
    private final AdministratorService administrators;
    private final AdministratorSetupAccess setupAccess;
    private final SurveyPublicationService publications;

    public SurveyController(SurveyCatalog catalog, SurveyValidator validator, SurveyResponseRepository repository,
                            AdministratorService administrators, AdministratorSetupAccess setupAccess, SurveyPublicationService publications) {
        this.catalog = catalog;
        this.validator = validator;
        this.repository = repository;
        this.administrators = administrators;
        this.setupAccess = setupAccess;
        this.publications = publications;
    }

    @GetMapping("/")
    String home(Model model) {
        var available = new java.util.ArrayList<>(catalog.all()); available.addAll(publications.activeDefinitions());
        model.addAttribute("surveys", available);
        return "home";
    }

    @GetMapping("/encuestas/{slug}")
    String survey(@PathVariable String slug, Model model, HttpSession session) {
        if (catalog.all().stream().noneMatch(s -> s.slug().equals(slug))) {
            var active = publications.activeVersion(slug); return "redirect:" + active.publicPath();
        }
        SurveyDefinition definition = catalog.get(slug);
        Map<String, String> tokens = tokens(session);
        String token = tokens.get(slug);
        if (token == null || repository.submitted(token)) {
            token = UUID.randomUUID().toString();
            tokens.put(slug, token);
        }
        form(model, definition, Map.of(), Map.of(), token);
        return "survey";
    }

    @PostMapping("/encuestas/{slug}")
    String submit(@PathVariable String slug, @RequestParam MultiValueMap<String, String> submitted,
                  Model model, HttpSession session, HttpServletResponse response) {
        SurveyDefinition definition = catalog.get(slug);
        var validation = validator.validate(definition, submitted);
        var errors = new LinkedHashMap<>(validation.errors());
        String expected = tokens(session).get(slug);
        List<String> tokenValues = submitted.getOrDefault("formToken", List.of());
        if (expected == null || !List.of(expected).equals(tokenValues)) {
            errors.put("_form", "El formulario expiró o no es válido. Recargue la encuesta antes de enviarla.");
        }
        if (!errors.isEmpty()) {
            if (expected == null) {
                expected = UUID.randomUUID().toString();
                tokens(session).put(slug, expected);
            }
            form(model, definition, submitted, errors, expected);
            return "survey";
        }
        String reference;
        try {
            try {
                reference = repository.save(definition, expected, validation);
            } catch (DuplicateKeyException ex) {
                var existing = repository.responseIdForToken(expected);
                if (existing.isEmpty()) throw ex;
                reference = repository.verifyRetry(existing.get(), definition, validation);
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 409) throw ex;
            response.setStatus(409);
            form(model, definition, submitted, Map.of("_form", ex.getReason()), expected);
            return "survey";
        }
        session.setAttribute("lastSubmissionReference", reference);
        return "redirect:/gracias";
    }

    private void form(Model model, SurveyDefinition definition, Map<String, List<String>> values,
                      Map<String, String> errors, String token) {
        model.addAttribute("survey", definition);
        model.addAttribute("values", values);
        model.addAttribute("errors", errors);
        model.addAttribute("formToken", token);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> tokens(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute("surveyTokens");
            if (existing == null) {
                existing = new ConcurrentHashMap<String, String>();
                session.setAttribute("surveyTokens", existing);
            }
            return (Map<String, String>) existing;
        }
    }

    @GetMapping("/gracias")
    String thanks(HttpServletRequest request, Model model) {
        HttpSession session = request.getSession(false);
        model.addAttribute("responseReference", session == null ? null : session.getAttribute("lastSubmissionReference"));
        return "thanks";
    }

    @GetMapping("/login")
    String login(HttpServletRequest request, Model model) {
        boolean pending = !administrators.exists();
        if (pending && setupAccess.allowed(request)) return "redirect:/configuracion-inicial";
        model.addAttribute("initialSetupPending", pending);
        return "login";
    }
}

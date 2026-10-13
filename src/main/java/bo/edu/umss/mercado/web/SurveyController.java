package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.SurveyValidator;
import jakarta.servlet.http.HttpSession;
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

@Controller
public class SurveyController {
    private final SurveyCatalog catalog;
    private final SurveyValidator validator;
    private final SurveyResponseRepository repository;

    public SurveyController(SurveyCatalog catalog, SurveyValidator validator, SurveyResponseRepository repository) {
        this.catalog = catalog;
        this.validator = validator;
        this.repository = repository;
    }

    @GetMapping("/")
    String home(Model model) {
        model.addAttribute("surveys", catalog.all());
        return "home";
    }

    @GetMapping("/encuestas/{slug}")
    String survey(@PathVariable String slug, Model model, HttpSession session) {
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
                  Model model, HttpSession session) {
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
        if (!repository.submitted(expected)) {
            try {
                repository.save(definition, expected, validation.answers());
            } catch (DuplicateKeyException ex) {
                if (!repository.submitted(expected)) throw ex;
            }
        }
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
    String thanks() {
        return "thanks";
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }
}

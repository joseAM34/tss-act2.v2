package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class AdminController {
    private final SurveyResponseRepository repository;
    private final SurveyCatalog catalog;

    public AdminController(SurveyResponseRepository repository, SurveyCatalog catalog) {
        this.repository = repository;
        this.catalog = catalog;
    }

    @GetMapping("/admin")
    String dashboard(Model model) {
        model.addAttribute("total", repository.count());
        model.addAttribute("graduateCount", repository.count("titulados"));
        model.addAttribute("employerCount", repository.count("empleadores"));
        model.addAttribute("responses", repository.recent());
        model.addAttribute("surveys", catalog.all());
        return "admin";
    }

    @GetMapping("/admin/respuestas/{id}")
    String response(@PathVariable String id, Model model) {
        model.addAttribute("response", repository.detail(id));
        return "response";
    }

    @GetMapping("/admin/exportar.csv")
    void export(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"respuestas-umss.csv\"");
        response.setHeader("Cache-Control", "no-store");
        repository.writeCsv(response.getWriter());
    }
}

package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.wrangling.WranglingService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminController {
    private final SurveyResponseRepository repository;
    private final SurveyCatalog catalog;
    private final WranglingService wrangling;
    private final bo.edu.umss.mercado.survey.SurveyWorkspaceService workspaces;

    public AdminController(SurveyResponseRepository repository, SurveyCatalog catalog, WranglingService wrangling, bo.edu.umss.mercado.survey.SurveyWorkspaceService workspaces) {
        this.repository = repository;
        this.catalog = catalog;
        this.wrangling = wrangling;
        this.workspaces = workspaces;
    }

    @GetMapping("/admin")
    String entry(Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");model.addAttribute("surveyCards",workspaces.all());return "workspace-list";
    }

    @GetMapping("/admin/herramientas")
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
        model.addAttribute("origin", wrangling.origin(id));
        model.addAttribute("importReference", repository.importReference(id));
        return "response";
    }

    @GetMapping("/admin/respuestas")
    String responses(@RequestParam(defaultValue = "") String survey, @RequestParam(defaultValue = "") String version,
                     Model model, HttpServletResponse response) {
        var options = repository.surveyOptions();
        String error = null;
        if (!survey.isEmpty() && options.stream().noneMatch(o -> o.slug().equals(survey))) error = "Selecciona una encuesta del listado.";
        var versions = survey.isEmpty() || error != null ? java.util.List.<String>of() : repository.versions(survey);
        if (!version.isEmpty() && !versions.contains(version)) error = "Selecciona primero una encuesta y después una de sus versiones.";
        model.addAttribute("surveyOptions", options); model.addAttribute("versions", versions);
        model.addAttribute("selectedSurvey", survey); model.addAttribute("selectedVersion", version);
        if (error != null) { response.setStatus(400); model.addAttribute("error", error); }
        model.addAttribute("responses", error == null ? repository.selected(survey, version) : java.util.List.of());
        model.addAttribute("total", error == null ? repository.selectedCount(survey, version) : 0);
        return "survey-responses";
    }

    @GetMapping("/admin/exportar.csv")
    void export(HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"respuestas-umss.csv\"");
        response.setHeader("Cache-Control", "no-store");
        repository.writeCsv(response.getWriter());
    }
}

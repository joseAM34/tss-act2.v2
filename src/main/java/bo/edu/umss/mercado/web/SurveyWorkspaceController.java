package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/espacio")
public class SurveyWorkspaceController {
    private final SurveyWorkspaceService workspaces;
    private final SurveyResponseRepository responses;
    private final DraftSurveyService drafts;
    private final SurveyPublicationService publications;
    private final bo.edu.umss.mercado.statistics.LiveResultsService live;
    private final bo.edu.umss.mercado.statistics.WorkspaceResultsService results;
    public SurveyWorkspaceController(SurveyWorkspaceService workspaces, SurveyResponseRepository responses, DraftSurveyService drafts, SurveyPublicationService publications,bo.edu.umss.mercado.statistics.LiveResultsService live,bo.edu.umss.mercado.statistics.WorkspaceResultsService results) {
        this.workspaces=workspaces;this.responses=responses;this.drafts=drafts;this.publications=publications;
        this.live=live;
        this.results=results;
    }
    @GetMapping
    String list(@RequestParam(defaultValue="false") boolean archivadas,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); model.addAttribute("surveyCards",workspaces.all(archivadas));
        model.addAttribute("includeArchived",archivadas); return "workspace-list";
    }
    @PostMapping
    String create(@RequestParam String title,@RequestParam(defaultValue="") String description,Principal actor,Model model,HttpServletResponse response) {
        try { return "redirect:/admin/encuestas/"+drafts.create(title,description,actor.getName()); }
        catch(IllegalArgumentException ex) { response.setStatus(422);model.addAttribute("error",ex.getMessage());model.addAttribute("createTitle",title);model.addAttribute("createDescription",description);return list(false,model,response); }
    }
    @GetMapping({"/{slug}","/{slug}/{section}"})
    String workspace(@PathVariable String slug,@PathVariable(required=false) String section,@RequestParam(defaultValue="") String version,
                     @RequestParam(defaultValue="NO_DECLARADO") String origin,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String until,
                     @RequestParam(defaultValue="") String row,@RequestParam(defaultValue="") String column,java.security.Principal actor,Model model,HttpServletResponse response) {
        section=section==null?"resumen":section;
        if(!List.of("resumen","versiones","respuestas","resultados","importar","reportes").contains(section)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var w=workspaces.open(slug,version);
        response.setHeader("Cache-Control","no-store");model.addAttribute("workspace",w);model.addAttribute("workspaceSection",section);
        model.addAttribute("counts",workspaces.counts(w));
        if(!w.legacy() && !w.selected().draft()) model.addAttribute("publication",publications.get(w.selected().id()));
        if(section.equals("respuestas")) model.addAttribute("responses",responses.selected(slug,w.selected().code()));
        if(section.equals("resultados")) {
            model.addAttribute("prepared",workspaces.preparations(w));model.addAttribute("savedAnalyses",workspaces.analyses(w));
            model.addAttribute("resultsMode","total");model.addAttribute("resultCuts",w.legacy()?List.of():results.cuts(slug));
            model.addAttribute("resultsFrom","");model.addAttribute("resultsUntil","");model.addAttribute("selectedVersions",List.of(w.selected().id()));
        }
        if(section.equals("reportes")) {model.addAttribute("savedReports",workspaces.reports(w));model.addAttribute("savedAnalyses",workspaces.analyses(w));}
        if(section.equals("importar")) {model.addAttribute("savedImports",workspaces.imports(w));model.addAttribute("savedWorkbooks",workspaces.workbooks(w));}
        return "workspace";
    }
    @PostMapping("/{slug}/variante")
    String variant(@PathVariable String slug,@RequestParam String version,Principal actor) {
        var w=workspaces.open(slug,version);
        String id;
        if(w.legacy()) {
            if(!w.selected().active()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Para copiar el cuestionario original elige su formulario actual.");
            id=drafts.copyLegacy(slug,actor.getName());
        } else if(w.selected().draft()) id=drafts.duplicate(w.selected().id(),actor.getName());
        else id=drafts.copyPublished(publications.get(w.selected().id()).content(),actor.getName());
        return "redirect:/admin/encuestas/"+id;
    }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException ex,Model model,HttpServletResponse response) {
        response.setStatus(ex.getStatusCode().value());response.setHeader("Cache-Control","no-store");
        model.addAttribute("message",ex.getReason());model.addAttribute("back","/admin/espacio");return "survey-editor-error";
    }
}

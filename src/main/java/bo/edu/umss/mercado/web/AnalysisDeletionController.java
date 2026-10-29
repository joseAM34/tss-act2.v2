package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.reporting.AnalysisDeletionService;
import bo.edu.umss.mercado.survey.SurveyWorkspaceService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/espacio/{slug}/reportes/analisis/{id}/eliminar")
public class AnalysisDeletionController {
    private final SurveyWorkspaceService workspaces;
    private final AnalysisDeletionService deletion;
    public AnalysisDeletionController(SurveyWorkspaceService workspaces,AnalysisDeletionService deletion){this.workspaces=workspaces;this.deletion=deletion;}
    @GetMapping
    String confirmation(@PathVariable String slug,@PathVariable String id,@RequestParam String version,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var workspace=workspaces.open(slug,version);
        model.addAttribute("workspace",workspace);model.addAttribute("workspaceSection","reportes");model.addAttribute("deletion",deletion.state(workspace,id));return "analysis-delete";
    }
    @PostMapping
    String delete(@PathVariable String slug,@PathVariable String id,@RequestParam String version,@RequestParam String expectedHash,@RequestParam(defaultValue="false") boolean confirm,Principal actor,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var workspace=workspaces.open(slug,version);deletion.delete(workspace,id,expectedHash,confirm,actor.getName());return "redirect:"+workspace.link("reportes");
    }
    @ExceptionHandler({ResponseStatusException.class,DataAccessException.class})
    String error(Exception ex,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");response.setStatus(ex instanceof ResponseStatusException r?r.getStatusCode().value():409);
        model.addAttribute("message",ex instanceof ResponseStatusException r?r.getReason():"No se pudo eliminar de forma consistente. Revisa el estado actual y vuelve a intentar.");model.addAttribute("back","/admin/espacio");return "survey-editor-error";
    }
}

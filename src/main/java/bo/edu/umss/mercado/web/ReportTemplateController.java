package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.reporting.*;
import bo.edu.umss.mercado.statistics.*;
import bo.edu.umss.mercado.survey.*;
import jakarta.servlet.http.*;
import java.security.Principal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;

@Controller @RequestMapping("/admin/plantillas-reportes")
public class ReportTemplateController {
    private final ReportTemplateLibrary library;private final ReportPresentationTemplates templates;private final GenericAnalysisService analyses;private final WorkspaceResultsService results;private final SurveyPublicationService publications;
    public ReportTemplateController(ReportPresentationTemplates templates,GenericAnalysisService analyses,WorkspaceResultsService results,SurveyPublicationService publications,ReportTemplateLibrary library){this.library=library;this.templates=templates;this.analyses=analyses;this.results=results;this.publications=publications;}
    @GetMapping({"/nueva","/{id}/{mode:usar|editar}"})
    String choose(@PathVariable(required=false) String id,@PathVariable(required=false) String mode,@RequestParam(defaultValue="") String workspaceVersionId,Model model,HttpServletResponse response){response.setHeader("Cache-Control","no-store");model.addAllAttributes(library.picker(id,id==null?"create":mode.equals("usar")?"use":"edit",workspaceVersionId));return "report-template-source";}
    @SuppressWarnings("unchecked")
    @PostMapping("/abrir")
    String open(@RequestParam(defaultValue="") String templateId,@RequestParam String mode,@RequestParam String sourceType,@RequestParam(defaultValue="") String versionId,@RequestParam(defaultValue="") String cutId,Principal actor,HttpSession session){
        if(!Set.of("create","edit","use","report").contains(mode)||!Set.of("total","cut").contains(sourceType))throw new IllegalArgumentException("Selecciona la plantilla y su fuente de datos.");
        ReportTemplateLibrary.Entry selected=templateId.isBlank()?null:library.entry(templateId);if(selected==null&&!Set.of("create","report").contains(mode))throw new IllegalArgumentException("Selecciona una plantilla.");
        WorkspaceResultsService.Snapshot frame;
        if(sourceType.equals("total")){if(versionId.isBlank())throw new IllegalArgumentException("Selecciona una versión de encuesta.");var version=publications.get(versionId);if(!Set.of("PUBLICADA","CERRADA").contains(version.status()))throw new IllegalArgumentException("Selecciona una versión publicada o cerrada.");frame=results.load(version.slug(),List.of(version.id()),"","",actor.getName());}
        else{var cut=library.cuts().stream().filter(c->cutId.equals(c.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Selecciona un corte guardado."));frame=results.cut((String)cut.get("slug"),cutId).snapshot();}
        String analysis=results.materialize(frame,actor.getName());var config=ReportCustomization.defaults(analyses.get(analysis),List.of());
        if(selected!=null){if(selected.legacy()){var d=library.legacy(templateId).definition();config=new ReportCustomization.Configuration(analysis,d.title(),d.introduction(),d.sections().contains("CONTEXTO"),true,d.charts(),"",config.questions(),List.of());}else config=templates.apply(templateId,analysis,actor.getName());}
        String editingId=mode.equals("edit")&&selected!=null&&!selected.legacy()?templateId:"";String hash=editingId.isBlank()?"":selected.hash();
        var state=new LinkedHashMap<String,Object>();state.put("configuration",config);state.put("mode",mode);state.put("id",editingId);state.put("hash",hash);state.put("name",selected==null?"":selected.name());
        String token=UUID.randomUUID().toString();synchronized(session){var old=session.getAttribute("reportTemplateEditors");var editors=old instanceof Map<?,?>?new LinkedHashMap<>((Map<String,Map<String,Object>>)old):new LinkedHashMap<String,Map<String,Object>>();editors.put(token,state);while(editors.size()>12)editors.remove(editors.keySet().iterator().next());session.setAttribute("reportTemplateEditors",editors);}
        return "redirect:/admin/reportes/nuevo?analysisId="+analysis+"&templateEditor="+token;
    }
    public record Save(ReportCustomization.Configuration configuration,String expectedHash,String templateId,String templateHash,String name){}
    @PostMapping("/guardar") @ResponseBody Map<String,String> save(@RequestBody Save body,Principal actor,HttpServletResponse response){response.setHeader("Cache-Control","no-store");String id=templates.save(body.configuration(),body.expectedHash(),body.templateId(),body.templateHash(),body.name(),actor.getName());return Map.of("id",id,"url","/admin/reportes");}
    @PostMapping("/{id}/duplicar") String duplicate(@PathVariable String id,@RequestParam String expectedHash,@RequestParam String name,Principal actor){if(id.startsWith("legacy-"))throw new IllegalArgumentException("Abre la plantilla anterior en el editor actual y guárdala como una nueva.");String copy=templates.duplicate(id,expectedHash,name,actor.getName());return "redirect:/admin/plantillas-reportes/"+copy+"/editar";}
    @PostMapping("/{id}/eliminar") String delete(@PathVariable String id,@RequestParam String expectedHash,@RequestParam(defaultValue="false") boolean confirm,Principal actor){if(id.startsWith("legacy-"))library.hideLegacy(id,expectedHash,confirm,actor.getName());else templates.delete(id,expectedHash,confirm,actor.getName());return "redirect:/admin/reportes";}
    @ExceptionHandler(IllegalArgumentException.class) Object invalid(IllegalArgumentException ex,HttpServletRequest request,HttpServletResponse response,Model model){response.setHeader("Cache-Control","no-store");if(request.getRequestURI().endsWith("/guardar"))return ResponseEntity.badRequest().body(Map.of("message",ex.getMessage()));response.setStatus(400);String id=Objects.toString(request.getParameter("templateId"),"");String mode=Objects.toString(request.getParameter("mode"),"report");model.addAllAttributes(library.picker(id,mode,Objects.toString(request.getParameter("versionId"),"")));model.addAttribute("sourceType",request.getParameter("sourceType"));model.addAttribute("error",ex.getMessage());return "report-template-source";}
    @ExceptionHandler(ResponseStatusException.class) @ResponseBody ResponseEntity<?> statusError(ResponseStatusException ex){return ResponseEntity.status(ex.getStatusCode()).header("Cache-Control","no-store").body(Map.of("message",Objects.toString(ex.getReason(),"No se pudo completar la operación.")));}
}

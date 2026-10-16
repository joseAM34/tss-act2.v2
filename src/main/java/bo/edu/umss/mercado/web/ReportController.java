package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.reporting.ReportService;
import bo.edu.umss.mercado.reporting.ReportExport;
import bo.edu.umss.mercado.statistics.GenericAnalysisService;
import bo.edu.umss.mercado.statistics.GenericCrossService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller @RequestMapping("/admin/reportes")
public class ReportController {
    private final ReportService reports;
    private final GenericAnalysisService analyses;
    private final GenericCrossService crosses;
    public ReportController(ReportService reports,GenericAnalysisService analyses,GenericCrossService crosses) { this.reports=reports; this.analyses=analyses; this.crosses=crosses; }
    @GetMapping
    String index(Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); model.addAttribute("reports",reports.recent()); model.addAttribute("revisions",reports.revisions()); return "reports";
    }
    @GetMapping("/plantillas/nueva")
    String newTemplate(Model model,HttpServletResponse response) { return editor("",0,new ReportService.Template("","",ReportService.SECTIONS,true),model,response); }
    @GetMapping("/plantillas/{id}/editar")
    String edit(@PathVariable String id,Model model,HttpServletResponse response) {
        var latest=reports.latest(id); model.addAttribute("history",reports.revisions().stream().filter(r -> r.templateId().equals(id)).toList());
        return editor(id,latest.revision(),latest.definition(),model,response);
    }
    private String editor(String id,int revision,ReportService.Template d,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); model.addAttribute("templateId",id); model.addAttribute("expectedRevision",revision); model.addAttribute("definition",d); model.addAttribute("sections",ReportService.SECTIONS); return "report-template-editor";
    }
    @PostMapping("/plantillas")
    String saveTemplate(@RequestParam(defaultValue="") String templateId,@RequestParam int expectedRevision,@RequestParam String title,
                        @RequestParam(defaultValue="") String introduction,@RequestParam(defaultValue="false") boolean charts,
                        @RequestParam(defaultValue="") String section1,@RequestParam(defaultValue="") String section2,
                        @RequestParam(defaultValue="") String section3,@RequestParam(defaultValue="") String section4,
                        Principal actor,Model model,HttpServletResponse response) {
        var d=new ReportService.Template(title,introduction,List.of(section1,section2,section3,section4).stream().filter(s -> !s.isBlank()).toList(),charts);
        try {
            if (templateId.isEmpty()) { if (expectedRevision != 0) throw new IllegalArgumentException("Revisión inicial no válida."); templateId=reports.createTemplate(d,actor.getName()); }
            else reports.revise(templateId,expectedRevision,d,actor.getName());
            var workspace=(bo.edu.umss.mercado.survey.SurveyWorkspaceService.Workspace)model.getAttribute("workspace");
            return workspace==null?"redirect:/admin/reportes":"redirect:"+workspace.link("reportes");
        } catch (IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error",ex.getMessage()); return editor(templateId,expectedRevision,d,model,response); }
    }
    @GetMapping("/nuevo")
    String newReport(@RequestParam(defaultValue="") String analysisId,@RequestParam(defaultValue="") String templateRevisionId,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); model.addAttribute("analyses",analyses.recent());
        var revisions=new ArrayList<>(reports.revisions());
        if (!templateRevisionId.isBlank() && revisions.stream().noneMatch(r -> r.id().equals(templateRevisionId))) revisions.add(reports.revision(templateRevisionId));
        model.addAttribute("revisions",revisions); model.addAttribute("analysisId",analysisId); model.addAttribute("templateRevisionId",templateRevisionId);
        if (!analysisId.isBlank()) {
            var analysis=analyses.get(analysisId); model.addAttribute("analysis",analysis);
            var result=(Map<?,?>)analysis.get("result");
            model.addAttribute("analyses",List.of(new GenericAnalysisService.Summary(analysisId,(String)analysis.get("preparation_id"),(String)analysis.get("created_at"),
                    (String)result.get("title"),(String)result.get("instrument_version"),(String)result.get("origin"))));
            model.addAttribute("crosses",crosses.forPreparation((String)analysis.get("preparation_id")).stream().map(c -> crosses.get(c.id())).toList());
        }
        return "report-generator";
    }
    @PostMapping
    String generate(@RequestParam String templateRevisionId,@RequestParam String analysisId,@RequestParam(defaultValue="") List<String> crossIds,
                    @RequestParam(defaultValue="") String comment,Principal actor,Model model,HttpServletResponse response) {
        try { return "redirect:/admin/reportes/"+reports.generate(templateRevisionId,analysisId,crossIds,comment,actor.getName()); }
        catch (IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error",ex.getMessage()); model.addAttribute("comment",comment); model.addAttribute("selectedCrossIds",crossIds); return newReport(analysisId,templateRevisionId,model,response); }
    }
    @GetMapping("/{id}")
    String report(@PathVariable String id,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); var report=reports.get(id); model.addAttribute("report",report); model.addAttribute("content",report.get("content")); return "generated-report";
    }
    @GetMapping("/{id}/nuevo")
    @SuppressWarnings("unchecked")
    String another(@PathVariable String id,Model model,HttpServletResponse response) {
        var content=(Map<String,Object>)reports.get(id).get("content"); var analysis=(Map<String,Object>)content.get("analysis");
        model.addAttribute("comment",content.get("comment"));
        model.addAttribute("selectedCrossIds",((List<Map<String,Object>>)content.get("crosses")).stream().map(c -> c.get("id")).toList());
        return newReport((String)analysis.get("id"),(String)content.get("template_revision_id"),model,response);
    }
    @GetMapping("/{id}/contenido.json")
    void json(@PathVariable String id,HttpServletResponse response) throws IOException {
        var report=reports.get(id); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control","no-store"); response.getWriter().write(CanonicalJson.json(report));
    }
    @GetMapping("/{id}/resultados.{format:csv|xlsx}")
    void download(@PathVariable String id,@PathVariable String format,HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control","no-store");
        var rows=ReportExport.rows(reports.get(id));
        byte[] bytes=format.equals("csv")?ReportExport.csv(rows):ReportExport.xlsx(rows);
        response.setContentType(format.equals("csv")?"text/csv;charset=UTF-8":"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Cache-Control","no-store");
        response.setHeader("Content-Disposition","attachment; filename=\"reporte-"+id.replaceAll("[^A-Za-z0-9-]","_")+"."+format+"\"");
        response.setContentLength(bytes.length); response.getOutputStream().write(bytes);
    }
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException ex,Model model,HttpServletResponse response) {
        response.setStatus(ex.getStatusCode().value()); response.setHeader("Cache-Control","no-store"); model.addAttribute("message",ex.getReason()); model.addAttribute("back","/admin/reportes"); return "survey-editor-error";
    }
}

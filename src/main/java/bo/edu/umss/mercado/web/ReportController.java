package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.reporting.ReportService;
import bo.edu.umss.mercado.reporting.ReportExport;
import bo.edu.umss.mercado.reporting.ReportCustomization;
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
    @ModelAttribute
    void workspacePreferences(@RequestParam(defaultValue="") String analysisId,@RequestParam(defaultValue="") String templateEditor,Model model,jakarta.servlet.http.HttpSession session){
        if(session.getAttribute("workspaceReportPreferences") instanceof Map<?,?> saved && saved.get(analysisId) instanceof ReportCustomization.Configuration config)model.addAttribute("customConfiguration",config);
        if(!templateEditor.isBlank()){if(!(session.getAttribute("reportTemplateEditors") instanceof Map<?,?> editors)||!(editors.get(templateEditor) instanceof Map<?,?> state)||!(state.get("configuration") instanceof ReportCustomization.Configuration config)||!analysisId.equals(config.analysisId()))throw new IllegalArgumentException("La sesión de edición de la plantilla ya no está disponible. Elige nuevamente la fuente.");model.addAttribute("customConfiguration",config);model.addAttribute("templateEditState",Map.of("id",state.get("id"),"hash",state.get("hash"),"name",state.get("name"),"editing",Set.of("create","edit").contains(state.get("mode"))));}
    }
    private final bo.edu.umss.mercado.reporting.ReportTemplateLibrary templateLibrary;private final ReportService reports;
    private final bo.edu.umss.mercado.reporting.ReportPresentationTemplates presentations;
    private final GenericAnalysisService analyses;
    private final GenericCrossService crosses;
    private final org.thymeleaf.spring6.SpringTemplateEngine templates;
    private final bo.edu.umss.mercado.reporting.ReportCrossComposer composer;
    public ReportController(ReportService reports,GenericAnalysisService analyses,GenericCrossService crosses,org.thymeleaf.spring6.SpringTemplateEngine templates,bo.edu.umss.mercado.reporting.ReportCrossComposer composer,bo.edu.umss.mercado.reporting.ReportPresentationTemplates presentations,bo.edu.umss.mercado.reporting.ReportTemplateLibrary templateLibrary) { this.templateLibrary=templateLibrary;this.presentations=presentations; this.reports=reports; this.analyses=analyses; this.crosses=crosses;this.templates=templates;this.composer=composer; }
    @GetMapping
    String index(Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); model.addAttribute("reports",reports.recent()); model.addAttribute("revisions",reports.revisions());model.addAttribute("templateEntries",templateLibrary.entries());return "reports";
    }
    @GetMapping("/plantillas/nueva")
    String newTemplate(Model model,HttpServletResponse response) { response.setHeader("Cache-Control","no-store");var w=(bo.edu.umss.mercado.survey.SurveyWorkspaceService.Workspace)model.getAttribute("workspace");model.addAllAttributes(templateLibrary.picker("","create",w==null?"":w.selected().id()));return "report-template-source"; }
    @GetMapping("/plantillas/{id}/editar")
    String edit(@PathVariable String id,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");String key=presentations.recent().stream().anyMatch(t->t.id().equals(id))?id:"legacy-"+id;model.addAllAttributes(templateLibrary.picker(key,"edit",""));return "report-template-source";
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
    String newReport(@RequestParam(defaultValue="") String analysisId,@RequestParam(defaultValue="") String templateRevisionId,@RequestParam(defaultValue="") String crossId,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); model.addAttribute("analyses",analyses.recent());
        if(analysisId.isBlank()){model.addAllAttributes(templateLibrary.picker(templateRevisionId.isBlank()?"":"legacy-"+reports.revision(templateRevisionId).templateId(),templateRevisionId.isBlank()?"report":"use",""));return "report-template-source";}
        if(!templateRevisionId.isBlank()){var legacy=reports.revision(templateRevisionId).definition();var base=ReportCustomization.defaults(analyses.get(analysisId),List.of());model.addAttribute("customConfiguration",new ReportCustomization.Configuration(analysisId,legacy.title(),legacy.introduction(),legacy.sections().contains("CONTEXTO"),true,legacy.charts(),Objects.toString(model.getAttribute("comment"),""),base.questions(),List.of()));templateRevisionId="";}
        var revisions=new ArrayList<>(reports.revisions());
        if (!templateRevisionId.isBlank()) {var selectedRevision=reports.revision(templateRevisionId);if(revisions.stream().noneMatch(r -> r.id().equals(selectedRevision.id())))revisions.add(selectedRevision);}
        model.addAttribute("revisions",revisions); model.addAttribute("analysisId",analysisId); model.addAttribute("templateRevisionId",templateRevisionId);
        if (!analysisId.isBlank()) {
            var analysis=analyses.get(analysisId); model.addAttribute("analysis",analysis);
            var result=(Map<?,?>)analysis.get("result");
            model.addAttribute("analyses",List.of(new GenericAnalysisService.Summary(analysisId,(String)analysis.get("preparation_id"),(String)analysis.get("created_at"),
                    (String)result.get("title"),(String)result.get("instrument_version"),(String)result.get("origin"))));
            model.addAttribute("crosses",crosses.forPreparation((String)analysis.get("preparation_id")).stream().map(c -> crosses.get(c.id())).toList());
            model.addAttribute("sourceCutoff",reports.sourceCutoff(analysisId));
            if(!crossId.isBlank()) {
                var cross=crosses.get(crossId);
                if(!analysis.get("preparation_id").equals(cross.get("preparation_id")))throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"El cruce no pertenece al corte seleccionado.");
                model.addAttribute("selectedCrossIds",List.of(crossId));
            }
        }
        if(!analysisId.isBlank() && templateRevisionId.isBlank()) {
            var analysis=analyses.get(analysisId);
            @SuppressWarnings("unchecked") var chosen=(List<String>)model.getAttribute("selectedCrossIds");
            var config=ReportCustomization.defaults(analysis,chosen==null?List.of():chosen);
            if(model.getAttribute("customConfiguration") instanceof ReportCustomization.Configuration previous)config=previous;
            else config=new ReportCustomization.Configuration(config.analysisId(),Objects.toString(model.getAttribute("title"),config.title()),config.introduction(),config.context(),config.tables(),config.charts(),Objects.toString(model.getAttribute("comment"),""),config.questions(),config.crossIds());
            @SuppressWarnings("unchecked") var available=new ArrayList<>((List<Map<String,Object>>)model.getAttribute("crosses"));
            var referenced=new LinkedHashSet<>(config.crossIds());
            if(config.crossSettings()!=null)config.crossSettings().forEach(c->referenced.add(c.id()));
            for(String id:referenced)if(available.stream().noneMatch(c->id.equals(c.get("id")))){
                var source=crosses.get(id);
                if(!analysis.get("preparation_id").equals(source.get("preparation_id")))throw new IllegalArgumentException("Cruce ajeno al conjunto seleccionado.");
                available.add(source);
            }
            model.addAttribute("crosses",available);
            var advancedData=reports.advancedData(analysisId);
            model.addAttribute("presentationTemplates",presentations.recent());
            model.addAttribute("customBootstrap",CanonicalJson.json(Map.of("templateEditor",model.getAttribute("templateEditState")==null?Map.of():model.getAttribute("templateEditState"),"advancedOptions",advancedData.get("options"),"configuration",config,"result",advancedData.get("result"),"crosses",model.getAttribute("crosses"),"cutoff",model.getAttribute("sourceCutoff"),"crossChoices",composer.choices(analysisId))));
            return "report-customizer";
        }
        return "report-generator";
    }
    private org.thymeleaf.context.WebContext webContext(jakarta.servlet.http.HttpServletRequest request,HttpServletResponse response,Map<String,Object> variables) {
        var application=org.thymeleaf.web.servlet.JakartaServletWebApplication.buildApplication(request.getServletContext());
        return new org.thymeleaf.context.WebContext(application.buildExchange(request,response),Locale.forLanguageTag("es"),variables);
    }
    public record CustomizedGeneration(ReportCustomization.Configuration configuration,String expectedHash,Boolean saveTemplate,String templateName) {}
    @PostMapping("/personalizar/vista-previa") @ResponseBody
    Map<String,Object> preview(@RequestBody ReportCustomization.Configuration configuration,@RequestParam(defaultValue="") String focusCross,Principal actor,HttpServletResponse response,jakarta.servlet.http.HttpServletRequest request) {
        response.setHeader("Cache-Control","no-store");
        var content=reports.preview(configuration,actor.getName());
        var context=webContext(request,response,Map.of("content",content,"report",Map.of("created_at","Vista previa","actor",actor.getName())));
        var all=new ReportCustomization.Configuration(configuration.analysisId(),configuration.title(),configuration.introduction(),configuration.context(),configuration.tables(),configuration.charts(),configuration.comment(),
                configuration.questions().stream().map(q->ReportCustomization.catalogQuestion(q,configuration)).toList(),focusCross.isBlank()||!ReportCustomization.activeCross(focusCross,configuration)?configuration.crossIds():List.of(focusCross),configuration.crossSettings(),configuration.advanced()==null?null:configuration.advanced().stream().map(a->new ReportCustomization.Advanced(a.type(),a.questions(),a.title(),a.comment(),true)).toList());
        var catalog=CanonicalJson.hash(all).equals(CanonicalJson.hash(configuration))?content:reports.preview(all,actor.getName());
        var catalogContext=webContext(request,response,Map.of("content",catalog,"report",Map.of("created_at","Vista previa","actor",actor.getName())));
        return Map.of("content",content,"hash",CanonicalJson.hash(content),"html",templates.process("fragments/report-document",Set.of("documentContent"),context),
                "catalog",catalog,"catalogHtml",templates.process("fragments/report-document",Set.of("documentContent"),catalogContext));
    }
    @PostMapping("/personalizar/cruces/vista-previa") @ResponseBody
    Map<String,Object> crossPreview(@RequestBody bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair pair,HttpServletResponse response,jakarta.servlet.http.HttpServletRequest request){
        response.setHeader("Cache-Control","no-store");var snapshot=composer.preview(pair);
        var context=webContext(request,response,Map.of("c",snapshot,"index",0,"charts",true));
        return Map.of("cross",snapshot,"hash",CanonicalJson.hash(snapshot),"html",templates.process("fragments/report-results",Set.of("cross"),context));
    }
    @PostMapping("/personalizar/cruces") @ResponseBody
    Map<String,Object> saveCross(@RequestBody bo.edu.umss.mercado.reporting.ReportCrossComposer.Save request,Principal actor,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");return composer.save(request,actor.getName());
    }
    @PostMapping("/personalizar/generar") @ResponseBody
    Map<String,String> customized(@RequestBody CustomizedGeneration request,Principal actor,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return Map.of("url","/admin/reportes/"+presentations.generate(request.configuration(),request.expectedHash(),Boolean.TRUE.equals(request.saveTemplate()),request.templateName(),actor.getName()));
    }
    public record ApplyPresentation(String templateId,String analysisId) {}
    @PostMapping("/personalizar/plantillas/aplicar") @ResponseBody
    Map<String,Object> applyPresentation(@RequestBody ApplyPresentation request,Principal actor,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        var config=presentations.apply(request.templateId(),request.analysisId(),actor.getName());
        return Map.of("configuration",config,"crosses",config.crossIds().stream().map(crosses::get).toList());
    }
    @ExceptionHandler(IllegalArgumentException.class) @ResponseBody
    org.springframework.http.ResponseEntity<Map<String,String>> invalid(IllegalArgumentException ex) {
        return org.springframework.http.ResponseEntity.badRequest().cacheControl(org.springframework.http.CacheControl.noStore()).body(Map.of("message",ex.getMessage()));
    }
    @PostMapping
    String generate(@RequestParam(defaultValue="") String templateRevisionId,@RequestParam String analysisId,@RequestParam(defaultValue="") List<String> crossIds,
                    @RequestParam(defaultValue="") String title,
                    @RequestParam(defaultValue="") String comment,Principal actor,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        try { return "redirect:/admin/reportes/"+(templateRevisionId.isBlank()?reports.generateDirect(analysisId,title,crossIds,comment,actor.getName()):reports.generate(templateRevisionId,analysisId,crossIds,comment,actor.getName())); }
        catch (IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error",ex.getMessage()); model.addAttribute("title",title); model.addAttribute("comment",comment); model.addAttribute("selectedCrossIds",crossIds); return newReport(analysisId,templateRevisionId,"",model,response); }
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
        model.addAttribute("title",((Map<?,?>)content.get("template")).get("title"));
        if(content.get("presentation")!=null)model.addAttribute("customConfiguration",CanonicalJson.read(CanonicalJson.json(content.get("presentation")),ReportCustomization.Configuration.class));
        model.addAttribute("selectedCrossIds",((List<Map<String,Object>>)content.get("crosses")).stream().map(c -> c.get("id")).toList());
        return newReport((String)analysis.get("id"),Objects.toString(content.get("template_revision_id"),""),"",model,response);
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
    String problem(ResponseStatusException ex,Model model,HttpServletResponse response,jakarta.servlet.http.HttpServletRequest request) throws IOException {
        response.setStatus(ex.getStatusCode().value()); response.setHeader("Cache-Control","no-store");
        if(request.getRequestURI().startsWith("/admin/reportes/personalizar/")) {
            response.setContentType("application/json;charset=UTF-8");response.getWriter().write(CanonicalJson.json(Map.of("message",Objects.toString(ex.getReason(),"No se pudo verificar el reporte."))));return null;
        }
        model.addAttribute("message",ex.getReason()); model.addAttribute("back","/admin/reportes"); return "survey-editor-error";
    }
}

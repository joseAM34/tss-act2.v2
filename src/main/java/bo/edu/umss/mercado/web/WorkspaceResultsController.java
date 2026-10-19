package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.*;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.reporting.ReportCustomization;
import jakarta.servlet.http.*;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.*;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;

@Controller @RequestMapping("/admin/espacio/{slug}/resultados")
public class WorkspaceResultsController {
    private final WorkspaceResultsService results;
    private final SurveyWorkspaceService spaces;
    private final SurveyPublicationService publications;
    private final GenericAnalysisService analyses;
    private final SpringTemplateEngine templates;
    private final TextDictionaryService dictionaries;
    private final WikidataTerms wikidata;
    private final UniversalTermLookup lookup;
    public WorkspaceResultsController(WorkspaceResultsService results,SurveyWorkspaceService spaces,SurveyPublicationService publications,GenericAnalysisService analyses,SpringTemplateEngine templates,TextDictionaryService dictionaries,WikidataTerms wikidata,UniversalTermLookup lookup){this.results=results;this.spaces=spaces;this.publications=publications;this.analyses=analyses;this.templates=templates;this.dictionaries=dictionaries;this.wikidata=wikidata;this.lookup=lookup;}
    private static class Cache {final Map<String,WorkspaceResultsService.Snapshot> frames=new LinkedHashMap<>();final Map<String,WorkspaceResultsService.Snapshot> history=new LinkedHashMap<>();}
    private Cache cache(HttpSession session){synchronized(session){if(!(session.getAttribute("workspaceResultsCache") instanceof Cache))session.setAttribute("workspaceResultsCache",new Cache());return (Cache)session.getAttribute("workspaceResultsCache");}}
    private SurveyWorkspaceService.Workspace workspace(String slug,String version){var w=spaces.open(slug,version);if(w.legacy()||w.selected().draft())throw new IllegalArgumentException("Selecciona una versión publicada desde el editor.");return w;}
    private List<String> selection(SurveyWorkspaceService.Workspace w,List<String> ids,boolean all){
        var available=w.versions().stream().filter(v->!v.draft()&&!v.id().isBlank()).map(SurveyWorkspaceService.Version::id).toList();
        var selected=all?available:ids==null||ids.isEmpty()?List.of(w.selected().id()):ids;
        if(selected.isEmpty()||new HashSet<>(selected).size()!=selected.size()||!available.containsAll(selected))throw new IllegalArgumentException("Selecciona versiones de esta encuesta.");
        return available.stream().filter(selected::contains).toList();
    }
    private WorkspaceResultsService.Snapshot frame(String slug,String version,List<String> ids,boolean all,String from,String until,String cutId,boolean refresh,String actor,HttpSession session){
        var w=workspace(slug,version);WorkspaceResultsService.Cut cut=cutId.isBlank()?null:results.cut(slug,cutId);
        boolean full=cut==null?all:cut.snapshot().allVersions();List<String> selected=cut==null?selection(w,ids,all):refresh&&full?selection(w,null,true):cut.snapshot().versionIds();String first=cut==null?from:cut.snapshot().from(),last=cut==null?until:cut.snapshot().until();
        String key=CanonicalJson.hash(cut==null?List.of(slug,selected,first,last,full):List.of(slug,cutId));var cache=cache(session);
        synchronized(cache){
            if(!refresh&&cache.frames.containsKey(key))return cache.frames.get(key);
            var snapshot=cut!=null&&!refresh?cut.snapshot():results.load(slug,selected,first,last,actor,full);
            cache.frames.put(key,snapshot);cache.history.put(snapshot.id(),snapshot);while(cache.frames.size()>12)cache.frames.remove(cache.frames.keySet().iterator().next());while(cache.history.size()>24)cache.history.remove(cache.history.keySet().iterator().next());return snapshot;
        }
    }
    private WorkspaceResultsService.Snapshot cached(String slug,String frameId,HttpSession session){var c=cache(session);synchronized(c){return c.history.values().stream().filter(f->f.slug().equals(slug)&&f.id().equals(frameId)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"La carga ya no está en esta sesión. Abre Resultados y vuelve a intentarlo."));}}
    @GetMapping("/datos") @ResponseBody
    Map<String,Object> data(@PathVariable String slug,@RequestParam String version,@RequestParam(required=false) List<String> versionId,@RequestParam(defaultValue="false") boolean allVersions,
            @RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String until,@RequestParam(defaultValue="") String cutId,@RequestParam(defaultValue="false") boolean refresh,
            Principal actor,HttpSession session,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var f=frame(slug,version,versionId,allVersions,from,until,cutId,refresh,actor.getName(),session);
        var context=new Context(Locale.forLanguageTag("es"),Map.of("result",f.result()));
        return Map.of("frameId",f.id(),"cutoff",f.cutoff(),"result",f.result(),"html",templates.process("fragments/workspace-result-questions",Set.of("questions"),context));
    }
    @GetMapping("/respuestas-pregunta") @ResponseBody
    Map<String,Object> questionAnswers(@PathVariable String slug,@RequestParam String frameId,@RequestParam String questionId,@RequestParam(required=false) String keywordRules,@RequestParam(defaultValue="false") boolean sensitiveAcknowledged,Principal actor,HttpSession session,HttpServletResponse response){response.setHeader("Cache-Control","no-store");var frame=cached(slug,frameId,session);return keywordRules==null?results.questionAnswers(frame,questionId,actor.getName(),sensitiveAcknowledged):results.questionAnswers(frame,questionId,actor.getName(),keywordRules,sensitiveAcknowledged);}
    @GetMapping("/sugerencias")
    String suggestions(@PathVariable String slug,@RequestParam String frameId,@RequestParam String questionId,@RequestParam(defaultValue="false") boolean sensitiveAcknowledged,Principal actor,HttpSession session,HttpServletResponse response,Model model){
        response.setHeader("Cache-Control","no-store");var frame=cached(slug,frameId,session);var answers=results.questionAnswers(frame,questionId,actor.getName(),sensitiveAcknowledged);if(!Boolean.TRUE.equals(answers.get("groupable")))throw new IllegalArgumentException(Boolean.TRUE.equals(answers.get("sensitive"))?"Confirma la advertencia de datos sensibles para esta pregunta.":"Esta pregunta no admite análisis de texto.");model.addAttribute("sensitiveAcknowledged",sensitiveAcknowledged);model.addAttribute("sensitiveContext",answers.get("sensitive"));model.addAttribute("dictionaryOnly",false);model.addAttribute("dictionaryAction","/admin/espacio/"+slug+"/resultados/sugerencias/guardar");model.addAttribute("answers",answers);var view=dictionaries.editor(frame.logical(),questionId,"");model.addAttribute("dictionary",new TextDictionaryService.Saved(((Number)view.get("revision")).intValue(),(String)view.get("rules")));model.addAttribute("scopedDictionary",true);model.addAttribute("dictionaryScope",view.get("dictionaryScope"));model.addAttribute("contextRevision",view.get("contextRevision"));model.addAttribute("frameId",frameId);model.addAttribute("questionId",questionId);model.addAttribute("slug",slug);return "text-suggestions";
    }
    @PostMapping("/sugerencias/{operation}") @ResponseBody
    @SuppressWarnings("unchecked")
    Map<String,Object> manage(@PathVariable String slug,@PathVariable String operation,@RequestParam String frameId,@RequestParam String questionId,@RequestParam(name="rules",defaultValue="") String rawRules,@RequestParam(defaultValue="0") int expectedRevision,@RequestParam(defaultValue="") String term,@RequestParam(defaultValue="") String concept,@RequestParam(defaultValue="") String incoming,@RequestParam(defaultValue="GLOBAL") String dictionaryScope,@RequestParam(defaultValue="0") int expectedContextRevision,@RequestParam(defaultValue="false") boolean sensitiveAcknowledged,Principal actor,HttpSession session,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var frame=cached(slug,frameId,session);dictionaries.get(frame,questionId);if(Set.of("consultar","buscar","alias").contains(operation))throw new IllegalArgumentException("Las consultas externas están desactivadas. Usa el prompt y pega las equivalencias para validarlas.");String rules=TextMentions.canonicalize(rawRules);var answers=results.questionAnswers(frame,questionId,actor.getName(),dictionaryScope.equals("GLOBAL")?TextDictionaryService.globalRules(rules):rules,sensitiveAcknowledged);if(!Boolean.TRUE.equals(answers.get("groupable")))throw new IllegalArgumentException(Boolean.TRUE.equals(answers.get("sensitive"))?"Confirma la advertencia de datos sensibles para esta pregunta.":"Esta pregunta no admite análisis de texto.");
        var rows=(List<Map<String,Object>>)answers.get("rows");var texts=rows.stream().filter(r->"CON_VALOR".equals(r.get("state"))).map(r->r.get("value").toString()).toList();
        return switch(operation){case "scope"->dictionaries.editor(frame.logical(),questionId,dictionaryScope);case "extraer"->{var terms=TextDictionaryService.classify(TextDictionaryService.extract(texts),(String)dictionaries.editor(frame.logical(),questionId,dictionaryScope).get("rules"));yield Map.of("terms",terms,"base",texts.size());}
            case "alimentar"->dictionaries.scopedImport(frame.logical(),questionId,dictionaryScope,rawRules,incoming);
            case "guardar"->{if(!"true".equals(term))throw new IllegalArgumentException("Confirma que revisaste las agrupaciones y sus respuestas.");yield dictionaries.saveScoped(frame.logical(),questionId,dictionaryScope,expectedRevision,expectedContextRevision,rules,actor.getName());}
            case "previsualizar"->{var reviewed=rows.stream().filter(r->"CON_VALOR".equals(r.get("state"))).map(r->Map.of("text",r.get("value"),"matches",TextMentions.highlights(r.get("value").toString(),dictionaryScope.equals("GLOBAL")?TextDictionaryService.globalRules(rules):rules))).toList();yield Map.of("rules",rules,"keywords",answers.get("keywords"),"rows",reviewed);}
            default->throw new IllegalArgumentException("Operación de sugerencias no válida.");};
    }
    @GetMapping("/pregunta")
    String questionPage(@PathVariable String slug,@RequestParam String frameId,@RequestParam String questionId,Principal actor,HttpSession session,HttpServletResponse response,Model model){
        response.setHeader("Cache-Control","no-store");var snapshot=cached(slug,frameId,session);model.addAttribute("answers",results.questionAnswers(snapshot,questionId,actor.getName(),true));model.addAttribute("back","/admin/espacio/"+slug+"/resultados");return "workspace-question-answers";
    }
    @GetMapping("/cortes/nuevo")
    String newCut(@PathVariable String slug,@RequestParam String version,@RequestParam(required=false) List<String> versionId,@RequestParam(defaultValue="false") boolean allVersions,
                  @RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String until,Model model,HttpServletResponse response){
        var w=workspace(slug,version);selection(w,versionId,allVersions);context(w,model,response);model.addAttribute("resultsMode","cut-new");model.addAttribute("selectedVersions",selection(w,versionId,allVersions));model.addAttribute("allVersions",allVersions);model.addAttribute("resultsFrom",from);model.addAttribute("resultsUntil",until);return "workspace-results";
    }
    @GetMapping("/cortes/{id}")
    String cut(@PathVariable String slug,@PathVariable String id,Model model,HttpServletResponse response){var cut=results.cut(slug,id);var w=workspace(slug,publications.get(cut.snapshot().versionIds().getFirst()).code());context(w,model,response);model.addAttribute("resultsMode","cut-saved");model.addAttribute("resultCut",cut);model.addAttribute("cutState",results.cutState(slug,id));model.addAttribute("selectedVersions",cut.snapshot().versionIds());model.addAttribute("resultsFrom",cut.snapshot().from());model.addAttribute("resultsUntil",cut.snapshot().until());return "workspace-results";}
    private void context(SurveyWorkspaceService.Workspace w,Model model,HttpServletResponse response){response.setHeader("Cache-Control","no-store");model.addAttribute("workspace",w);model.addAttribute("workspaceSection","resultados");model.addAttribute("publication",publications.get(w.selected().id()));model.addAttribute("resultCuts",results.cuts(w.slug()));}
    @PostMapping("/cortes") @ResponseBody
    Map<String,String> save(@PathVariable String slug,@RequestParam String frameId,@RequestParam(defaultValue="") String name,Principal actor,HttpSession session,HttpServletResponse response){response.setHeader("Cache-Control","no-store");String id=results.save(cached(slug,frameId,session),name,actor.getName());return Map.of("url","/admin/espacio/"+slug+"/resultados/cortes/"+id);}
    @PostMapping("/cortes/{id}/eliminar")
    String delete(@PathVariable String slug,@PathVariable String id,@RequestParam String expectedHash,@RequestParam(defaultValue="false") boolean confirm,Principal actor){if(!confirm)throw new IllegalArgumentException("Confirma la eliminación del corte y los productos indicados.");results.deleteCut(slug,id,expectedHash,actor.getName());return "redirect:/admin/espacio/"+slug+"/resultados";}
    @PostMapping("/reporte-total")
    @SuppressWarnings("unchecked")
    String report(@PathVariable String slug,@RequestParam String version,@RequestParam(defaultValue="") String frameId,@RequestParam(defaultValue="{}") String representations,Principal actor,HttpSession session,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var f=frameId.isBlank()?frame(slug,version,null,false,"","","",false,actor.getName(),session):cached(slug,frameId,session);
        if(representations.length()>131072)throw new IllegalArgumentException("Demasiadas representaciones.");Map<?,?> prefs;try{prefs=CanonicalJson.read(representations,Map.class);}catch(RuntimeException ex){throw new IllegalArgumentException("Representaciones no válidas.");}if(prefs==null)throw new IllegalArgumentException("Representaciones no válidas.");
        var defaults=ReportCustomization.defaults(Map.of("id",f.id(),"result",f.result()),List.of());
        var questions=defaults.questions().stream().map(q->{Object raw=prefs.get(q.id());List<String> styles=raw==null?List.of("table","columns"):raw instanceof List<?> values&&values.stream().allMatch(String.class::isInstance)?(List<String>)values:null;if(styles==null)throw new IllegalArgumentException("Representaciones no válidas.");return new ReportCustomization.Question(q.id(),!styles.isEmpty(),q.title(),q.comment(),styles);}).toList();
        var candidate=new ReportCustomization.Configuration(f.id(),defaults.title(),"",true,true,true,"",questions,List.of());ReportCustomization.validate(candidate,Map.of("id",f.id(),"result",f.result()));
        String analysisId=results.materialize(f,actor.getName());var config=new ReportCustomization.Configuration(analysisId,candidate.title(),candidate.introduction(),candidate.context(),candidate.tables(),candidate.charts(),candidate.comment(),candidate.questions(),candidate.crossIds());
        synchronized(session){var previous=session.getAttribute("workspaceReportPreferences");var saved=previous instanceof Map<?,?>?new LinkedHashMap<>((Map<String,ReportCustomization.Configuration>)previous):new LinkedHashMap<String,ReportCustomization.Configuration>();saved.put(analysisId,config);while(saved.size()>12)saved.remove(saved.keySet().iterator().next());session.setAttribute("workspaceReportPreferences",saved);}
        return "redirect:/admin/reportes/nuevo?analysisId="+analysisId;
    }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class,java.time.DateTimeException.class,ResponseStatusException.class,org.springframework.dao.DataAccessException.class})
    Object error(Exception ex,HttpServletRequest request,HttpServletResponse response,Model model){int status=ex instanceof ResponseStatusException r?r.getStatusCode().value():ex instanceof IllegalStateException||ex instanceof org.springframework.dao.DataAccessException?409:400;String message=ex instanceof ResponseStatusException r?r.getReason():ex instanceof org.springframework.dao.DataAccessException?"No se pudo completar la operación de forma consistente. Los datos se conservan; vuelve a intentar.":ex.getMessage();response.setHeader("Cache-Control","no-store");
        if(ex instanceof WikidataTerms.RateLimited limited){response.setHeader("Retry-After",String.valueOf(limited.seconds()));return ResponseEntity.status(429).body(Map.of("message",limited.getMessage(),"retrySeconds",limited.seconds()));}
        if(request.getRequestURI().contains("/sugerencias/")||request.getRequestURI().endsWith("/datos")||request.getRequestURI().endsWith("/respuestas-pregunta")||request.getRequestURI().endsWith("/cortes"))return ResponseEntity.status(status).body(Map.of("message",Objects.toString(message,"No se pudo completar la operación.")));
        response.setStatus(status);model.addAttribute("message",message);model.addAttribute("back","/admin/espacio");return "survey-editor-error";
    }
}

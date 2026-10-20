package bo.edu.umss.mercado.web;
import bo.edu.umss.mercado.statistics.*;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

@Controller @RequestMapping("/admin/diccionario")
public class DictionaryController {
    private final TextDictionaryService dictionaries;
    private final bo.edu.umss.mercado.reporting.ReportService reports;
    public DictionaryController(TextDictionaryService dictionaries,bo.edu.umss.mercado.reporting.ReportService reports){this.dictionaries=dictionaries;this.reports=reports;}
    @GetMapping String page(@RequestParam(defaultValue="") String analysisId,@RequestParam(defaultValue="") String questionId,@RequestParam(defaultValue="false") boolean sensitiveAcknowledged,Model model,HttpServletResponse response){
        if(dictionaries.recovery()!=null)return recoveryPage(model,response);
        response.setHeader("Cache-Control","no-store");model.addAttribute("dictionary",dictionaries.global());model.addAttribute("dictionaryOnly",true);model.addAttribute("dictionaryAction","/admin/diccionario/guardar");model.addAttribute("slug","");model.addAttribute("answers",Map.of("label","Alimentar diccionario global"));
        if(!analysisId.isBlank()){
            var logical=reports.dictionaryLogical(analysisId,questionId,sensitiveAcknowledged);var context=reports.dictionaryContext(analysisId,questionId,sensitiveAcknowledged);var view=dictionaries.editor(logical,questionId,"");
            model.addAttribute("answers",context);model.addAttribute("sensitiveContext",context.get("sensitive"));model.addAttribute("initialTerms",bo.edu.umss.mercado.data.CanonicalJson.json(context.get("terms")));
            model.addAttribute("dictionary",new TextDictionaryService.Saved(((Number)view.get("revision")).intValue(),(String)view.get("rules")));model.addAttribute("scopedDictionary",true);model.addAttribute("dictionaryScope",view.get("dictionaryScope"));model.addAttribute("contextRevision",view.get("contextRevision"));
            model.addAttribute("analysisId",analysisId);model.addAttribute("questionId",questionId);model.addAttribute("sensitiveAcknowledged",sensitiveAcknowledged);
        }
        return "text-suggestions";
    }
    @GetMapping("/recuperar")
    String recoveryPage(Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var recovery=dictionaries.recovery();
        if(recovery==null)return "redirect:/admin/diccionario";
        model.addAttribute("recovery",recovery);return "dictionary-recovery";
    }
    @PostMapping("/recuperar")
    String recover(@RequestParam String snapshot,@RequestParam(defaultValue="false") boolean confirmed,Principal actor,Model model,HttpServletResponse response){
        try{dictionaries.recover(snapshot,confirmed,actor.getName());return "redirect:/admin/diccionario";}
        catch(IllegalArgumentException|IllegalStateException ex){response.setStatus(ex instanceof IllegalStateException?409:400);model.addAttribute("error",ex.getMessage());return recoveryPage(model,response);}
    }
    @PostMapping("/{operation}") @ResponseBody
    Map<String,Object> action(@PathVariable String operation,@RequestParam(defaultValue="") String rules,@RequestParam(defaultValue="") String incoming,@RequestParam(defaultValue="0") int expectedRevision,@RequestParam(defaultValue="false") boolean reviewed,@RequestParam(defaultValue="") String analysisId,@RequestParam(defaultValue="") String questionId,@RequestParam(defaultValue="false") boolean sensitiveAcknowledged,@RequestParam(defaultValue="GLOBAL") String dictionaryScope,@RequestParam(defaultValue="0") int expectedContextRevision,Principal actor,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");var logical=analysisId.isBlank()?null:reports.dictionaryLogical(analysisId,questionId,sensitiveAcknowledged);
        if(logical==null&&!dictionaryScope.equals("GLOBAL"))throw new IllegalArgumentException("El diccionario privado requiere una pregunta concreta.");
        return switch(operation){
            case "scope"->{if(logical==null)throw new IllegalArgumentException("Selecciona una pregunta.");yield dictionaries.editor(logical,questionId,dictionaryScope);}
            case "alimentar"->logical==null?dictionaries.importPreview(rules,incoming):dictionaries.scopedImport(logical,questionId,dictionaryScope,rules,incoming);
            case "previsualizar"->Map.of("rules",TextMentions.canonicalize(rules),"groups",TextMentions.parse(TextMentions.canonicalize(rules)).size());
            case "guardar"->{if(!reviewed)throw new IllegalArgumentException("Confirma que revisaste los cambios.");if(logical!=null)yield dictionaries.saveScoped(logical,questionId,dictionaryScope,expectedRevision,expectedContextRevision,rules,actor.getName());var saved=dictionaries.saveGlobal(expectedRevision,rules,actor.getName());yield Map.of("rules",saved.rules(),"revision",saved.revision());}
            default->throw new IllegalArgumentException("Operación no disponible.");
        };
    }
    @ExceptionHandler({IllegalArgumentException.class,IllegalStateException.class}) @ResponseBody ResponseEntity<?> error(RuntimeException ex){return ResponseEntity.status(ex instanceof IllegalStateException?409:400).header("Cache-Control","no-store").body(Map.of("message",ex.getMessage()));}
}

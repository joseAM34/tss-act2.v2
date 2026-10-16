package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.survey.SurveyWorkspaceService;
import bo.edu.umss.mercado.statistics.LiveResultsService;
import jakarta.servlet.http.HttpServletResponse;
import java.time.LocalDate;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.context.Context;

@Controller
@RequestMapping("/admin/espacio/{slug}/resultados")
public class LiveResultsController {
    private final SurveyWorkspaceService spaces;
    private final LiveResultsService live;
    private final SpringTemplateEngine templates;
    public LiveResultsController(SurveyWorkspaceService spaces,LiveResultsService live,SpringTemplateEngine templates) {this.spaces=spaces;this.live=live;this.templates=templates;}
    private String versionId(String slug,String version) {
        var w=spaces.open(slug,version);
        if(w.legacy() || w.selected().draft())throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"Esta vista requiere una versión publicada desde el editor. Los originales UMSS conservan el protocolo del estudio.");
        return w.selected().id();
    }
    static LiveResultsService.Selection selection(String origin,String from,String until,String row,String column) {
        return new LiveResultsService.Selection(origin,from.isBlank()?null:LocalDate.parse(from),until.isBlank()?null:LocalDate.parse(until),row,column);
    }
    @GetMapping("/actuales") @ResponseBody
    Map<String,Object> current(@PathVariable String slug,@RequestParam String version,@RequestParam(defaultValue="NO_DECLARADO") String origin,
            @RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String until,@RequestParam(defaultValue="") String row,@RequestParam(defaultValue="") String column,
            Principal actor,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        var frame=live.current(versionId(slug,version),selection(origin,from,until,row,column),actor.getName());
        var context=new Context(Locale.forLanguageTag("es"),Map.of("frame",frame));
        return Map.of("frame",frame,"html",templates.process("fragments/live-results",Set.of("content"),context));
    }
    @PostMapping("/guardar") @ResponseBody
    LiveResultsService.Saved save(@PathVariable String slug,@RequestParam String version,@RequestParam String expectedHash,@RequestParam String expectedCutoff,
            @RequestParam(defaultValue="NO_DECLARADO") String origin,@RequestParam(defaultValue="") String from,@RequestParam(defaultValue="") String until,
            @RequestParam(defaultValue="") String row,@RequestParam(defaultValue="") String column,Principal actor,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return live.save(versionId(slug,version),selection(origin,from,until,row,column),expectedHash,expectedCutoff,actor.getName());
    }
    @ExceptionHandler({IllegalArgumentException.class,java.time.DateTimeException.class,IllegalStateException.class,ResponseStatusException.class,org.springframework.dao.DataAccessException.class}) @ResponseBody
    ResponseEntity<Map<String,String>> problem(Exception ex) {
        var status=ex instanceof ResponseStatusException r?r.getStatusCode():ex instanceof IllegalStateException?HttpStatus.INTERNAL_SERVER_ERROR:ex instanceof IllegalArgumentException || ex instanceof java.time.DateTimeException?HttpStatus.BAD_REQUEST:HttpStatus.CONFLICT;
        String message=ex instanceof ResponseStatusException r?r.getReason():ex instanceof IllegalArgumentException?ex.getMessage():"No se pudo completar o verificar la consulta. Conserva el último corte y vuelve a intentar.";
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(Map.of("message",message==null?"No se pudo actualizar.":message));
    }
}

package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.GenericAnalysisService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/analisis-general")
public class GenericAnalysisController {
    private final GenericAnalysisService service;
    public GenericAnalysisController(GenericAnalysisService service) { this.service = service; }
    @GetMapping
    String index(Model model, HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); model.addAttribute("analyses", service.recent()); return "generic-analyses"; }
    @PostMapping
    String create(@RequestParam String preparationId, Principal actor) { return "redirect:/admin/analisis-general/" + service.create(preparationId, actor.getName()); }
    @GetMapping("/{id}")
    String detail(@PathVariable String id, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); var analysis = service.get(id); model.addAttribute("analysis", analysis); model.addAttribute("result", analysis.get("result")); return "generic-analysis";
    }
    @GetMapping("/{id}/resultado.json")
    void json(@PathVariable String id, HttpServletResponse response) throws IOException {
        var analysis = service.get(id); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control", "no-store"); response.getWriter().write(CanonicalJson.json(analysis));
    }
}

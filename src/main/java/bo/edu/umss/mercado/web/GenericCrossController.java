package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.GenericCrossService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/admin/cruces-generales")
public class GenericCrossController {
    private final GenericCrossService service;
    public GenericCrossController(GenericCrossService service) { this.service = service; }
    @GetMapping
    String index(@RequestParam(defaultValue = "") String preparationId, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); model.addAttribute("crosses", service.recent()); model.addAttribute("preparationId", preparationId);
        if (!preparationId.isBlank()) { model.addAttribute("crosses",service.forPreparation(preparationId));model.addAttribute("choices", service.choices(preparationId)); model.addAttribute("source", service.source(preparationId)); }
        return "generic-crosses";
    }
    @PostMapping
    String create(@RequestParam String preparationId, @RequestParam String rowQuestion, @RequestParam String columnQuestion,
                  @RequestParam(defaultValue = "0.05") double alpha, @RequestParam(defaultValue = "false") boolean independenceDocumented,
                  @RequestParam(defaultValue = "") String unit, @RequestParam(defaultValue = "") String evidence,
                  @RequestParam(defaultValue = "false") boolean structuralZeros, Principal actor, Model model, HttpServletResponse response) {
        try { return "redirect:/admin/cruces-generales/" + service.create(preparationId, new GenericCrossService.Spec(rowQuestion, columnQuestion, alpha, independenceDocumented, unit.strip(), evidence.strip(), structuralZeros), actor.getName()); }
        catch (IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error", ex.getMessage()); return index(preparationId, model, response); }
    }
    @GetMapping("/{id}")
    String detail(@PathVariable String id, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); var cross = service.get(id); model.addAttribute("cross", cross); model.addAttribute("result", cross.get("result"));
        var result = (java.util.Map<?, ?>) cross.get("result"); var table = (java.util.Map<?, ?>) result.get("table");
        model.addAttribute("applicability", switch (table.get("status").toString()) {
            case "CALCULADO" -> "Prueba ejecutada con los requisitos registrados.";
            case "SIN_DATOS" -> "No hay pares con valor para calcular el cruce.";
            case "SIN_VARIACION" -> "Se requieren al menos dos categorías con observaciones en cada pregunta.";
            case "INDEPENDENCIA_NO_DOCUMENTADA" -> "Falta documentar la unidad de análisis y la evidencia de independencia.";
            case "ESTRUCTURA_INCOMPATIBLE" -> "Las combinaciones imposibles declaradas requieren otro modelo estadístico.";
            case "FRECUENCIAS_INSUFICIENTES" -> "Al menos una frecuencia esperada activa es menor de 5.";
            default -> "No se pudo obtener un resultado estadístico válido. Consulta el diagnóstico.";
        }); return "generic-cross";
    }
    @GetMapping("/{id}/resultado.json")
    void json(@PathVariable String id, HttpServletResponse response) throws IOException {
        var cross = service.get(id); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control", "no-store"); response.getWriter().write(CanonicalJson.json(cross));
    }
}

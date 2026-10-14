package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.statistics.AnalysisService;
import bo.edu.umss.mercado.visualization.DashboardDataService;
import bo.edu.umss.mercado.visualization.DashboardProjection;
import bo.edu.umss.mercado.visualization.DashboardSelection;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

/** Las rutas /admin/** requieren ADMIN; no se publican resultados en assets. */
@Controller
public class DashboardController {
    private final AnalysisService analyses;
    private final DashboardDataService data;
    private final DashboardProjection projection;
    public DashboardController(AnalysisService analyses, DashboardDataService data, DashboardProjection projection) {
        this.analyses = analyses; this.data = data; this.projection = projection;
    }

    @GetMapping("/admin/estadisticas/{id}/visualizacion")
    String view(@PathVariable String id, Model model, HttpServletResponse response) {
        analyses.detail(id);
        response.setHeader("Cache-Control", "no-store");
        model.addAttribute("analysisId", id);
        return "dashboard";
    }

    @GetMapping("/admin/estadisticas/{id}/visualizacion.json")
    @ResponseBody
    Map<String, Object> results(@PathVariable String id, @RequestParam Map<String, String> filters, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return projection.project(data.data(id, DashboardSelection.from(filters)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(Map.of("error", ex.getMessage()));
    }
}

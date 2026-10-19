package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.importing.ImportCatalogService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/importaciones/{id}")
public class ImportCatalogController {
    private final ImportCatalogService catalog;
    public ImportCatalogController(ImportCatalogService catalog){this.catalog=catalog;}
    @PostMapping("/ampliar-catalogo")
    String prepare(@PathVariable String id,@RequestParam String revisionId,@RequestParam String previewHash,
                   @RequestParam String questionId,@RequestParam MultiValueMap<String,String> values,
                   Principal actor,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        return "redirect:/admin/encuestas/"+catalog.prepare(id,revisionId,previewHash,questionId,values.getOrDefault("labels",List.of()),actor.getName());
    }
    @PostMapping("/reutilizar")
    String reuse(@PathVariable String id,@RequestParam String versionId,Principal actor,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        return "redirect:/admin/importaciones/"+catalog.reuse(id,versionId,actor.getName());
    }
    @ExceptionHandler({IllegalArgumentException.class,ResponseStatusException.class})
    String problem(Exception ex,Model model,HttpServletResponse response){
        response.setStatus(ex instanceof ResponseStatusException r?r.getStatusCode().value():400);
        response.setHeader("Cache-Control","no-store");
        model.addAttribute("message",ex instanceof ResponseStatusException r?r.getReason():ex.getMessage());
        model.addAttribute("back","/admin/importaciones");return "survey-editor-error";
    }
}

package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.survey.SurveyManagementService;
import bo.edu.umss.mercado.survey.SurveyDeletionService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/espacio/{slug}/gestion")
public class SurveyManagementController {
    private final SurveyManagementService surveys;
    private final SurveyDeletionService deletions;
    public SurveyManagementController(SurveyManagementService surveys, SurveyDeletionService deletions) {
        this.surveys = surveys; this.deletions = deletions;
    }

    @GetMapping
    String view(@PathVariable String slug, Model model, HttpServletResponse response, Principal actor) {
        response.setHeader("Cache-Control", "no-store");
        var state = surveys.get(slug);
        model.addAttribute("surveyManagement", state);
        model.addAttribute("backupReceipt", deletions.latest(slug, actor.getName(), state.hash()).orElse(null));
        return "survey-management";
    }

    @PostMapping("/archivar")
    String archive(@PathVariable String slug, @RequestParam String expectedHash, Principal actor) {
        surveys.archive(slug, expectedHash, actor.getName()); return "redirect:/admin/espacio?archivadas=true";
    }

    @PostMapping("/restaurar")
    String restore(@PathVariable String slug, @RequestParam String expectedHash, Principal actor) {
        surveys.restore(slug, expectedHash, actor.getName()); return "redirect:/admin/espacio/" + slug;
    }

    @PostMapping("/eliminar")
    String delete(@PathVariable String slug, @RequestParam String expectedHash, @RequestParam(defaultValue = "false") boolean confirm,
                  Principal actor) {
        if (!confirm) throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Marca la confirmación para eliminar esta encuesta sin datos.");
        surveys.delete(slug, expectedHash, actor.getName()); return "redirect:/admin/espacio";
    }

    @PostMapping("/respaldo.zip")
    ResponseEntity<byte[]> backup(@PathVariable String slug, @RequestParam String expectedHash, Principal actor) {
        var backup = deletions.prepare(slug, expectedHash, actor.getName());
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(backup.receipt().filename()).build().toString())
                .header("X-Survey-Backup-Id", backup.receipt().id())
                .header("X-Survey-Backup-SHA256", backup.hash())
                .body(backup.bytes());
    }

    @PostMapping("/eliminar-con-respaldo")
    String deleteWithBackup(@PathVariable String slug, @RequestParam String expectedHash, @RequestParam String backupId,
                            @RequestParam(defaultValue = "false") boolean confirm, Principal actor, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (!confirm) throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Confirma que guardaste el respaldo y quieres eliminar definitivamente esta encuesta y sus datos.");
        deletions.delete(slug, backupId, expectedHash, actor.getName());
        return "redirect:/admin/espacio";
    }

    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException ex, HttpServletResponse response, Model model) {
        response.setStatus(ex.getStatusCode().value()); response.setHeader("Cache-Control", "no-store");
        model.addAttribute("message", ex.getReason()); model.addAttribute("back", "/admin/espacio"); return "survey-editor-error";
    }
}

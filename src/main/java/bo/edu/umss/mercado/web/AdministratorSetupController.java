package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.security.AdministratorService;
import bo.edu.umss.mercado.security.AdministratorSetupAccess;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class AdministratorSetupController {
    private final AdministratorService administrators;
    private final AdministratorSetupAccess setupAccess;

    public AdministratorSetupController(AdministratorService administrators, AdministratorSetupAccess setupAccess) {
        this.administrators = administrators;
        this.setupAccess = setupAccess;
    }

    @GetMapping("/configuracion-inicial")
    String setup(HttpServletRequest request, Model model) {
        setupAccess.requireAllowed(request);
        if (administrators.exists()) return "redirect:/login";
        model.addAttribute("username", "admin");
        model.addAttribute("errors", Map.of());
        return "setup";
    }

    @PostMapping("/configuracion-inicial")
    String create(@RequestParam(defaultValue = "") String username,
                  @RequestParam(defaultValue = "") String password,
                  @RequestParam(defaultValue = "") String confirmation,
                  HttpServletRequest request, Model model) {
        setupAccess.requireAllowed(request);
        if (administrators.exists()) throw new ResponseStatusException(HttpStatus.CONFLICT);
        Map<String, String> errors = administrators.validate(username, password, confirmation);
        if (!errors.isEmpty()) {
            model.addAttribute("username", username);
            model.addAttribute("errors", errors);
            return "setup";
        }
        try {
            administrators.register(username, password);
        } catch (DuplicateKeyException | AdministratorService.SetupAlreadyCompletedException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return "redirect:/login?created";
    }
}

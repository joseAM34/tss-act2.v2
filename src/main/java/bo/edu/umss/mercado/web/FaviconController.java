package bo.edu.umss.mercado.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Browsers may request this conventional path even without an explicit icon link. */
@Controller
public class FaviconController {
    @GetMapping("/favicon.ico")
    String icon() {
        return "redirect:/favicon.svg";
    }
}

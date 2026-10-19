package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.survey.PredefinedSurveyService;
import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/encuestas/predefinidas")
public class PredefinedSurveyController {
    private final PredefinedSurveyService service;
    public PredefinedSurveyController(PredefinedSurveyService service){this.service=service;}
    @GetMapping
    String index(Model model,HttpServletResponse response){response.setHeader("Cache-Control","no-store");model.addAttribute("templates",service.templates());return "survey-predefined";}
    @PostMapping
    String install(@RequestParam(defaultValue="") List<String> keys,Principal actor,Model model,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        try{model.addAttribute("installed",service.install(keys,actor.getName()));return index(model,response);}
        catch(IllegalArgumentException ex){response.setStatus(400);model.addAttribute("error",ex.getMessage());return index(model,response);}
    }
}

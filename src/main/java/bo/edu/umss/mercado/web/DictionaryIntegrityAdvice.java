package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.statistics.DictionaryIntegrityException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice(basePackages="bo.edu.umss.mercado.web")
public class DictionaryIntegrityAdvice {
    @ExceptionHandler(DictionaryIntegrityException.class)
    Object recover(DictionaryIntegrityException ex,HttpServletRequest request,HttpServletResponse response){
        response.setHeader("Cache-Control","no-store");
        String accept=request.getHeader("Accept");
        if(accept!=null&&accept.contains("application/json")){
            return ResponseEntity.status(409).body(Map.of("message",ex.getMessage(),"recoveryUrl",request.getContextPath()+"/admin/diccionario/recuperar"));
        }
        return new ModelAndView("redirect:/admin/diccionario/recuperar");
    }
}

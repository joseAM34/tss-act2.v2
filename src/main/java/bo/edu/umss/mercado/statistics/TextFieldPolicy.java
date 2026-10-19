package bo.edu.umss.mercado.statistics;

import java.util.Set;

public final class TextFieldPolicy {
    private TextFieldPolicy(){}
    public static boolean sensitive(Object type,Object label){
        String text=TextMentions.normalize(String.valueOf(label));
        if(Set.of("email","tel").contains(type))return true;
        if(text.matches(".*\\bnombres? (del?|de la|de los|de las) (programa|curso|posgrado|maestria|doctorado|asignatura|materia|herramienta|software|tecnologia|sistema|plataforma|certificacion)\\b.*"))return false;
        return text.matches(".*(nombre|correo|email|telefono|contacto|direccion).*");
    }
}

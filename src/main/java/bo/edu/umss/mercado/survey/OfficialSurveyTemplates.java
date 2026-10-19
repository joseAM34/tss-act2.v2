package bo.edu.umss.mercado.survey;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Recetas de estructura para crear encuestas ordinarias en la base de datos.
 * No cargan respuestas, no publican al iniciar y no conceden aprobación institucional.
 */
@Component
public class OfficialSurveyTemplates {
    public record Template(String key, String title, String description, String version,
                           List<Question> questions, Map<String,String> reviewNotes) {
        public Template(String key,String title,String description,String version,List<Question> questions){this(key,title,description,version,questions,Map.of());}
        public Template {
            questions = List.copyOf(questions);
            reviewNotes = Map.copyOf(reviewNotes);
        }
    }

    private final List<Template> templates;

    public OfficialSurveyTemplates() {
        try {
            String json = new ClassPathResource("/surveys/official-templates.json", OfficialSurveyTemplates.class)
                    .getContentAsString(StandardCharsets.UTF_8);
            templates = List.copyOf(Arrays.asList(CanonicalJson.read(json, Template[].class)));
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar la estructura de las encuestas predefinidas.", exception);
        }
    }

    public List<Template> all() {
        return templates;
    }

    public Template get(String key) {
        return templates.stream().filter(template -> template.key().equals(key)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Encuesta predefinida no encontrada."));
    }
}

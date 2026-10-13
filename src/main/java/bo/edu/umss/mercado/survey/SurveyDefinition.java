package bo.edu.umss.mercado.survey;

import java.util.List;

public record SurveyDefinition(String slug, String title, String description,
                               String version, List<Question> questions) {
    public SurveyDefinition {
        questions = List.copyOf(questions);
    }
}

package bo.edu.umss.mercado.survey;

import java.util.List;
import java.util.Map;

public record ValidationResult(Map<String, List<String>> answers, Map<String, String> errors,
                               Map<String, AnswerState> states) {
    public ValidationResult {
        answers = Map.copyOf(answers);
        errors = Map.copyOf(errors);
        states = Map.copyOf(states);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}

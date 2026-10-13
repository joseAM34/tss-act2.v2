package bo.edu.umss.mercado.survey;

import java.util.List;
import java.util.Map;

public record ValidationResult(Map<String, List<String>> answers, Map<String, String> errors) {
    public boolean valid() {
        return errors.isEmpty();
    }
}

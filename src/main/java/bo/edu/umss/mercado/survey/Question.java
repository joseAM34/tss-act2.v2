package bo.edu.umss.mercado.survey;

import java.util.List;
import java.util.Map;
import java.util.Arrays;

/** Catálogo versionado del instrumento; los códigos se almacenan como datos originales. */
public record Question(String id, String section, String label, String type, boolean required,
                       List<Option> options, String dependsOn, String dependsValue,
                       int minSelections, int maxSelections, String hint,
                       Integer min, Integer max) {
    public record Option(String value, String label) {}

    public Question {
        options = List.copyOf(options);
    }

    /** Una condición puede admitir varios códigos de la pregunta anterior, separados por |. */
    public boolean applicable(Map<String, List<String>> answers) {
        return dependsOn == null || Arrays.stream(dependsValue.split("\\|"))
                .anyMatch(answers.getOrDefault(dependsOn, List.of())::contains);
    }
}

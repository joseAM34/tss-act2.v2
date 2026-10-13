package bo.edu.umss.mercado.survey;

import java.util.List;

/** Catálogo versionado del instrumento; los códigos se almacenan como datos originales. */
public record Question(String id, String section, String label, String type, boolean required,
                       List<Option> options, String dependsOn, String dependsValue,
                       int minSelections, int maxSelections, String hint,
                       Integer min, Integer max) {
    public record Option(String value, String label) {}

    public Question {
        options = List.copyOf(options);
    }
}

package bo.edu.umss.mercado.survey;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

@Component
public class SurveyValidator {
    private static final Set<String> CONTROL_FIELDS = Set.of("_csrf", "formToken", "consent");

    public ValidationResult validate(SurveyDefinition survey, MultiValueMap<String, String> submitted) {
        return validate(survey, submitted, true);
    }

    /** Recepción administrativa histórica: valida respuestas sin afirmar consentimiento del participante. */
    public ValidationResult validateHistorical(SurveyDefinition survey, MultiValueMap<String, String> submitted) {
        if (submitted.containsKey("consent")) throw new IllegalArgumentException("La recepción histórica no declara consentimiento del participante.");
        return validate(survey, submitted, false);
    }

    private ValidationResult validate(SurveyDefinition survey, MultiValueMap<String, String> submitted, boolean requireConsent) {
        var answers = new LinkedHashMap<String, List<String>>();
        var errors = new LinkedHashMap<String, String>();
        var states = new LinkedHashMap<String, AnswerState>();
        Set<String> known = new HashSet<>(CONTROL_FIELDS);
        survey.questions().forEach(q -> known.add(q.id()));
        if (submitted.keySet().stream().anyMatch(key -> !known.contains(key))) {
            errors.put("_form", "Se recibieron campos que no pertenecen a esta encuesta. Recargue e intente nuevamente.");
        }
        if (requireConsent && !List.of("SI").equals(submitted.get("consent"))) {
            errors.put("consent", "Debe aceptar el uso académico de las respuestas para enviarlas.");
        }

        for (Question q : survey.questions()) {
            if (!q.applicable(answers)) {
                states.put(q.id(), AnswerState.NO_APLICA);
                continue;
            }
            states.put(q.id(), AnswerState.SIN_RESPUESTA);
            List<String> values = submitted.getOrDefault(q.id(), List.of()).stream()
                    .filter(v -> v != null && !v.isBlank()).toList();
            if (values.isEmpty()) {
                if (q.required()) errors.put(q.id(), "Responda esta pregunta.");
                continue;
            }
            if ((!q.type().equals("checkbox") && values.size() != 1)
                    || values.size() < q.minSelections() || values.size() > q.maxSelections()) {
                errors.put(q.id(), q.type().equals("checkbox")
                        ? "Seleccione entre " + q.minSelections() + " y " + q.maxSelections() + " opciones."
                        : "Seleccione o escriba una sola respuesta.");
                continue;
            }
            if (new HashSet<>(values).size() != values.size()) {
                errors.put(q.id(), "Cada opción puede seleccionarse una sola vez.");
                continue;
            }
            int maxLength = !requireConsent && Set.of("text","textarea").contains(q.type()) ? 8000 : q.type().equals("textarea") ? 2000 : 1000;
            if (values.stream().anyMatch(v -> v.length() > maxLength || v.indexOf('\0') >= 0)) {
                errors.put(q.id(), "La respuesta debe tener como máximo " + maxLength + " caracteres válidos.");
                continue;
            }
            if (!q.options().isEmpty()) {
                Set<String> allowed = new HashSet<>();
                q.options().forEach(o -> allowed.add(o.value()));
                if (!allowed.containsAll(values)) {
                    errors.put(q.id(), "Seleccione una de las opciones disponibles.");
                    continue;
                }
            }
            String value = values.getFirst();
            String error = switch (q.type()) {
                case "number" -> validateNumber(q, value);
                case "email" -> value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                        ? null : "Ingrese un correo electrónico válido.";
                case "url" -> validUrl(value) ? null : "Ingrese una dirección completa que comience con http:// o https://.";
                case "tel" -> value.matches("^[+()0-9 .-]{5,30}$") && value.replaceAll("[^0-9]", "").length() >= 5
                        ? null : "Ingrese un teléfono válido con su código de país.";
                default -> null;
            };
            if (error != null) errors.put(q.id(), error);
            else {
                answers.put(q.id(), List.copyOf(values));
                states.put(q.id(), AnswerState.CON_VALOR);
            }
        }
        if (survey.slug().equals("titulados") && answers.containsKey("estado_laboral") && answers.containsKey("sector")) {
            boolean unemployed = answers.get("estado_laboral").getFirst().equals("DESEMPLEADO");
            boolean noSector = answers.get("sector").getFirst().equals("NO_APLICA");
            if (unemployed != noSector) {
                errors.put("sector", unemployed
                        ? "Si actualmente no trabaja, seleccione «No desempeño una actividad laboral actualmente»."
                        : "Seleccione el sector de su actividad laboral actual.");
            }
        }
        if (survey.slug().equals("titulados")) {
            if (answers.containsKey("meses_desempleado")) {
                if (!answers.containsKey("meses_vida_profesional")) {
                    errors.put("meses_vida_profesional", "Indique los meses de vida profesional para comprobar el tiempo desempleado.");
                } else if (Integer.parseInt(answers.get("meses_desempleado").getFirst())
                        > Integer.parseInt(answers.get("meses_vida_profesional").getFirst())) {
                    errors.put("meses_desempleado", "El tiempo desempleado no puede superar los meses de vida profesional.");
                }
            }
            if (List.of("SIN_EXPERIENCIA").equals(answers.get("experiencia_laboral"))
                    && answers.containsKey("estado_laboral")
                    && !List.of("DESEMPLEADO").equals(answers.get("estado_laboral"))) {
                errors.put("experiencia_laboral", "Si actualmente trabaja o emprende, indique el tipo de experiencia laboral que tiene.");
            }
        }
        return new ValidationResult(answers, errors, states);
    }

    private String validateNumber(Question q, String value) {
        if (!value.matches("[0-9]{1,9}")) return "Ingrese un número entero sin espacios.";
        int number = Integer.parseInt(value);
        if ((q.min() != null && number < q.min()) || (q.max() != null && number > q.max())) {
            return "Ingrese un valor entre " + q.min() + " y " + q.max() + ".";
        }
        return null;
    }

    private boolean validUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}

package bo.edu.umss.mercado.survey;

/** Aplicabilidad y presencia de una respuesta; no representan puntuaciones. */
public enum AnswerState {
    CON_VALOR("Respondida"),
    SIN_RESPUESTA("Sin respuesta"),
    NO_APLICA("No aplica");

    private final String label;

    AnswerState(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

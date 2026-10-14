package bo.edu.umss.mercado.response;

import bo.edu.umss.mercado.survey.AnswerState;
import java.util.List;

public record AnswerView(String label, List<String> values, AnswerState state) {}

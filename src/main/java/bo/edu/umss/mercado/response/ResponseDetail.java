package bo.edu.umss.mercado.response;

import java.util.List;

public record ResponseDetail(ResponseSummary summary, List<AnswerView> answers) {}

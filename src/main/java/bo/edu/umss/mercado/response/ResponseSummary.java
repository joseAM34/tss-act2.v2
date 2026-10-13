package bo.edu.umss.mercado.response;

public record ResponseSummary(String id, String surveySlug, String surveyTitle,
                              String version, String submittedAt) {}

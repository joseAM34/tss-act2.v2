package bo.edu.umss.mercado.survey;

import java.util.List;
import bo.edu.umss.mercado.survey.DraftSurveyService.Section;
import bo.edu.umss.mercado.survey.DraftSurveyService.Item;

/** Contrato nuevo de publicación. No cambia el formato de los snapshots históricos. */
public record PublishedSurvey(String schemaVersion, String surveyId, String studyId,
                             SurveyDefinition definition, List<Section> sections, List<Item> items,
                             String sourceSlug, String sourceVersion) {
    public PublishedSurvey { sections = List.copyOf(sections); items = List.copyOf(items); }
}

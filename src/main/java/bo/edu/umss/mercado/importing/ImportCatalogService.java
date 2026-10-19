package bo.edu.umss.mercado.importing;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.survey.DraftSurveyService;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Receives observed labels into a draft without rewriting the source publication or file. */
@Service
public class ImportCatalogService {
    public record Proposal(String questionId, String question, List<String> labels) {
        public Proposal { labels = List.copyOf(labels); }
    }

    private final CsvImportService imports;
    private final SurveyPublicationService publications;
    private final DraftSurveyService drafts;

    public ImportCatalogService(CsvImportService imports, SurveyPublicationService publications, DraftSurveyService drafts) {
        this.imports = imports;
        this.publications = publications;
        this.drafts = drafts;
    }

    @Transactional(readOnly = true)
    public List<Proposal> proposals(String batchId, String revisionId) {
        var preview = imports.preview(batchId, revisionId);
        var batch = imports.batch(batchId);
        var mapping = imports.revision(batchId, revisionId).mapping();
        var definition = publications.get(batch.versionId()).content().definition();
        var proposals = new ArrayList<Proposal>();
        for (var question : definition.questions()) {
            if (!question.type().equals("checkbox")) continue;
            var column = mapping.columns().stream().filter(c -> c.questionId().equals(question.id())).findFirst();
            if (column.isEmpty() || column.get().index() < 0) continue;
            var labels = new LinkedHashSet<String>();
            for (var row : preview.rows()) {
                if (!row.errors().containsKey(question.id())) continue;
                labels.addAll(ImportSuggestions.unrecognizedOptions(question, row.cells().get(column.get().index()),
                        mapping.optionMode(), mapping.multipleSeparator()));
                if (labels.size() >= 100) break;
            }
            var bounded = labels.stream().filter(s -> s != null && !s.isBlank() && s.length() <= 1000).limit(100).toList();
            if (!bounded.isEmpty()) proposals.add(new Proposal(question.id(), question.label(), bounded));
        }
        return List.copyOf(proposals);
    }

    @Transactional
    public String prepare(String batchId, String revisionId, String expectedHash, String questionId,
                          List<String> labels, String actor) {
        actor(actor);
        var preview = imports.preview(batchId, revisionId);
        if (!Objects.equals(preview.hash(), expectedHash))
            throw conflict("La vista previa cambió. Revisa las discrepancias antes de preparar la adaptación.");
        var optionMode = imports.revision(batchId, revisionId).mapping().optionMode();
        if (labels == null || labels.isEmpty() || labels.size() > 100 || new HashSet<>(labels).size() != labels.size())
            throw new IllegalArgumentException("Selecciona los valores observados que deseas conservar en el borrador.");
        var proposal = proposals(batchId, revisionId).stream().filter(p -> p.questionId().equals(questionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay valores nuevos reconocibles para esta pregunta."));
        if (!proposal.labels().containsAll(labels))
            throw new IllegalArgumentException("La selección no coincide con los valores observados en esta vista previa.");

        var batch = imports.batch(batchId);
        var source = publications.get(batch.versionId());
        var sourceQuestion = source.content().definition().questions().stream().filter(q -> q.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pregunta de origen no encontrada."));
        String draftId = publications.successor(source.id(), actor);
        var draft = drafts.get(draftId);
        var item = draft.items().stream().filter(i -> i.question().id().equals(questionId)).findFirst()
                .orElseThrow(() -> conflict("La pregunta ya no existe en el borrador. Revisa sus cambios antes de adaptar la importación."));
        var question = item.question();
        if (!draft.surveyId().equals(source.surveyId()) || !question.type().equals(sourceQuestion.type())
                || !question.options().containsAll(sourceQuestion.options()))
            throw conflict("El borrador cambió las opciones originales de esta pregunta. Revisa sus cambios antes de adaptar la importación.");
        var options = new ArrayList<>(question.options());
        for (String label : labels) {
            var existing = ImportSuggestions.matchingSourceOptions(options, label, optionMode);
            if (existing.size() > 1)
                throw conflict("El borrador tiene opciones ambiguas para este valor observado. Revisa las opciones antes de continuar.");
            if (existing.size() == 1) continue;
            String code = "OBS_" + CanonicalJson.hash(label).substring(0, 12);
            if (options.stream().anyMatch(o -> o.value().equals(code)))
                throw conflict("El código del valor observado ya tiene otro texto en el borrador. Revisa las opciones.");
            options.add(new Question.Option(code, label));
        }
        if (options.size() > 100) throw new IllegalArgumentException("La adaptación superaría las 100 opciones por pregunta.");
        if (options.equals(question.options())) return draftId;
        int maximum = question.maxSelections() == question.options().size() ? options.size() : question.maxSelections();
        var updated = new Question(question.id(), question.section(), question.label(), question.type(), question.required(),
                options, question.dependsOn(), question.dependsValue(), question.minSelections(), maximum,
                question.hint(), question.min(), question.max());
        drafts.question(draftId, draft.revision(), item.sectionId(), updated, false, actor);
        return draftId;
    }

    @Transactional
    public String reuse(String batchId, String targetVersion, String actor) {
        actor(actor);
        var batch = imports.batch(batchId);
        var source = publications.get(batch.versionId());
        var target = publications.lockForCapture(targetVersion);
        if (!target.surveyId().equals(source.surveyId()))
            throw new IllegalArgumentException("Selecciona una versión de la misma encuesta para reutilizar este archivo.");
        if (!target.status().equals("PUBLICADA"))
            throw conflict("Publica primero la versión adaptada antes de revisar el archivo en ella.");
        return imports.upload(target.id(), batch.fileName(), imports.original(batchId), batch.delimiter(), batch.platform(), actor);
    }

    private static void actor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 100)
            throw new IllegalArgumentException("El administrador no es válido.");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}

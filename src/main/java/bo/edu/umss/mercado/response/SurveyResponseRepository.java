package bo.edu.umss.mercado.response;

import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.AnswerState;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.ValidationResult;
import bo.edu.umss.mercado.survey.SurveyValidator;
import bo.edu.umss.mercado.data.CaptureStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.util.LinkedMultiValueMap;

@Repository
public class SurveyResponseRepository {
    private final JdbcTemplate jdbc;
    private final SurveyCatalog catalog;
    private final CaptureStore captures;

    public SurveyResponseRepository(JdbcTemplate jdbc, SurveyCatalog catalog, CaptureStore captures) {
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.captures = captures;
    }

    public boolean submitted(String token) {
        return responseIdForToken(token).isPresent();
    }

    public Optional<String> responseIdForToken(String token) {
        var ids = jdbc.queryForList("SELECT id FROM survey_response WHERE submission_token = ?", String.class, token);
        return ids.stream().findFirst();
    }

    @Transactional
    public String save(SurveyDefinition survey, String token, ValidationResult validation) {
        if (!validation.valid()) throw new IllegalArgumentException("No se puede guardar un formulario con errores.");
        Map<String, List<String>> answers = validation.answers();
        var questionIds = survey.questions().stream().map(Question::id).collect(Collectors.toSet());
        if (!questionIds.equals(validation.states().keySet()) || !questionIds.containsAll(answers.keySet())) {
            throw new IllegalArgumentException("Los estados deben corresponder al instrumento completo.");
        }
        for (Question question : survey.questions()) {
            AnswerState state = validation.states().get(question.id());
            boolean hasValue = !answers.getOrDefault(question.id(), List.of()).isEmpty();
            boolean applicable = question.applicable(answers);
            AnswerState expectedState = !applicable ? AnswerState.NO_APLICA
                    : hasValue ? AnswerState.CON_VALOR : AnswerState.SIN_RESPUESTA;
            if (state != expectedState || (!applicable && hasValue)
                    || (applicable && question.required() && !hasValue)) {
                throw new IllegalArgumentException("El estado no coincide con la respuesta y su aplicabilidad.");
            }
        }
        var checkedValues = new LinkedMultiValueMap<String, String>();
        answers.forEach((key, values) -> checkedValues.put(key, List.copyOf(values)));
        checkedValues.set("consent", "SI");
        if (!new SurveyValidator().validate(survey, checkedValues).valid()) {
            throw new IllegalArgumentException("Los valores no cumplen las reglas de esta versión del instrumento.");
        }
        var existing = responseIdForToken(token);
        if (existing.isPresent()) return captures.verifyRetry(existing.get(), survey, validation);
        String id = UUID.randomUUID().toString();
        OffsetDateTime submittedAt = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update("""
                INSERT INTO survey_response (id, submission_token, survey_slug, instrument_version, submitted_at, consent)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """, id, token, survey.slug(), survey.version(), submittedAt);
        List<Object[]> batch = new ArrayList<>();
        List<Object[]> stateBatch = new ArrayList<>();
        for (int questionIndex = 0; questionIndex < survey.questions().size(); questionIndex++) {
            var q = survey.questions().get(questionIndex);
            stateBatch.add(new Object[]{id, q.id(), questionIndex, q.label(), validation.states().get(q.id()).name()});
            var values = answers.getOrDefault(q.id(), List.of());
            for (int item = 0; item < values.size(); item++) {
                String value = values.get(item);
                String display = q.options().stream().filter(o -> o.value().equals(value))
                        .map(o -> o.label()).findFirst().orElse(value);
                batch.add(new Object[]{id, q.id(), questionIndex, q.label(), item, value, display});
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO survey_answer (response_id, question_id, question_index, question_label, item_index, answer_value, display_value)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, batch);
        jdbc.batchUpdate("""
                INSERT INTO survey_question_state (response_id, question_id, question_index, question_label, answer_state)
                VALUES (?, ?, ?, ?, ?)
                """, stateBatch);
        captures.persist(id, survey, validation, submittedAt);
        return id;
    }

    public String verifyRetry(String responseId, SurveyDefinition survey, ValidationResult validation) {
        return captures.verifyRetry(responseId, survey, validation);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class);
    }

    public long count(String slug) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE survey_slug = ?", Long.class, slug);
    }

    public List<ResponseSummary> recent() {
        return jdbc.query("SELECT * FROM survey_response ORDER BY submitted_at DESC, id DESC LIMIT 100",
                (rs, row) -> summary(rs));
    }

    public ResponseDetail detail(String id) {
        List<ResponseSummary> matches = jdbc.query("SELECT * FROM survey_response WHERE id = ?", (rs, row) -> summary(rs), id);
        if (matches.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Respuesta no encontrada");
        var values = new LinkedHashMap<String, List<String>>();
        var labels = new LinkedHashMap<String, String>();
        jdbc.query("SELECT * FROM survey_answer WHERE response_id = ? ORDER BY question_index, item_index", (RowCallbackHandler) rs -> {
            String key = rs.getString("question_id");
            labels.put(key, rs.getString("question_label"));
            values.computeIfAbsent(key, ignored -> new ArrayList<>()).add(rs.getString("display_value"));
        }, id);
        List<AnswerView> withStates = jdbc.query("""
                SELECT question_id, question_label, answer_state FROM survey_question_state
                WHERE response_id = ? ORDER BY question_index
                """, (rs, row) -> new AnswerView(rs.getString("question_label"),
                List.copyOf(values.getOrDefault(rs.getString("question_id"), List.of())),
                AnswerState.valueOf(rs.getString("answer_state"))), id);
        if (!withStates.isEmpty()) return new ResponseDetail(matches.getFirst(), withStates, true);
        return new ResponseDetail(matches.getFirst(), values.entrySet().stream()
                .map(e -> new AnswerView(labels.get(e.getKey()), List.copyOf(e.getValue()), null)).toList(), false);
    }

    private ResponseSummary summary(ResultSet rs) throws SQLException {
        String slug = rs.getString("survey_slug");
        String submittedAt = rs.getObject("submitted_at", OffsetDateTime.class)
                .atZoneSameInstant(ZoneId.of("America/La_Paz")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        return new ResponseSummary(rs.getString("id"), slug, catalog.get(slug).title(), rs.getString("instrument_version"), submittedAt);
    }

    /** Exportación larga: una fila por valor, sin aplanar selecciones múltiples. */
    public void writeCsv(Writer writer) throws IOException {
        writer.write('\uFEFF');
        csvRow(writer, List.of("response_id", "survey_slug", "instrument_version", "submitted_at", "question_id",
                "item_index", "answer_value", "question_label", "display_value"));
        jdbc.query("""
                SELECT r.id, r.survey_slug, r.instrument_version, r.submitted_at,
                       a.question_id, a.item_index, a.answer_value, a.question_label, a.display_value
                FROM survey_response r JOIN survey_answer a ON r.id = a.response_id
                ORDER BY r.submitted_at, r.id, a.question_index, a.item_index
                """, (RowCallbackHandler) rs -> {
            try {
                csvRow(writer, List.of(rs.getString("id"), rs.getString("survey_slug"), rs.getString("instrument_version"),
                        rs.getObject("submitted_at", OffsetDateTime.class).toString(), rs.getString("question_id"),
                        rs.getString("item_index"), rs.getString("answer_value"), rs.getString("question_label"), rs.getString("display_value")));
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        });
    }

    private void csvRow(Writer writer, List<String> cells) throws IOException {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) writer.write(',');
            String value = cells.get(i);
            String first = value.stripLeading();
            if ((!first.isEmpty() && "=+-@".indexOf(first.charAt(0)) >= 0)
                    || (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0)) {
                value = "'" + value;
            }
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }
}

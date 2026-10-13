package bo.edu.umss.mercado.response;

import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class SurveyResponseRepository {
    private final JdbcTemplate jdbc;
    private final SurveyCatalog catalog;

    public SurveyResponseRepository(JdbcTemplate jdbc, SurveyCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    public boolean submitted(String token) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE submission_token = ?", Long.class, token) > 0;
    }

    @Transactional
    public void save(SurveyDefinition survey, String token, Map<String, List<String>> answers) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO survey_response (id, submission_token, survey_slug, instrument_version, submitted_at, consent)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """, id, token, survey.slug(), survey.version(), OffsetDateTime.now(ZoneOffset.UTC));
        List<Object[]> batch = new ArrayList<>();
        for (int questionIndex = 0; questionIndex < survey.questions().size(); questionIndex++) {
            var q = survey.questions().get(questionIndex);
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
        return new ResponseDetail(matches.getFirst(), values.entrySet().stream()
                .map(e -> new AnswerView(labels.get(e.getKey()), List.copyOf(e.getValue()))).toList());
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

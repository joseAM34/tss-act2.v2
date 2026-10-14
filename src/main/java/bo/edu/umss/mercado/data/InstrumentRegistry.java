package bo.edu.umss.mercado.data;

import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra definiciones inmutables; una modificación exige otro identificador de versión. */
@Service
public class InstrumentRegistry implements ApplicationRunner {
    public static final String STUDY_ID = "umss-mercado-2026-borrador";
    private final JdbcTemplate jdbc;
    private final SurveyCatalog catalog;

    public InstrumentRegistry(JdbcTemplate jdbc, SurveyCatalog catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        catalog.all().forEach(this::register);
    }

    @Transactional
    public Snapshot register(SurveyDefinition survey) {
        String hash = CanonicalJson.hash(survey);
        var existing = jdbc.query("""
                SELECT * FROM instrument_snapshot WHERE survey_slug = ? AND instrument_version = ?
                """, (rs, n) -> new Snapshot(rs.getString("study_id"),
                CanonicalJson.read(rs.getString("definition_json"), SurveyDefinition.class),
                rs.getString("definition_hash"), rs.getString("status")), survey.slug(), survey.version());
        if (!existing.isEmpty()) {
            Snapshot snapshot = existing.getFirst();
            if (!hash.equals(snapshot.hash()) || !hash.equals(CanonicalJson.hash(snapshot.definition()))) {
                throw new IllegalStateException("La definición cambió sin cambiar la versión del instrumento.");
            }
            return snapshot;
        }
        jdbc.update("""
                INSERT INTO instrument_snapshot
                (survey_slug, instrument_version, study_id, status, definition_json, definition_hash, created_at)
                VALUES (?, ?, ?, 'BORRADOR', ?, ?, ?)
                """, survey.slug(), survey.version(), STUDY_ID, CanonicalJson.json(survey), hash,
                OffsetDateTime.now(ZoneOffset.UTC));
        return new Snapshot(STUDY_ID, survey, hash, "BORRADOR");
    }

    public record Snapshot(String studyId, SurveyDefinition definition, String hash, String status) {}
}

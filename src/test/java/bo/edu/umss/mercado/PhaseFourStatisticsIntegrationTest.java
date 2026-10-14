package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.security.AdministratorService;
import bo.edu.umss.mercado.statistics.AnalysisPlan;
import bo.edu.umss.mercado.statistics.AnalysisService;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.SurveyValidator;
import bo.edu.umss.mercado.survey.ValidationResult;
import bo.edu.umss.mercado.wrangling.WranglingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Estadística consumida desde conjuntos congelados, con denominadores y acceso verificables. */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:phase-four-statistics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseFourStatisticsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SurveyCatalog catalog;
    @Autowired SurveyValidator validator;
    @Autowired SurveyResponseRepository responses;
    @Autowired CaptureStore captures;
    @Autowired WranglingService wrangling;
    @Autowired AnalysisService analyses;
    @Autowired AdministratorService administrators;

    @BeforeEach
    void clearTestData() {
        jdbc.update("DELETE FROM statistical_analysis");
        jdbc.update("DELETE FROM wrangling_member");
        jdbc.update("DELETE FROM wrangling_run");
        jdbc.update("DELETE FROM wrangling_text_coding");
        jdbc.update("DELETE FROM wrangling_correction");
        jdbc.update("DELETE FROM wrangling_origin_classification");
        jdbc.update("DELETE FROM survey_question_state");
        jdbc.update("DELETE FROM survey_answer");
        jdbc.update("DELETE FROM survey_response");
        jdbc.update("DELETE FROM instrument_snapshot WHERE instrument_version LIKE 'test-phase4-%'");
        jdbc.update("DELETE FROM administrator");
        administrators.register("test-admin", "test-secret-no-real");
    }

    @AfterEach
    void removeAnalysisForeignKeysForOtherSuitesOnTheSharedPostgresDatabase() {
        jdbc.update("DELETE FROM statistical_analysis");
    }

    @Test
    void analysisRecordsTheSourceHashesProtocolPlanAndAdministrator() {
        save("titulados");
        String run = prepare();
        AnalysisPlan plan = new AnalysisPlan(List.of("B01"), "Envío revisado", "Registro documentado para esta prueba", false, .01);
        String id = analyses.create(run, plan, "reviewer");
        Map<String, Object> bundle = analyses.detail(id);
        Map<String, Object> manifest = object(bundle, "manifest");
        assertThat(manifest).containsEntry("analysis_id", id).containsEntry("source_run_id", run)
                .containsEntry("source_logical_hash", wrangling.detail(run).summary().logicalHash())
                .containsEntry("protocol_version", AnalysisPlan.PROTOCOL_VERSION).containsEntry("actor", "reviewer")
                .containsEntry("status", "BORRADOR").containsEntry("independence_documented", true);
        assertThat(manifest.get("logical_hash")).asString().matches("[a-f0-9]{64}");
        assertThat(object(bundle, "plan")).containsEntry("contrasts", List.of("B01"))
                .containsEntry("independenceUnit", plan.independenceUnit()).containsEntry("independenceEvidence", plan.independenceEvidence())
                .containsEntry("alpha", .01);
        assertThat(object(bundle, "result")).containsEntry("protocol_version", AnalysisPlan.PROTOCOL_VERSION);
        assertThat(jdbc.queryForObject("SELECT actor FROM statistical_analysis WHERE id = ?", String.class, id)).isEqualTo("reviewer");
    }

    @Test
    void theSamePreparedDataAndPlanHaveAStableLogicalHashAcrossNewAnalysisIdentifiers() {
        save("titulados");
        String run = prepare();
        String first = analyses.create(run, AnalysisPlan.defaults(), "reviewer");
        String second = analyses.create(run, AnalysisPlan.defaults(), "another-reviewer");
        assertThat(second).isNotEqualTo(first);
        assertThat(object(analyses.detail(second), "manifest").get("logical_hash"))
                .isEqualTo(object(analyses.detail(first), "manifest").get("logical_hash"));
        assertThat(object(analyses.detail(second), "manifest").get("actor")).isEqualTo("another-reviewer");
    }

    @Test
    void subsequentCorrectionsAndNewSourceDataCannotChangeAPublishedAnalysis() {
        String response = save("titulados");
        String run = prepare();
        String id = analyses.create(run, AnalysisPlan.defaults(), "reviewer");
        String frozen = analyses.bundle(id);
        wrangling.correct(response, "edad", List.of("40"), "Revisión posterior al análisis", "reviewer");
        save("titulados");
        String newRun = prepare();
        String newer = analyses.create(newRun, AnalysisPlan.defaults(), "reviewer");
        assertThat(analyses.bundle(id)).isEqualTo(frozen);
        assertThat(object(analyses.detail(id), "manifest").get("source_run_id")).isEqualTo(run);
        assertThat(object(analyses.detail(newer), "manifest").get("source_logical_hash"))
                .isNotEqualTo(object(analyses.detail(id), "manifest").get("source_logical_hash"));
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'edad'",
                String.class, response)).isEqualTo("30");
    }

    @Test
    void tamperedAnalysisJsonCannotBeReadOrDownloadedAsAVerifiedResult() {
        save("titulados");
        String id = analyses.create(prepare(), AnalysisPlan.defaults(), "reviewer");
        jdbc.update("UPDATE statistical_analysis SET bundle_json = '{}' WHERE id = ?", id);
        assertThatThrownBy(() -> analyses.bundle(id)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> analyses.detail(id)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anInvalidSourceHashStopsPreparationBeforeAnAnalysisIsPublished() {
        save("titulados");
        String run = prepare();
        jdbc.update("UPDATE wrangling_run SET bundle_json = '{}' WHERE id = ?", run);
        assertThatThrownBy(() -> analyses.create(run, AnalysisPlan.defaults(), "reviewer"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isZero();
    }

    @Test
    void aPersistenceFailureCannotPublishAnAnalysisOrReplaceAnExistingOne() {
        save("titulados");
        String run = prepare();
        String existing = analyses.create(run, AnalysisPlan.defaults(), "reviewer");
        String frozen = analyses.bundle(existing);
        jdbc.execute("ALTER TABLE statistical_analysis ADD CONSTRAINT test_reject_statistics_actor CHECK (actor <> 'reject-test')");
        try {
            assertThatThrownBy(() -> analyses.create(run, AnalysisPlan.defaults(), "reject-test"))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isEqualTo(1);
            assertThat(analyses.bundle(existing)).isEqualTo(frozen);
        } finally {
            jdbc.execute("ALTER TABLE statistical_analysis DROP CONSTRAINT test_reject_statistics_actor");
        }
    }

    @Test
    void frequencyPercentagesUseValidAnswersWhileCoverageKeepsUnansweredApplicableParticipants() {
        save(catalog.get("titulados"), Map.of("genero", List.of("HOMBRE")));
        save(catalog.get("titulados"), Map.of("genero", List.of("MUJER")));
        save(catalog.get("titulados"), Map.of("genero", List.of()));
        Map<String, Object> stratum = stratum(analyse(), "titulados", catalog.get("titulados").version());
        Map<String, Object> frequency = item(records(stratum, "frequencies"), "genero");
        Map<String, Object> coverage = object(frequency, "coverage");
        assertCount(coverage, "n_base", 3);
        assertCount(coverage, "n_present", 3);
        assertCount(coverage, "n_applicable", 3);
        assertCount(coverage, "n_validos", 2);
        for (String code : List.of("HOMBRE", "MUJER")) {
            Map<String, Object> count = coded(records(frequency, "counts"), "code", code);
            assertCount(count, "count", 1);
            assertCount(count, "denominator", 2);
            assertThat(((Number) count.get("percentage")).doubleValue()).isEqualTo(50);
        }
        assertThat(strings(frequency.get("modas"))).containsExactlyInAnyOrder("HOMBRE", "MUJER");
    }

    @Test
    void aConditionalFrequencySeparatesItsEligibleParticipantsFromThoseOutsideTheBranch() {
        save("titulados");
        save(catalog.get("titulados"), Map.of("interes_posgrado", List.of("NO")));
        Map<String, Object> stratum = stratum(analyse(), "titulados", catalog.get("titulados").version());
        Map<String, Object> frequency = item(records(stratum, "frequencies"), "area_interes");
        Map<String, Object> coverage = object(frequency, "coverage");
        assertCount(coverage, "n_base", 2);
        assertCount(coverage, "n_present", 2);
        assertCount(coverage, "n_applicable", 1);
        assertCount(coverage, "n_validos", 1);
        Map<String, Object> count = coded(records(frequency, "counts"), "code", "IA");
        assertCount(count, "count", 1);
        assertCount(count, "denominator", 1);
        assertThat(((Number) count.get("percentage")).doubleValue()).isEqualTo(100);
    }

    @Test
    void numericalStatisticsUseOnlyValidObservedNumbersAndKeepTheirCoverage() {
        save(catalog.get("titulados"), Map.of("edad", List.of("30"), "meses_desempleado", List.of("0")));
        save(catalog.get("titulados"), Map.of("edad", List.of("40")));
        save(catalog.get("titulados"), Map.of("edad", List.of()));
        Map<String, Object> stratum = stratum(analyse(), "titulados", catalog.get("titulados").version());
        Map<String, Object> age = item(records(stratum, "numeric"), "edad");
        assertCount(age, "n", 2);
        assertThat(((Number) age.get("mean")).doubleValue()).isEqualTo(35);
        assertThat(((Number) age.get("median")).doubleValue()).isEqualTo(35);
        assertThat(((Number) age.get("sample_standard_deviation")).doubleValue()).isCloseTo(Math.sqrt(50), within(1e-12));
        assertCount(object(age, "coverage"), "n_base", 3);
        assertCount(object(age, "coverage"), "n_validos", 2);
        Map<String, Object> unemployment = item(records(stratum, "numeric"), "meses_desempleado");
        assertThat(((Number) unemployment.get("min")).doubleValue()).isZero();
        assertCount(unemployment, "n", 3);
    }

    @Test
    void multipleSelectionPercentagesDistinguishParticipantsFromMentionsAndOmissions() {
        save(catalog.get("titulados"), Map.of("aspectos_utiles", List.of("APLICACION_PRACTICA", "PASANTIAS")));
        save(catalog.get("titulados"), Map.of("aspectos_utiles", List.of("PASANTIAS")));
        save(catalog.get("titulados"), Map.of("aspectos_utiles", List.of()));
        Map<String, Object> stratum = stratum(analyse(), "titulados", catalog.get("titulados").version());
        Map<String, Object> multiple = item(records(stratum, "multiple"), "aspectos_utiles");
        assertCount(multiple, "n_validos", 2);
        assertCount(multiple, "mention_count", 3);
        Map<String, Object> placements = coded(records(multiple, "options"), "code", "PASANTIAS");
        assertCount(placements, "count", 2);
        assertCount(placements, "denominator", 2);
        assertThat(((Number) placements.get("participant_percentage")).doubleValue()).isEqualTo(100);
        assertThat(((Number) placements.get("mention_percentage")).doubleValue()).isCloseTo(200d / 3, within(1e-12));
        Map<String, Object> practical = coded(records(multiple, "options"), "code", "APLICACION_PRACTICA");
        assertThat(((Number) practical.get("participant_percentage")).doubleValue()).isEqualTo(50);
        assertThat(((Number) practical.get("mention_percentage")).doubleValue()).isCloseTo(100d / 3, within(1e-12));
        assertCount(object(multiple, "coverage"), "n_base", 3);
    }

    @Test
    void noSabeAndCannotRateRemainExplicitCategoriesOutsideSubstantiveAgreementCounts() {
        save(catalog.get("empleadores"), Map.of("criterio_01", List.of("TOTALMENTE_ACUERDO")));
        save(catalog.get("empleadores"), Map.of("criterio_01", List.of("NO_SABE")));
        save(catalog.get("empleadores"), Map.of("criterio_01", List.of("TOTALMENTE_DESACUERDO")));
        save(catalog.get("titulados"), Map.of("competencia_tecnica_01", List.of("ADECUACION_1")));
        save(catalog.get("titulados"), Map.of("competencia_tecnica_01", List.of("ADECUACION_5")));
        save(catalog.get("titulados"), Map.of("competencia_tecnica_01", List.of("NO_PUEDE_VALORAR")));
        Map<String, Object> bundle = analyse();
        Map<String, Object> employer = stratum(bundle, "empleadores", catalog.get("empleadores").version());
        Map<String, Object> criterion = item(records(employer, "frequencies"), "criterio_01");
        assertCount(object(criterion, "coverage"), "n_validos", 3);
        assertCount(object(criterion, "coverage"), "n_no_sabe", 1);
        assertCount(object(criterion, "coverage"), "n_sustantivos", 2);
        assertCount(coded(records(criterion, "counts"), "code", "NO_SABE"), "count", 1);
        Map<String, Object> graduate = stratum(bundle, "titulados", catalog.get("titulados").version());
        Map<String, Object> skill = item(records(graduate, "frequencies"), "competencia_tecnica_01");
        assertCount(object(skill, "coverage"), "n_no_puede_valorar", 1);
        assertCount(object(skill, "coverage"), "n_sustantivos", 2);
        assertCount(coded(records(skill, "counts"), "code", "NO_PUEDE_VALORAR"), "count", 1);
        assertThat(records(employer, "numeric").stream().map(variable -> variable.get("variable"))).doesNotContain("criterio_01");
        assertThat(records(graduate, "numeric").stream().map(variable -> variable.get("variable"))).doesNotContain("competencia_tecnica_01");
        Map<String, Object> criterionScores = item(records(employer, "ordinal"), "criterio_01");
        assertCount(criterionScores, "n_scored", 2);
        assertThat(object(criterionScores, "score_map")).doesNotContainKey("NO_SABE");
        assertThat(((Number) object(criterionScores, "score_summary").get("mean")).doubleValue()).isEqualTo(2);
        Map<String, Object> skillScores = item(records(graduate, "ordinal"), "competencia_tecnica_01");
        assertCount(skillScores, "n_scored", 2);
        assertThat(object(skillScores, "score_map")).doesNotContainKey("NO_PUEDE_VALORAR");
        assertThat(((Number) object(skillScores, "score_summary").get("mean")).doubleValue()).isEqualTo(3);
        Map<String, Object> agreement = coded(records(employer, "indicators"), "indicator", "ACUERDO_SOBRE_SUSTANTIVOS");
        assertCount(agreement, "numerator", 1);
        assertCount(agreement, "denominator", 2);
        assertThat(((Number) agreement.get("percentage")).doubleValue()).isEqualTo(50);
    }

    @Test
    void occupationUsesEmployedAndEntrepreneurshipAmongEightValidDeclarations() {
        for (int i = 0; i < 4; i++) save("titulados");
        for (int i = 0; i < 2; i++) save(catalog.get("titulados"), Map.of("estado_laboral", List.of("EMPRENDIMIENTO")));
        for (int i = 0; i < 2; i++) save(catalog.get("titulados"), Map.of("estado_laboral", List.of("DESEMPLEADO"), "sector", List.of("NO_APLICA")));
        Map<String, Object> graduate = stratum(analyse(), "titulados", catalog.get("titulados").version());
        Map<String, Object> occupation = coded(records(graduate, "indicators"), "indicator", "OCUPACION_DECLARADA");
        assertCount(occupation, "numerator", 6);
        assertCount(occupation, "denominator", 8);
        assertThat(((Number) occupation.get("percentage")).doubleValue()).isEqualTo(75);
    }

    @Test
    void anOpenEndedJobCountStaysAnOrdinalCategoryInsteadOfBecomingExactlyFourJobs() {
        save(catalog.get("titulados"), Map.of("cantidad_empleos", List.of("MAS_TRES")));
        Map<String, Object> graduate = stratum(analyse(), "titulados", catalog.get("titulados").version());
        assertThat(records(graduate, "numeric").stream().map(variable -> variable.get("variable"))).doesNotContain("cantidad_empleos");
        Map<String, Object> jobs = item(records(graduate, "ordinal"), "cantidad_empleos");
        assertThat(jobs.get("score_summary")).isNull();
        assertThat(strings(jobs.get("central_categories"))).containsExactly("MAS_TRES");
        assertCount(coded(records(item(records(graduate, "frequencies"), "cantidad_empleos"), "counts"), "code", "MAS_TRES"), "count", 1);
    }

    @Test
    void textThemesUseReviewedParticipantsIncludingExplicitEmptyReviewsRatherThanAllTextsOrMentions() {
        String privateText = "TEXTO-PRIVADO-" + UUID.randomUUID();
        String first = save(catalog.get("empleadores"), Map.of("areas_emergentes", List.of(privateText)));
        String second = save("empleadores");
        save("empleadores");
        wrangling.codeText(first, "areas_emergentes", List.of("IA", "IA"), "libro-v1", "Tema revisado", "reviewer");
        wrangling.codeText(second, "areas_emergentes", List.of(), "libro-v1", "Revisado sin tema aplicable", "reviewer");
        Map<String, Object> bundle = analyse();
        Map<String, Object> employer = stratum(bundle, "empleadores", catalog.get("empleadores").version());
        Map<String, Object> text = coded(records(employer, "texts"), "codebook_version", "libro-v1");
        assertCount(text, "reviewed_count", 2);
        assertCount(text, "valid_text_count", 3);
        assertCount(text, "unreviewed_count", 1);
        Map<String, Object> theme = coded(records(text, "themes"), "theme", "IA");
        assertCount(theme, "count", 1);
        assertCount(theme, "denominator", 2);
        assertThat(((Number) theme.get("percentage")).doubleValue()).isEqualTo(50);
        assertThat(CanonicalJson.json(bundle)).doesNotContain(privateText);
    }

    @Test
    void defaultIndependenceAndStructuralZeroGuardsBlockInferenceEvenWithEnoughExpectedFrequencies() {
        SurveyDefinition graduate = catalog.get("titulados");
        for (int i = 0; i < 10; i++) save(graduate, Map.of("estado_laboral", List.of("EMPLEADO"), "interes_posgrado", List.of("SI")));
        for (int i = 0; i < 10; i++) save(graduate, Map.of("estado_laboral", List.of("DESEMPLEADO"), "sector", List.of("NO_APLICA"), "interes_posgrado", List.of("NO")));
        for (int i = 0; i < 5; i++) save(graduate, Map.of("estado_laboral", List.of("EMPLEADO"), "interes_posgrado", List.of("NO")));
        for (int i = 0; i < 5; i++) save(graduate, Map.of("estado_laboral", List.of("DESEMPLEADO"), "sector", List.of("NO_APLICA"), "interes_posgrado", List.of("SI")));
        String run = prepare();
        Map<String, Object> pending = contrast(analyses.detail(analyses.create(run,
                new AnalysisPlan(List.of("B03"), "", "", false, .05), "reviewer")), "B03");
        assertCount(pending, "n_pairs", 30);
        assertThat(pending.get("status")).isEqualTo("INDEPENDENCIA_NO_DOCUMENTADA");
        assertThat(object(pending, "table").get("p")).isNull();
        Map<String, Object> executable = contrast(analyses.detail(analyses.create(run,
                new AnalysisPlan(List.of("B03"), "Unidad revisada", "Independencia documentada para la prueba", false, .05), "reviewer")), "B03");
        assertThat(executable.get("status")).isEqualTo("CALCULADO");
        assertThat(((Number) object(executable, "table").get("p")).doubleValue()).isFinite().isBetween(0d, 1d);
        Map<String, Object> incompatible = contrast(analyses.detail(analyses.create(run,
                new AnalysisPlan(List.of("B03"), "Unidad revisada", "Independencia documentada para la prueba", true, .05), "reviewer")), "B03");
        assertThat(incompatible.get("status")).isEqualTo("ESTRUCTURA_INCOMPATIBLE");
        assertThat(object(incompatible, "table").get("p")).isNull();
    }

    @Test
    void futureAndPastFinancingCrossesUseTheirOwnPairsWithoutCountingInactiveBranches() {
        save("titulados");
        save(catalog.get("titulados"), Map.of("financiamiento_futuro", List.of("BECA_ESTATAL"), "nivel_interes", List.of("DOCTORADO"),
                "financiamiento_cursado", List.of("BECA_TOTAL"), "nivel_cursado", List.of("MAESTRIA")));
        save(catalog.get("titulados"), Map.of("interes_posgrado", List.of("NO")));
        Map<String, Object> bundle = analyses.detail(analyses.create(prepare(), new AnalysisPlan(List.of("B01", "B02"), "", "", false, .05), "reviewer"));
        Map<String, Object> future = contrast(bundle, "B01");
        assertThat(strings(future.get("variables"))).containsExactly("financiamiento_futuro", "nivel_interes");
        assertCount(future, "n_pairs", 2);
        assertCount(future, "exclusions", 1);
        assertThat(strings(future.get("rows"))).contains("BECA_ESTATAL").doesNotContain("BECA_TOTAL");
        Map<String, Object> past = contrast(bundle, "B02");
        assertThat(strings(past.get("variables"))).containsExactly("financiamiento_cursado", "nivel_cursado");
        assertCount(past, "n_pairs", 3);
        assertCount(past, "exclusions", 0);
        assertThat(strings(past.get("rows"))).contains("BECA_TOTAL").doesNotContain("BECA_ESTATAL");
        assertThat(records(object(bundle, "result"), "families")).allSatisfy(family -> assertCount(family, "planned_count", 1));
    }

    @Test
    void employerCriteriaKeepNoSabeOutsideInferentialPairsAndRetainAllTenPlannedTests() {
        save("empleadores");
        save(catalog.get("empleadores"), Map.of("tipo_organizacion", List.of("PRIVADO"), "criterio_01", List.of("NO_SABE")));
        List<String> criteria = java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(index -> "B06_%02d".formatted(index)).toList();
        Map<String, Object> bundle = analyses.detail(analyses.create(prepare(), new AnalysisPlan(criteria, "", "", false, .05), "reviewer"));
        Map<String, Object> criterion = contrast(bundle, "B06_01");
        assertCount(criterion, "n_pairs", 1);
        assertCount(criterion, "exclusions", 1);
        assertThat(strings(criterion.get("columns"))).doesNotContain("NO_SABE");
        assertCount(object(criterion, "no_sabe_by_row"), "PRIVADO", 1);
        assertThat(records(object(bundle, "result"), "contrasts")).hasSize(10)
                .extracting(contrast -> contrast.get("id")).doesNotHaveDuplicates();
        Map<String, Object> family = records(object(bundle, "result"), "families").getFirst();
        assertCount(family, "planned_count", 10);
        assertCount(family, "executed_count", 0);
        assertThat((List<?>) family.get("adjusted_p")).hasSize(10).allMatch(java.util.Objects::isNull);
    }

    @Test
    void professionalAndUnemploymentMonthsUseCompletePairsWithoutInventingGraduationSeniority() {
        save("titulados");
        save(catalog.get("titulados"), Map.of("meses_vida_profesional", List.of("100"), "meses_desempleado", List.of("0")));
        save(catalog.get("titulados"), Map.of("meses_vida_profesional", List.of(), "meses_desempleado", List.of()));
        Map<String, Object> bundle = analyses.detail(analyses.create(prepare(), new AnalysisPlan(List.of("B07", "B08"), "", "", false, .05), "reviewer"));
        Map<String, Object> months = contrast(bundle, "B08");
        assertCount(months, "n_pairs", 2);
        assertThat(months.get("status")).isEqualTo("DESCRIPTIVO");
        assertThat(((Number) object(months, "x_summary").get("mean")).doubleValue()).isEqualTo(110);
        assertThat(((Number) object(months, "y_summary").get("mean")).doubleValue()).isEqualTo(12);
        assertThat(records(months, "pairs")).hasSize(2).anySatisfy(pair -> {
            assertThat(((Number) pair.get("x")).doubleValue()).isEqualTo(120);
            assertThat(((Number) pair.get("y")).doubleValue()).isEqualTo(24);
        });
        Map<String, Object> seniority = contrast(bundle, "B07");
        assertThat(seniority).containsEntry("status", "PENDIENTE_DEFINICION")
                .containsEntry("reason", "ANTIGUEDAD_SIN_REFERENCIA_APROBADA").containsEntry("table", null);
    }

    @Test
    void distinctInstrumentVersionsKeepTheirOwnDescriptiveStrataInsteadOfBeingPooled() {
        SurveyDefinition current = catalog.get("titulados");
        SurveyDefinition another = new SurveyDefinition(current.slug(), current.title(), current.description(),
                "test-phase4-alternate-version", current.questions());
        save(current, Map.of("edad", List.of("30")));
        save(another, Map.of("edad", List.of("60")));
        Map<String, Object> result = object(analyse(), "result");
        assertThat(records(result, "strata")).hasSize(2);
        Map<String, Object> currentStratum = coded(records(result, "strata"), "instrument_version", current.version());
        Map<String, Object> anotherStratum = coded(records(result, "strata"), "instrument_version", another.version());
        assertThat(item(records(currentStratum, "numeric"), "edad").get("mean")).isEqualTo(30d);
        assertThat(item(records(anotherStratum, "numeric"), "edad").get("mean")).isEqualTo(60d);
    }

    @Test
    void aNewOrdinalCategoryIsCountedWithoutInventingItsPositionOrScore() {
        SurveyDefinition current = catalog.get("titulados");
        List<Question> questions = current.questions().stream().map(q -> {
            if (!q.id().equals("competencia_tecnica_01")) return q;
            var options = new java.util.ArrayList<>(q.options());
            options.add(new Question.Option("CATEGORIA_NUEVA", "Categoría pendiente de regla"));
            return new Question(q.id(), q.section(), q.label(), q.type(), q.required(), options, q.dependsOn(),
                    q.dependsValue(), q.minSelections(), q.maxSelections(), q.hint(), q.min(), q.max());
        }).toList();
        SurveyDefinition updated = new SurveyDefinition(current.slug(), current.title(), current.description(),
                "test-phase4-new-ordinal-category", questions);
        save(updated, Map.of("competencia_tecnica_01", List.of("CATEGORIA_NUEVA")));
        Map<String, Object> group = stratum(analyse(), updated.slug(), updated.version());
        Map<String, Object> distribution = item(records(group, "frequencies"), "competencia_tecnica_01");
        assertCount(coded(records(distribution, "counts"), "code", "CATEGORIA_NUEVA"), "count", 1);
        Map<String, Object> ordinal = item(records(group, "ordinal"), "competencia_tecnica_01");
        assertThat(ordinal).containsEntry("status", "PENDIENTE_REGLA").containsEntry("ordering_complete", false);
        assertCount(ordinal, "n_unordered", 1);
        assertCount(ordinal, "n_scored", 0);
        assertThat(strings(ordinal.get("central_categories"))).isEmpty();
        assertThat(object(ordinal, "score_map")).doesNotContainKey("CATEGORIA_NUEVA");
    }

    @Test
    void verifiedOlderPreparedBundlesPreserveUnknownCoverageAndTextReviewDenominators() {
        save("titulados");
        String employer = save("empleadores");
        wrangling.codeText(employer, "areas_emergentes", List.of("IA"), "libro-anterior", "Revisión de prueba", "reviewer");
        String run = prepare();
        Map<String, Object> older = CanonicalJson.read(wrangling.bundle(run), Map.class);
        older.remove("coverage");
        older.remove("text_reviews");
        Map<String, Object> manifest = object(older, "manifest");
        manifest.put("rules_version", "2026-10-wrangling-1");
        Map<String, Object> logical = new LinkedHashMap<>();
        for (String key : List.of("rules_version", "origin", "mode", "instruments", "studies")) logical.put(key, manifest.get(key));
        for (String key : List.of("members", "dictionary", "graduates", "employers", "selections", "coding", "issues", "quality")) logical.put(key, older.get(key));
        String oldLogicalHash = CanonicalJson.hash(logical);
        manifest.put("logical_hash", oldLogicalHash);
        String frozenOlderJson = CanonicalJson.json(older);
        // Solo la fixture representa un conjunto producido antes de incorporar esa metadata.
        jdbc.update("UPDATE wrangling_run SET logical_hash = ?, bundle_hash = ?, bundle_json = ? WHERE id = ?",
                oldLogicalHash, CanonicalJson.hash(older), frozenOlderJson, run);
        assertThat(wrangling.bundle(run)).isEqualTo(frozenOlderJson);
        Map<String, Object> bundle = analyses.detail(analyses.create(run, AnalysisPlan.defaults(), "reviewer"));
        Map<String, Object> graduate = stratum(bundle, "titulados", catalog.get("titulados").version());
        Map<String, Object> age = item(records(graduate, "numeric"), "edad");
        assertCount(age, "n", 1);
        Map<String, Object> coverage = object(age, "coverage");
        assertCount(coverage, "n_base", 1);
        assertThat(coverage).containsEntry("n_present", null).containsEntry("n_applicable", null)
                .containsEntry("n_con_value", null).containsEntry("coverage_status", "METADATA_DESCONOCIDA");
        Map<String, Object> employers = stratum(bundle, "empleadores", catalog.get("empleadores").version());
        Map<String, Object> text = coded(records(employers, "texts"), "codebook_version", "libro-anterior");
        assertThat(text).containsEntry("reviewed_count", null).containsEntry("valid_text_count", null)
                .containsEntry("unreviewed_count", null).containsEntry("status", "METADATA_DESCONOCIDA");
        Map<String, Object> theme = coded(records(text, "themes"), "theme", "IA");
        assertCount(theme, "count", 1);
        assertThat(theme).containsEntry("denominator", null).containsEntry("percentage", null);
        assertThat(wrangling.bundle(run)).isEqualTo(frozenOlderJson);
    }

    @Test
    void aHistoricalCaptureWithoutAVerifiedDefinitionCannotBorrowCurrentNumericOrCategoricalRules() {
        save("titulados");
        String responseId = UUID.randomUUID().toString();
        String oldVersion = "version-historica-sin-definicion";
        jdbc.update("""
                INSERT INTO survey_response (id, submission_token, survey_slug, instrument_version, submitted_at, consent)
                VALUES (?, ?, 'titulados', ?, ?, TRUE)
                """, responseId, UUID.randomUUID().toString(), oldVersion, OffsetDateTime.of(2020, 1, 2, 12, 0, 0, 0, ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO survey_answer (response_id, question_id, question_index, question_label, item_index, answer_value, display_value)
                VALUES (?, 'edad', 0, 'Etiqueta histórica', 0, '35', '35')
                """, responseId);
        Map<String, Object> historical = stratum(analyse(), "titulados", oldVersion);
        assertCount(historical, "n_base", 1);
        assertThat(records(historical, "numeric")).isEmpty();
        assertThat(records(historical, "frequencies")).isEmpty();
        Map<String, Object> age = item(records(historical, "coverage"), "edad");
        assertThat(age.get("status")).isEqualTo("PENDIENTE_DEFINICION");
        Map<String, Object> counts = object(age, "counts");
        assertCount(counts, "n_validos", 0);
        assertThat(counts).containsEntry("n_present", null).containsEntry("n_applicable", null).containsEntry("n_con_value", null);
        assertCount(object(counts, "state_counts"), "SIN_ESTADO_HISTORICO", 1);
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ?", String.class, responseId)).isEqualTo("35");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state WHERE response_id = ?", Long.class, responseId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/estadisticas", "/admin/datos/123/analisis", "/admin/estadisticas/123", "/admin/estadisticas/123/analisis.json",
            "/admin/estadisticas/123/frecuencias.csv", "/admin/estadisticas/123/numericos.csv", "/admin/estadisticas/123/ordinales.csv",
            "/admin/estadisticas/123/multiples.csv", "/admin/estadisticas/123/textos.csv", "/admin/estadisticas/123/indicadores.csv",
            "/admin/estadisticas/123/cobertura.csv", "/admin/estadisticas/123/cruces.csv", "/admin/estadisticas/123/familias.csv"})
    void statisticalPagesAndEveryDownloadRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/datos/123/analisis", "/admin/estadisticas/123", "/admin/estadisticas/123/analisis.json", "/admin/estadisticas/123/frecuencias.csv"})
    @WithMockUser(roles = "USER")
    void anAuthenticatedNonAdministratorCannotReadStatisticalOutputs(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void startingAnAnalysisRequiresCsrfEvenForAnAdministrator() throws Exception {
        save("titulados");
        String run = prepare();
        mvc.perform(post("/admin/datos/" + run + "/analisis")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isZero();
    }

    @Test
    void anAnonymousRequestCannotStartAnAnalysisEvenWithAValidCsrfToken() throws Exception {
        mvc.perform(post("/admin/datos/123/analisis").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isZero();
    }

    @Test
    @WithMockUser(username = "statistics-reviewer", roles = "ADMIN")
    void theProtectedPanelCreatesAndDeliversAFrozenAnalysisWithoutPrivateSourceText() throws Exception {
        String marker = "FUENTE-PRIVADA-" + UUID.randomUUID();
        save(catalog.get("empleadores"), Map.of("email_contacto", List.of(marker + "@example.test"),
                "nombre_organizacion", List.of(marker), "areas_emergentes", List.of(marker)));
        save("titulados");
        String run = prepare();
        MvcResult form = mvc.perform(get("/admin/datos/" + run + "/analisis")).andExpect(status().isOk()).andReturn();
        assertThat(form.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("name=\"_csrf\"");
        mvc.perform(post("/admin/datos/" + run + "/analisis").with(csrf()))
                .andExpect(status().is3xxRedirection());
        String id = jdbc.queryForObject("SELECT id FROM statistical_analysis", String.class);
        mvc.perform(get("/admin/estadisticas/" + id)).andExpect(status().isOk());
        mvc.perform(get("/admin/estadisticas")).andExpect(status().isOk());
        assertThat(records(object(analyses.detail(id), "result"), "strata")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT actor FROM statistical_analysis WHERE id = ?", String.class, id))
                .isEqualTo("statistics-reviewer");
        MvcResult downloaded = mvc.perform(get("/admin/estadisticas/" + id + "/analisis.json"))
                .andExpect(status().isOk()).andReturn();
        assertThat(downloaded.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(analyses.bundle(id))
                .doesNotContain(marker, "test-secret-no-real");
        assertThat(downloaded.getResponse().getHeader("Content-Disposition")).contains("attachment");
        for (String product : List.of("frecuencias", "numericos", "ordinales", "multiples", "textos", "indicadores", "cobertura", "cruces", "familias")) {
            MvcResult csv = mvc.perform(get("/admin/estadisticas/" + id + "/" + product + ".csv"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(marker);
            assertThat(csv.getResponse().getHeader("Content-Disposition")).contains("attachment");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-contrast", "partial-criteria-family", "partial-independence", "invalid-alpha"})
    @WithMockUser(roles = "ADMIN")
    void theBackendRejectsPlansOutsideTheRegisteredProtocol(String invalidCase) throws Exception {
        save("titulados");
        String run = prepare();
        var request = post("/admin/datos/" + run + "/analisis").with(csrf());
        switch (invalidCase) {
            case "unknown-contrast" -> request.param("contrasts", "CAMPO_ARBITRARIO");
            case "partial-criteria-family" -> request.param("contrasts", "B06_01");
            case "partial-independence" -> request.param("independenceUnit", "persona");
            case "invalid-alpha" -> request.param("alpha", "1");
            default -> throw new IllegalArgumentException(invalidCase);
        }
        mvc.perform(request).andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isZero();
    }

    private String prepare() {
        return wrangling.create("NO_DECLARADO", "BORRADOR", null, "reviewer");
    }

    private Map<String, Object> analyse() {
        return analyses.detail(analyses.create(prepare(), AnalysisPlan.defaults(), "reviewer"));
    }

    private static Map<String, Object> stratum(Map<String, Object> bundle, String slug, String version) {
        return records(object(bundle, "result"), "strata").stream()
                .filter(group -> slug.equals(group.get("survey_slug")) && version.equals(group.get("instrument_version")))
                .findFirst().orElseThrow();
    }

    private static Map<String, Object> item(List<Map<String, Object>> items, String variable) {
        return coded(items, "variable", variable);
    }

    private static Map<String, Object> contrast(Map<String, Object> analysis, String id) {
        return coded(records(object(analysis, "result"), "contrasts"), "id", id);
    }

    private static Map<String, Object> coded(List<Map<String, Object>> items, String key, String code) {
        return items.stream().filter(item -> code.equals(item.get(key))).findFirst().orElseThrow();
    }

    private static void assertCount(Map<String, Object> item, String key, int expected) {
        assertThat(((Number) item.get(key)).intValue()).as(key).isEqualTo(expected);
    }

    private String save(String slug) {
        return save(catalog.get(slug), Map.of());
    }

    private String save(SurveyDefinition survey, Map<String, List<String>> overrides) {
        MultiValueMap<String, String> values = validAnswers(survey, overrides);
        var validated = validator.validate(survey, values);
        assertThat(validated.errors()).isEmpty();
        return responses.save(survey, UUID.randomUUID().toString(), validated);
    }

    private MultiValueMap<String, String> validAnswers(SurveyDefinition survey) {
        return validAnswers(survey, Map.of());
    }

    private MultiValueMap<String, String> validAnswers(SurveyDefinition survey, Map<String, List<String>> overrides) {
        MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
        for (Question question : survey.questions()) {
            if (question.dependsOn() != null && Arrays.stream(question.dependsValue().split("\\|"))
                    .noneMatch(value -> values.getOrDefault(question.dependsOn(), List.of()).contains(value))) continue;
            if (overrides.containsKey(question.id())) {
                values.put(question.id(), overrides.get(question.id()));
                continue;
            }
            if (!question.options().isEmpty()) {
                int selections = question.type().equals("checkbox") ? Math.max(1, question.minSelections()) : 1;
                question.options().stream().limit(selections).forEach(option -> values.add(question.id(), option.value()));
            } else {
                values.set(question.id(), switch (question.type()) {
                    case "number" -> switch (question.id()) {
                        case "anio_egreso" -> "2020";
                        case "edad" -> "30";
                        case "meses_vida_profesional" -> "120";
                        case "meses_desempleado" -> "24";
                        default -> "1";
                    };
                    case "email" -> "test@example.test";
                    case "url" -> "https://example.test/profile";
                    case "tel" -> "+59170000000";
                    default -> "Respuesta de prueba";
                });
            }
        }
        values.set("consent", "SI");
        return values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> map, String key) {
        return (List<Map<String, Object>>) map.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object value) {
        return (List<String>) value;
    }
}

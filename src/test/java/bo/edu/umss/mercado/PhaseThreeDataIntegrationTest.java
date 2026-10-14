package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.data.InstrumentRegistry;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.security.AdministratorService;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.AnswerState;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.SurveyValidator;
import bo.edu.umss.mercado.survey.ValidationResult;
import bo.edu.umss.mercado.wrangling.WranglingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica el pipeline contra capturas reales, revisiones y salidas protegidas. */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:survey-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseThreeDataIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SurveyCatalog catalog;
    @Autowired SurveyValidator validator;
    @Autowired SurveyResponseRepository responses;
    @Autowired InstrumentRegistry instruments;
    @Autowired CaptureStore captures;
    @Autowired WranglingService wrangling;
    @Autowired AdministratorService administrators;

    @BeforeEach
    void clearTestData() {
        jdbc.update("DELETE FROM wrangling_member");
        jdbc.update("DELETE FROM wrangling_run");
        jdbc.update("DELETE FROM wrangling_text_coding");
        jdbc.update("DELETE FROM wrangling_correction");
        jdbc.update("DELETE FROM wrangling_origin_classification");
        jdbc.update("DELETE FROM survey_question_state");
        jdbc.update("DELETE FROM survey_answer");
        jdbc.update("DELETE FROM survey_response");
        jdbc.update("DELETE FROM instrument_snapshot WHERE instrument_version LIKE 'test-phase3-%'");
        jdbc.update("DELETE FROM administrator");
        administrators.register("test-admin", "test-secret-no-real");
    }

    @Test
    void instrumentDefinitionIsHashedAndCannotChangeWithoutANewVersion() {
        SurveyDefinition original = catalog.get("titulados");
        var first = instruments.register(original);
        assertThat(first.hash()).matches("[a-f0-9]{64}").isEqualTo(CanonicalJson.hash(original));
        assertThat(instruments.register(original)).isEqualTo(first);
        String frozen = jdbc.queryForObject("SELECT definition_json FROM instrument_snapshot "
                + "WHERE survey_slug = ? AND instrument_version = ?", String.class, original.slug(), original.version());
        SurveyDefinition changed = new SurveyDefinition(original.slug(), "Título modificado", original.description(),
                original.version(), original.questions());
        assertThatThrownBy(() -> instruments.register(changed)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT definition_json FROM instrument_snapshot "
                + "WHERE survey_slug = ? AND instrument_version = ?", String.class, original.slug(), original.version()))
                .isEqualTo(frozen);
        SurveyDefinition nextVersion = new SurveyDefinition(original.slug(), changed.title(), original.description(),
                "test-phase3-new-version", original.questions());
        assertThat(instruments.register(nextVersion).hash()).isNotEqualTo(first.hash());
    }

    @Test
    void aCapturedSubmissionStartsWithUndeclaredOriginAndHasVerifiedFrozenProvenance() {
        String id = save("titulados");
        var input = captures.input(id);
        assertThat(input.hasSnapshot()).isTrue();
        assertThat(input.origin()).isEqualTo("NO_DECLARADO");
        assertThat(input.provenance()).isEqualTo("CAPTURA_LOGICA");
        assertThat(input.captureHash()).matches("[a-f0-9]{64}");
        assertThat(input.definition()).isEqualTo(catalog.get("titulados"));
        assertThat(input.states()).containsEntry("edad", "CON_VALOR");
        assertThat(input.answers()).containsEntry("edad", List.of("30"));
        assertThatThrownBy(() -> input.answers().put("edad", List.of("99")))
                .isInstanceOf(UnsupportedOperationException.class);
        jdbc.update("UPDATE survey_answer SET answer_value = '99' WHERE response_id = ? AND question_id = 'edad'", id);
        assertThat(captures.input(id).answers()).containsEntry("edad", List.of("30"));
        assertThat(captures.input(id).captureHash()).isEqualTo(input.captureHash());
    }

    @Test
    void retryWithAChangedAnswerIsAConflictAndCannotRewriteTheFirstCapture() throws Exception {
        OpenForm form = openForm("empleadores");
        submit(form);
        String id = jdbc.queryForObject("SELECT id FROM survey_response", String.class);
        String originalCapture = jdbc.queryForObject("SELECT capture_json FROM response_capture WHERE response_id = ?",
                String.class, id);
        form.values().set("nombre_organizacion", "Otra organización válida");
        mvc.perform(post("/encuestas/empleadores").session(form.session()).params(form.values()).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT capture_json FROM response_capture WHERE response_id = ?", String.class, id))
                .isEqualTo(originalCapture);
        mvc.perform(get("/gracias").session(form.session())).andExpect(status().isOk())
                .andExpect(model().attribute("responseReference", id));
    }

    @Test
    void retryWithTheSameCheckboxSetInAnotherOrderReturnsTheOriginalReference() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().put("cargos_desempenados", List.of("ANALISTA", "TECNICO"));
        submit(form);
        String id = jdbc.queryForObject("SELECT id FROM survey_response", String.class);
        String captureHash = captures.input(id).captureHash();
        form.values().put("cargos_desempenados", List.of("TECNICO", "ANALISTA"));
        submit(form);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isEqualTo(1);
        assertThat(captures.input(id).captureHash()).isEqualTo(captureHash);
        mvc.perform(get("/gracias").session(form.session())).andExpect(status().isOk())
                .andExpect(model().attribute("responseReference", id));
    }

    @Test
    void theEntireAnalyticalBundleOmitsContactIdentificationAndUnreviewedFreeText() {
        String marker = "SECRETO-" + UUID.randomUUID();
        String graduate = save(catalog.get("titulados"), Map.of(
                "email_contacto", List.of(marker + "@example.test"), "telefono_contacto", List.of("+59179999999"),
                "linkedin", List.of("https://example.test/" + marker), "programa_cursado", List.of(marker)));
        String employer = save(catalog.get("empleadores"), Map.of(
                "email_contacto", List.of(marker + "-empresa@example.test"), "nombre_organizacion", List.of(marker),
                "areas_emergentes", List.of(marker), "herramientas_tecnologicas", List.of(marker),
                "competencias_requeridas", List.of(marker)));
        classify(graduate, "REAL");
        classify(employer, "REAL");
        String run = wrangling.create("REAL", "BORRADOR", null);
        String bundle = wrangling.bundle(run);
        assertThat(bundle).doesNotContain(marker, "+59179999999", "test-secret-no-real");
        var detail = wrangling.detail(run);
        assertThat(detail.graduates()).hasSize(1);
        assertThat(detail.employers()).hasSize(1);
        assertThat(detail.graduates().getFirst()).doesNotContainKeys("email_contacto", "telefono_contacto", "linkedin", "programa_cursado");
        assertThat(detail.employers().getFirst()).doesNotContainKeys("email_contacto", "nombre_organizacion", "areas_emergentes",
                "herramientas_tecnologicas", "competencias_requeridas");
        assertThat(detail.coding()).isEmpty();
    }

    @Test
    void repeatedPreparationHasDifferentRunIdentifiersButTheSameLogicalContentHash() {
        String id = save("titulados");
        classify(id, "REAL");
        String first = wrangling.create("REAL", "BORRADOR", null);
        String second = wrangling.create("REAL", "BORRADOR", null);
        assertThat(second).isNotEqualTo(first);
        assertThat(wrangling.detail(first).summary().logicalHash())
                .isEqualTo(wrangling.detail(second).summary().logicalHash());
        assertThat(wrangling.detail(first).summary().includedCount()).isEqualTo(1);
        assertThat(wrangling.detail(first).members()).hasSize(1);
    }

    @Test
    void newResponsesAndNewClassificationsCannotChangeAnAlreadyPreparedDataset() {
        String firstResponse = save("titulados");
        classify(firstResponse, "REAL");
        String firstRun = wrangling.create("REAL", "BORRADOR", null);
        String frozenBundle = wrangling.bundle(firstRun);
        String secondResponse = save("titulados");
        classify(secondResponse, "REAL");
        String secondRun = wrangling.create("REAL", "BORRADOR", null);
        assertThat(wrangling.detail(secondRun).graduates()).hasSize(2);
        assertThat(wrangling.detail(secondRun).summary().logicalHash())
                .isNotEqualTo(wrangling.detail(firstRun).summary().logicalHash());
        classify(firstResponse, "SINTETICO");
        assertThat(wrangling.bundle(firstRun)).isEqualTo(frozenBundle);
        assertThat(wrangling.detail(firstRun).graduates()).hasSize(1);
        assertThat(wrangling.detail(firstRun).members()).hasSize(1);
    }

    @Test
    void revisionsAppendWithoutRewritingRawDataOrPreviouslyPreparedResults() {
        String response = save("titulados");
        classify(response, "REAL");
        String firstRun = wrangling.create("REAL", "BORRADOR", null);
        String original = wrangling.bundle(firstRun);
        String capture = jdbc.queryForObject("SELECT capture_json FROM response_capture WHERE response_id = ?", String.class, response);
        wrangling.correct(response, "edad", List.of("31"), "Primera revisión documentada", "test-admin");
        wrangling.correct(response, "edad", List.of("32"), "Segunda revisión documentada", "test-admin");
        String correctedRun = wrangling.create("REAL", "BORRADOR", null);
        assertThat(((Number) wrangling.detail(correctedRun).graduates().getFirst().get("edad")).intValue()).isEqualTo(32);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_correction WHERE response_id = ?", Long.class, response))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'edad'",
                String.class, response)).isEqualTo("30");
        assertThat(jdbc.queryForObject("SELECT capture_json FROM response_capture WHERE response_id = ?", String.class, response))
                .isEqualTo(capture);
        assertThat(wrangling.bundle(firstRun)).isEqualTo(original);
        assertThat(((Number) wrangling.detail(firstRun).graduates().getFirst().get("edad")).intValue()).isEqualTo(30);
    }

    @Test
    void realSyntheticAndUndeclaredResponsesAreKeptInSeparateDatasets() {
        String real = save("titulados");
        String synthetic = save("titulados");
        String unknown = save("titulados");
        classify(real, "REAL");
        classify(synthetic, "SINTETICO");
        for (var entry : Map.of("REAL", real, "SINTETICO", synthetic, "NO_DECLARADO", unknown).entrySet()) {
            var detail = wrangling.detail(wrangling.create(entry.getKey(), "BORRADOR", null));
            assertThat(detail.graduates()).hasSize(1);
            assertThat(detail.graduates().getFirst()).containsEntry("response_id", entry.getValue())
                    .containsEntry("origin", entry.getKey());
            assertThat(detail.summary().includedCount()).isEqualTo(1);
        }
    }

    @Test
    void invalidImportedCategoriesAndTemporalRelationsBecomeIncidencesWithoutImputation() {
        SurveyDefinition survey = catalog.get("titulados");
        var validated = validator.validate(survey, validAnswers(survey));
        Map<String, List<String>> invalid = new LinkedHashMap<>(validated.answers());
        invalid.put("genero", List.of("CATEGORIA_AJENA"));
        invalid.put("edad", List.of("121"));
        invalid.put("meses_vida_profesional", List.of("12"));
        invalid.put("meses_desempleado", List.of("13"));
        String response = importCapture(survey, new ValidationResult(invalid, Map.of(), validated.states()));
        classify(response, "REAL");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", null));
        Map<String, Object> row = detail.graduates().getFirst();
        for (String question : List.of("genero", "edad", "meses_vida_profesional", "meses_desempleado")) {
            assertThat(row.get(question)).isNull();
            assertThat(row.get(question + "__estado")).isEqualTo("VALOR_INVALIDO");
            assertThat(detail.issues().stream().anyMatch(issue -> question.equals(issue.get("question_id"))))
                    .as("incidencia para %s", question).isTrue();
        }
        assertThat(detail.issues().stream().map(issue -> issue.get("rule_code")))
                .contains("VALOR_FUERA_DOMINIO", "DESEMPLEO_SUPERA_VIDA_PROFESIONAL");
    }

    @Test
    void incoherentImportedStatesAndMissingRequiredAnswersRemainVisibleAsQualityIssues() {
        SurveyDefinition survey = catalog.get("titulados");
        var validated = validator.validate(survey, validAnswers(survey));
        Map<String, List<String>> answers = new LinkedHashMap<>(validated.answers());
        Map<String, AnswerState> states = new LinkedHashMap<>(validated.states());
        states.put("edad", AnswerState.SIN_RESPUESTA);
        states.put("anio_egreso", AnswerState.SIN_RESPUESTA);
        answers.remove("anio_egreso");
        String response = importCapture(survey, new ValidationResult(answers, Map.of(), states));
        classify(response, "REAL");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", null));
        Map<String, Object> row = detail.graduates().getFirst();
        assertThat(row.get("edad")).isNull();
        assertThat(row.get("edad__estado")).isEqualTo("VALOR_INVALIDO");
        assertThat(row.get("anio_egreso")).isNull();
        assertThat(row.get("anio_egreso__estado")).isEqualTo("SIN_RESPUESTA");
        assertThat(detail.issues().stream().map(issue -> issue.get("rule_code")))
                .contains("VALOR_ESTADO_INCOHERENTE", "OBLIGATORIA_SIN_RESPUESTA");
    }

    @Test
    void anInvalidControlCannotConfirmThatItsDependentQuestionsDoNotApply() {
        SurveyDefinition survey = catalog.get("titulados");
        MultiValueMap<String, String> submitted = validAnswers(survey);
        submitted.set("interes_posgrado", "NO");
        ValidationResult validated = validator.validate(survey, submitted);
        assertThat(validated.errors()).isEmpty();
        Map<String, List<String>> importedAnswers = new LinkedHashMap<>(validated.answers());
        importedAnswers.put("interes_posgrado", List.of("CODIGO_INVALIDO"));
        String response = importCapture(survey, new ValidationResult(importedAnswers, Map.of(), validated.states()));
        var detail = wrangling.detail(wrangling.create("NO_DECLARADO", "BORRADOR", null));
        Map<String, Object> row = detail.graduates().getFirst();
        for (String child : List.of("area_interes", "institucion_interes", "nivel_interes", "financiamiento_futuro")) {
            assertThat(captures.input(response).states().get(child)).isEqualTo("NO_APLICA");
            assertThat(row.get(child)).isNull();
            assertThat(row.get(child + "__estado")).isEqualTo("VALOR_INVALIDO");
            assertThat(detail.issues().stream().anyMatch(issue -> child.equals(issue.get("question_id"))
                    && "APLICABILIDAD_NO_VERIFICABLE".equals(issue.get("rule_code"))
                    && "PENDIENTE_REGLA".equals(issue.get("severity"))))
                    .as("aplicabilidad no verificable de %s", child).isTrue();
        }
        assertThat(((Number) row.get("edad")).intValue()).isEqualTo(30);
        assertThat(row.get("edad__estado")).isEqualTo("CON_VALOR");
    }

    @Test
    void aUtcCutoffIncludesOnlyTheExactCapturedInputsAvailableBeforeIt() {
        SurveyDefinition survey = catalog.get("titulados");
        ValidationResult valid = validator.validate(survey, validAnswers(survey));
        String before = importCapture(survey, valid, OffsetDateTime.parse("2026-10-01T02:00:00.123456+02:00"));
        String after = importCapture(survey, valid, OffsetDateTime.parse("2026-10-12T00:00:00.123456Z"));
        classify(before, "REAL");
        classify(after, "REAL");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", Instant.parse("2026-10-05T00:00:00Z")));
        assertThat(detail.summary().inputCount()).isEqualTo(1);
        assertThat(detail.summary().includedCount()).isEqualTo(1);
        assertThat(detail.graduates()).hasSize(1);
        assertThat(detail.graduates().getFirst()).containsEntry("response_id", before);
        assertThat(detail.members()).hasSize(1);
        assertThat(detail.members().getFirst()).containsEntry("response_id", before);
        assertThat(detail.manifest()).containsEntry("cutoff_utc", "2026-10-05T00:00:00Z");
    }

    @Test
    void historicalResponsesWithoutASnapshotNeverBorrowTheCurrentCatalogOrInventStates() {
        String legacy = UUID.randomUUID().toString();
        insertResponseHeader(legacy, "titulados", "2026-10-borrador-1", OffsetDateTime.now(ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO survey_answer (response_id, question_id, question_index, question_label, item_index, answer_value, display_value)
                VALUES (?, 'edad', 0, 'Pregunta antigua', 0, '35', '35')
                """, legacy);
        String current = save("titulados");
        classify(legacy, "REAL");
        classify(current, "REAL");
        var input = captures.input(legacy);
        assertThat(input.hasSnapshot()).isFalse();
        assertThat(input.definition()).isNull();
        assertThat(input.provenance()).isEqualTo("RECONSTRUIDA_HISTORICA");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", null));
        Map<String, Object> row = row(detail.graduates(), legacy);
        assertThat(row.get("edad")).isNull();
        assertThat(row.get("edad__estado")).isEqualTo("SIN_ESTADO_HISTORICO");
        assertThat(detail.issues().stream().anyMatch(issue -> legacy.equals(issue.get("response_id"))
                && "DEFINICION_HISTORICA_NO_REGISTRADA".equals(issue.get("rule_code")))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state WHERE response_id = ?", Long.class, legacy)).isZero();
    }

    @Test
    void aQuestionAbsentFromAKnownOlderDefinitionIsNotCollectedRatherThanAnUnansweredQuestion() {
        SurveyDefinition current = catalog.get("titulados");
        SurveyDefinition older = new SurveyDefinition(current.slug(), current.title(), current.description(),
                "test-phase3-older-version", current.questions().stream().filter(question -> !question.id().equals("edad")).toList());
        String oldResponse = save(older, Map.of());
        String newResponse = save("titulados");
        classify(oldResponse, "REAL");
        classify(newResponse, "REAL");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", null));
        Map<String, Object> oldRow = row(detail.graduates(), oldResponse);
        assertThat(oldRow.get("edad")).isNull();
        assertThat(oldRow.get("edad__estado")).isEqualTo("NO_RECOLECTADA");
        assertThat(row(detail.graduates(), newResponse).get("edad__estado")).isEqualTo("CON_VALOR");
        assertThat(detail.dictionary().stream().filter(variable -> "edad".equals(variable.get("variable")))
                .map(variable -> variable.get("instrument_version"))).containsExactly(current.version());
    }

    @Test
    void noSabeAndCannotRateRemainNominalValuesWithoutAgreementScores() {
        String employer = save(catalog.get("empleadores"), Map.of("criterio_01", List.of("NO_SABE")));
        String graduate = save(catalog.get("titulados"), Map.of("competencia_tecnica_01", List.of("NO_PUEDE_VALORAR")));
        classify(employer, "REAL");
        classify(graduate, "REAL");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", null));
        assertThat(row(detail.employers(), employer)).containsEntry("criterio_01", "NO_SABE")
                .containsEntry("criterio_01__estado", "CON_VALOR");
        assertThat(row(detail.graduates(), graduate)).containsEntry("competencia_tecnica_01", "NO_PUEDE_VALORAR")
                .containsEntry("competencia_tecnica_01__estado", "CON_VALOR");
        for (String code : List.of("NO_SABE", "NO_PUEDE_VALORAR")) {
            List<?> options = detail.dictionary().stream().flatMap(variable -> ((List<?>) variable.get("catalog")).stream())
                    .filter(option -> code.equals(((Map<?, ?>) option).get("code"))).toList();
            assertThat(options).isNotEmpty();
            assertThat(options.stream().allMatch(option -> Boolean.TRUE.equals(((Map<?, ?>) option).get("nominal_without_score"))))
                    .isTrue();
        }
    }

    @Test
    void multipleChoiceIndicatorsDistinguishAnOmittedQuestionFromAnUnselectedOptionWithoutMultiplyingParticipants() {
        String missing = save(catalog.get("titulados"), Map.of("aspectos_utiles", List.of()));
        String answered = save(catalog.get("titulados"), Map.of("aspectos_utiles", List.of("APLICACION_PRACTICA", "PASANTIAS")));
        classify(missing, "REAL");
        classify(answered, "REAL");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", null));
        assertThat(detail.graduates()).hasSize(2);
        Map<String, Object> missingRow = row(detail.graduates(), missing);
        assertThat(missingRow.get("aspectos_utiles")).isNull();
        assertThat(missingRow.get("aspectos_utiles__estado")).isEqualTo("SIN_RESPUESTA");
        assertThat(missingRow.get("aspectos_utiles__APLICACION_PRACTICA")).isNull();
        Map<String, Object> answeredRow = row(detail.graduates(), answered);
        assertThat(((Number) answeredRow.get("aspectos_utiles__APLICACION_PRACTICA")).intValue()).isEqualTo(1);
        assertThat(((Number) answeredRow.get("aspectos_utiles__HORARIOS")).intValue()).isZero();
        assertThat(detail.selections().stream().filter(selection -> answered.equals(selection.get("response_id"))
                && "aspectos_utiles".equals(selection.get("question_id")))).hasSize(2);
        assertThat(detail.selections().stream().filter(selection -> missing.equals(selection.get("response_id"))
                && "aspectos_utiles".equals(selection.get("question_id")))).isEmpty();
    }

    @Test
    void manualTextCodingCountsEachParticipantAndThemeOnceAndNeverCopiesTheSourceText() {
        String privateText = "Texto privado original " + UUID.randomUUID();
        String response = save(catalog.get("empleadores"), Map.of("areas_emergentes", List.of(privateText)));
        classify(response, "REAL");
        wrangling.codeText(response, "areas_emergentes", List.of("IA", "IA", "SEGURIDAD"),
                "codebook-test-1", "Revisión manual documentada", "test-admin");
        var detail = wrangling.detail(wrangling.create("REAL", "BORRADOR", null));
        assertThat(detail.coding()).hasSize(2);
        assertThat(detail.coding().stream().map(code -> code.get("theme"))).containsExactlyInAnyOrder("IA", "SEGURIDAD");
        assertThat(detail.coding().stream().map(code -> code.get("response_id"))).containsOnly(response);
        assertThat(wrangling.bundle(detail.summary().id())).doesNotContain(privateText);
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'areas_emergentes'",
                String.class, response)).isEqualTo(privateText);
    }

    @Test
    void textCorrectionRequiresReviewingItsThemesAgainWithoutChangingThePreviousDataset() {
        String response = save(catalog.get("empleadores"), Map.of("areas_emergentes", List.of("Texto original para revisar")));
        classify(response, "REAL");
        wrangling.codeText(response, "areas_emergentes", List.of("IA"), "codebook-test-1", "Primera revisión", "test-admin");
        String firstRun = wrangling.create("REAL", "BORRADOR", null);
        String frozen = wrangling.bundle(firstRun);
        assertThat(wrangling.detail(firstRun).coding()).hasSize(1);
        wrangling.correct(response, "areas_emergentes", List.of("Texto corregido sobre seguridad"), "Corrección del texto", "test-admin");
        String changedRun = wrangling.create("REAL", "BORRADOR", null);
        assertThat(wrangling.detail(changedRun).coding()).isEmpty();
        assertThat(wrangling.bundle(firstRun)).isEqualTo(frozen);
        wrangling.codeText(response, "areas_emergentes", List.of("SEGURIDAD"), "codebook-test-1", "Revisión del nuevo texto", "test-admin");
        String reviewedRun = wrangling.create("REAL", "BORRADOR", null);
        assertThat(wrangling.detail(reviewedRun).coding().stream().map(code -> code.get("theme"))).containsExactly("SEGURIDAD");
        assertThat(wrangling.bundle(reviewedRun)).doesNotContain("Texto original para revisar", "Texto corregido sobre seguridad");
    }

    @Test
    void anUnknownVariableDoesNotBecomeAnalyticalMerelyBecauseItsTypeIsNumeric() {
        SurveyDefinition current = catalog.get("titulados");
        List<Question> questions = new ArrayList<>(current.questions());
        String privateId = "identificador_privado_no_catalogado";
        questions.add(new Question(privateId, "Datos adicionales", "Identificador privado", "number", false,
                List.of(), null, null, 0, 1, null, 0, 999999999));
        SurveyDefinition extended = new SurveyDefinition(current.slug(), current.title(), current.description(),
                "test-phase3-unknown-field", questions);
        String response = save(extended, Map.of(privateId, List.of("812345678")));
        classify(response, "REAL");
        String run = wrangling.create("REAL", "BORRADOR", null);
        assertThat(wrangling.detail(run).graduates().getFirst()).doesNotContainKeys(privateId, privateId + "__estado");
        assertThat(wrangling.bundle(run)).doesNotContain("812345678");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aFieldThatBecomesPrivateTextInAnotherVersionCannotBorrowTheEarlierNumericPrivacyRule() throws Exception {
        SurveyDefinition current = catalog.get("titulados");
        String marker = "TEXTO-PRIVADO-DE-OTRA-VERSION-" + UUID.randomUUID();
        List<Question> changedQuestions = current.questions().stream().map(question -> {
            if (!question.id().equals("edad")) return question;
            return new Question(question.id(), question.section(), question.label(), "text", false,
                    List.of(), null, null, 0, 1, null, null, null);
        }).toList();
        SurveyDefinition privateVersion = new SurveyDefinition(current.slug(), current.title(), current.description(),
                "test-phase3-private-age", changedQuestions);
        String numericResponse = save("titulados");
        String textResponse = save(privateVersion, Map.of("edad", List.of(marker)));
        classify(numericResponse, "REAL");
        classify(textResponse, "REAL");
        String run = wrangling.create("REAL", "BORRADOR", null);
        assertThat(wrangling.detail(run).graduates()).hasSize(2);
        assertThat(wrangling.bundle(run)).doesNotContain(marker);
        for (String product : List.of("conjunto.json", "titulados.csv", "diccionario.csv", "calidad.csv")) {
            String body = mvc.perform(get("/admin/datos/" + run + "/" + product)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).doesNotContain(marker);
        }
    }

    @Test
    void failureWhileFreezingMembersDoesNotPublishAPartialDataset() {
        String response = save("titulados");
        classify(response, "REAL");
        jdbc.execute("ALTER TABLE wrangling_member ADD CONSTRAINT test_reject_included_member CHECK (included = FALSE)");
        try {
            assertThatThrownBy(() -> wrangling.create("REAL", "BORRADOR", null)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_run", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_member", Long.class)).isZero();
            assertThat(wrangling.recent()).isEmpty();
        } finally {
            jdbc.execute("ALTER TABLE wrangling_member DROP CONSTRAINT test_reject_included_member");
        }
        String successful = wrangling.create("REAL", "BORRADOR", null);
        assertThat(wrangling.detail(successful).summary().includedCount()).isEqualTo(1);
    }

    @Test
    void aCaptureWhoseHashNoLongerMatchesCannotProduceAnAnalyticalDataset() {
        String response = save("titulados");
        classify(response, "REAL");
        jdbc.update("UPDATE response_capture SET capture_hash = ? WHERE response_id = ?", "0".repeat(64), response);
        assertThatThrownBy(() -> wrangling.create("REAL", "BORRADOR", null)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_run", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_member", Long.class)).isZero();
    }

    @Test
    void alteringThePersistedDatasetInvalidatesItsHashAndPreventsDelivery() {
        String response = save("titulados");
        classify(response, "REAL");
        String run = wrangling.create("REAL", "BORRADOR", null);
        jdbc.update("UPDATE wrangling_run SET bundle_json = '{}' WHERE id = ?", run);
        assertThatThrownBy(() -> wrangling.bundle(run)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> wrangling.detail(run)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown-question", "unknown-category", "out-of-range", "duplicate-selection", "wrong-cardinality", "no-reason", "no-actor"})
    void invalidRevisionsDoNotEnterTheAuditOrRewriteTheCapture(String invalidCase) {
        String response = save("titulados");
        String capture = jdbc.queryForObject("SELECT capture_json FROM response_capture WHERE response_id = ?", String.class, response);
        String question = switch (invalidCase) {
            case "unknown-question" -> "campo_ajeno";
            case "unknown-category" -> "genero";
            case "duplicate-selection", "wrong-cardinality" -> "asignaturas_ventaja";
            default -> "edad";
        };
        List<String> values = switch (invalidCase) {
            case "unknown-category" -> List.of("CATEGORIA_AJENA");
            case "out-of-range" -> List.of("121");
            case "duplicate-selection" -> List.of("INTRODUCCION", "INTRODUCCION", "BASE_DATOS", "INTELIGENCIA_ARTIFICIAL", "ELECTIVAS");
            case "wrong-cardinality" -> List.of("INTRODUCCION", "BASE_DATOS");
            default -> List.of("35");
        };
        String reason = invalidCase.equals("no-reason") ? " " : "Revisión documentada";
        String actor = invalidCase.equals("no-actor") ? " " : "test-admin";
        assertThatThrownBy(() -> wrangling.correct(response, question, values, reason, actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_correction", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT capture_json FROM response_capture WHERE response_id = ?", String.class, response))
                .isEqualTo(capture);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/datos", "/admin/datos/123", "/admin/datos/123/conjunto.json", "/admin/datos/123/conjunto.zip",
            "/admin/datos/123/titulados.csv", "/admin/datos/123/empleadores.csv", "/admin/datos/123/selecciones.csv",
            "/admin/datos/123/calidad.csv", "/admin/datos/123/diccionario.csv", "/admin/datos/123/miembros.csv", "/admin/datos/123/codificacion.csv"})
    void anonymousUsersCannotReadDatasetsOrAnyOfTheirDownloads(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/datos", "/admin/datos/123/conjunto.json", "/admin/datos/123/conjunto.zip", "/admin/datos/123/titulados.csv"})
    @WithMockUser(roles = "USER")
    void anAuthenticatedUserWithoutTheAdminRoleCannotReadDatasets(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/datos", "/admin/respuestas/123/origen", "/admin/respuestas/123/correcciones", "/admin/respuestas/123/codificacion"})
    @WithMockUser(roles = "ADMIN")
    void stateChangingDataOperationsRequireCsrf(String path) throws Exception {
        mvc.perform(post(path).param("origin", "REAL").param("mode", "BORRADOR").param("reason", "Motivo"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_run", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_correction", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_origin_classification", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_text_coding", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/datos", "/admin/respuestas/123/origen", "/admin/respuestas/123/correcciones", "/admin/respuestas/123/codificacion"})
    void anonymousUsersCannotCreateDatasetsOrChangeResponseMetadataEvenWithCsrf(String path) throws Exception {
        mvc.perform(post(path).with(csrf()).param("origin", "REAL").param("mode", "BORRADOR").param("reason", "Motivo"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(username = "reviewer", roles = "ADMIN")
    void administratorsCanClassifyCorrectCodeAndPrepareThroughTheProtectedPanel() throws Exception {
        String graduate = save("titulados");
        String employer = save("empleadores");
        String originalEmployerText = jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'areas_emergentes'",
                String.class, employer);
        String privateReason = "<script>review-history-private</script>";
        for (String id : List.of(graduate, employer)) {
            mvc.perform(post("/admin/respuestas/" + id + "/origen").with(csrf())
                            .param("origin", "REAL").param("reason", privateReason))
                    .andExpect(status().is3xxRedirection());
        }
        mvc.perform(post("/admin/respuestas/" + graduate + "/correcciones").with(csrf())
                        .param("questionId", "edad").param("values", "35").param("reason", "Corrección revisada"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/respuestas/" + employer + "/correcciones").with(csrf())
                        .param("questionId", "areas_emergentes").param("values", "IA\nSeguridad")
                        .param("reason", "Corrección de texto de varios párrafos"))
                .andExpect(status().is3xxRedirection());
        Map<String, Object> textCorrection = wrangling.reviewHistory(employer).stream()
                .filter(event -> "CORRECCION".equals(event.get("event")) && "areas_emergentes".equals(event.get("question_id")))
                .findFirst().orElseThrow();
        assertThat(textCorrection.get("values")).isEqualTo(List.of("IA\nSeguridad"));
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'areas_emergentes'",
                String.class, employer)).isEqualTo(originalEmployerText);
        mvc.perform(post("/admin/respuestas/" + employer + "/codificacion").with(csrf())
                        .param("questionId", "areas_emergentes").param("themes", "IA\nSEGURIDAD")
                        .param("codebookVersion", "codebook-test-1").param("reason", "Codificación revisada"))
                .andExpect(status().is3xxRedirection());
        for (String id : List.of(graduate, employer)) {
            String history = mvc.perform(get("/admin/respuestas/" + id + "/revision")).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(history).contains("reviewer", "&lt;script&gt;review-history-private&lt;/script&gt;")
                    .doesNotContain(privateReason);
        }
        mvc.perform(post("/admin/datos").with(csrf()).param("origin", "REAL").param("mode", "BORRADOR"))
                .andExpect(status().is3xxRedirection());
        String run = jdbc.queryForObject("SELECT id FROM wrangling_run", String.class);
        mvc.perform(get("/admin/datos")).andExpect(status().isOk());
        mvc.perform(get("/admin/datos/" + run)).andExpect(status().isOk());
        var detail = wrangling.detail(run);
        assertThat(detail.graduates()).hasSize(1);
        assertThat(detail.employers()).hasSize(1);
        assertThat(((Number) detail.graduates().getFirst().get("edad")).intValue()).isEqualTo(35);
        assertThat(detail.coding()).hasSize(2);
        for (String table : List.of("wrangling_origin_classification", "wrangling_correction", "wrangling_text_coding", "wrangling_run")) {
            assertThat(jdbc.queryForList("SELECT actor FROM " + table, String.class)).containsOnly("reviewer");
        }
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'edad'",
                String.class, graduate)).isEqualTo("30");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aDraftInstrumentCannotBePublishedAsAnOfficialDataset() throws Exception {
        String response = save("titulados");
        classify(response, "REAL");
        mvc.perform(post("/admin/datos").with(csrf()).param("origin", "REAL").param("mode", "OFICIAL"))
                .andExpect(status().is4xxClientError());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_run", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wrangling_member", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM instrument_snapshot WHERE survey_slug = 'titulados' AND instrument_version = ?",
                String.class, catalog.get("titulados").version())).isEqualTo("BORRADOR");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void jsonCsvAndZipDownloadsContainTheFrozenDatasetAndOmitPrivateSourceValues() throws Exception {
        String marker = "CONTACTO-PRIVADO-" + UUID.randomUUID();
        String response = save(catalog.get("empleadores"), Map.of("email_contacto", List.of(marker + "@example.test"),
                "nombre_organizacion", List.of(marker), "areas_emergentes", List.of(marker)));
        classify(response, "REAL");
        String run = wrangling.create("REAL", "BORRADOR", null);
        MvcResult json = mvc.perform(get("/admin/datos/" + run + "/conjunto.json")).andExpect(status().isOk()).andReturn();
        assertThat(json.getResponse().getContentAsString(StandardCharsets.UTF_8)).isEqualTo(wrangling.bundle(run))
                .doesNotContain(marker);
        assertThat(json.getResponse().getHeader("Content-Disposition")).contains("attachment");
        for (String product : List.of("titulados", "empleadores", "selecciones", "calidad", "diccionario", "miembros", "codificacion")) {
            MvcResult csv = mvc.perform(get("/admin/datos/" + run + "/" + product + ".csv"))
                    .andExpect(status().isOk()).andReturn();
            assertThat(csv.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(marker);
            assertThat(csv.getResponse().getHeader("Content-Disposition")).contains("attachment");
        }
        MvcResult zip = mvc.perform(get("/admin/datos/" + run + "/conjunto.zip")).andExpect(status().isOk()).andReturn();
        int entries = 0;
        try (ZipInputStream archive = new ZipInputStream(new ByteArrayInputStream(zip.getResponse().getContentAsByteArray()), StandardCharsets.UTF_8)) {
            while (archive.getNextEntry() != null) {
                entries++;
                assertThat(new String(archive.readAllBytes(), StandardCharsets.UTF_8)).doesNotContain(marker);
            }
        }
        assertThat(entries).isPositive();
    }

    private String importCapture(SurveyDefinition survey, ValidationResult imported) {
        OffsetDateTime at = OffsetDateTime.ofInstant(Instant.parse("2026-10-13T00:00:00.123456Z"), ZoneOffset.UTC);
        return importCapture(survey, imported, at);
    }

    private String importCapture(SurveyDefinition survey, ValidationResult imported, OffsetDateTime at) {
        String id = UUID.randomUUID().toString();
        insertResponseHeader(id, survey.slug(), survey.version(), at);
        captures.persist(id, survey, imported, at);
        return id;
    }

    private void insertResponseHeader(String id, String slug, String version, OffsetDateTime at) {
        jdbc.update("""
                INSERT INTO survey_response (id, submission_token, survey_slug, instrument_version, submitted_at, consent)
                VALUES (?, ?, ?, ?, ?, TRUE)
                """, id, UUID.randomUUID().toString(), slug, version, at);
    }

    private static Map<String, Object> row(List<Map<String, Object>> rows, String id) {
        return rows.stream().filter(candidate -> id.equals(candidate.get("response_id"))).findFirst().orElseThrow();
    }

    private String save(String slug) {
        return save(catalog.get(slug), Map.of());
    }

    private String save(SurveyDefinition survey, Map<String, List<String>> overrides) {
        MultiValueMap<String, String> values = validAnswers(survey);
        overrides.forEach(values::put);
        var validated = validator.validate(survey, values);
        assertThat(validated.errors()).isEmpty();
        return responses.save(survey, UUID.randomUUID().toString(), validated);
    }

    private void classify(String responseId, String origin) {
        wrangling.classify(responseId, origin, "Clasificación explícita para la prueba", "test-admin");
    }

    private MultiValueMap<String, String> validAnswers(SurveyDefinition survey) {
        MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
        for (Question question : survey.questions()) {
            if (question.dependsOn() != null && Arrays.stream(question.dependsValue().split("\\|"))
                    .noneMatch(value -> values.getOrDefault(question.dependsOn(), List.of()).contains(value))) continue;
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

    private OpenForm openForm(String slug) throws Exception {
        MvcResult result = mvc.perform(get("/encuestas/" + slug)).andExpect(status().isOk()).andReturn();
        SurveyDefinition survey = catalog.get(slug);
        MultiValueMap<String, String> values = validAnswers(survey);
        values.set("formToken", (String) result.getModelAndView().getModel().get("formToken"));
        return new OpenForm((MockHttpSession) result.getRequest().getSession(false), survey, values);
    }

    private void submit(OpenForm form) throws Exception {
        mvc.perform(post("/encuestas/" + form.survey().slug()).session(form.session()).params(form.values()).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/gracias"));
    }

    private record OpenForm(MockHttpSession session, SurveyDefinition survey, MultiValueMap<String, String> values) {}
}

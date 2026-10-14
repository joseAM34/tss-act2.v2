package bo.edu.umss.mercado;

import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.security.AdministratorService;
import bo.edu.umss.mercado.survey.AnswerState;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.SurveyValidator;
import bo.edu.umss.mercado.survey.ValidationResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Evidencia de los estados, saltos, comprobantes y guardado coherente de la fase 2. */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:survey-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseTwoAcceptanceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SurveyCatalog catalog;
    @Autowired SurveyValidator validator;
    @Autowired SurveyResponseRepository repository;
    @Autowired AdministratorService administrators;

    @BeforeEach
    void clearCollectedData() {
        jdbc.update("DELETE FROM survey_question_state");
        jdbc.update("DELETE FROM survey_answer");
        jdbc.update("DELETE FROM survey_response");
        jdbc.update("DELETE FROM administrator");
        administrators.register("test-admin", "test-secret-no-real");
    }

    @ParameterizedTest
    @ValueSource(strings = {"titulados", "empleadores"})
    void eachNewResponseStoresEveryQuestionStateAndItsHistoricalLabel(String slug) throws Exception {
        OpenForm form = openForm(slug);
        submit(form);
        String responseId = onlyResponseId();
        Map<String, AnswerState> expected = validator.validate(form.survey(), form.values()).states();
        assertThat(expected).hasSize(form.survey().questions().size());
        List<Map<String, Object>> stored = jdbc.queryForList("""
                SELECT question_id, question_index, question_label, answer_state
                FROM survey_question_state WHERE response_id = ? ORDER BY question_index
                """, responseId);
        assertThat(stored).hasSize(form.survey().questions().size());
        for (int i = 0; i < form.survey().questions().size(); i++) {
            Question question = form.survey().questions().get(i);
            Map<String, Object> row = stored.get(i);
            assertThat(row.get("question_id")).isEqualTo(question.id());
            assertThat(((Number) row.get("question_index")).intValue()).isEqualTo(i);
            assertThat(row.get("question_label")).isEqualTo(question.label());
            assertThat(row.get("answer_state")).isEqualTo(expected.get(question.id()).name());
            long values = answerCount(responseId, question.id());
            if (expected.get(question.id()) == AnswerState.CON_VALOR) {
                assertThat(values).as("valor de %s", question.id()).isPositive();
            } else {
                assertThat(values).as("sin valor aplicable de %s", question.id()).isZero();
            }
        }
    }

    @Test
    void emptyOptionalQuestionDiffersFromAnInactiveQuestionAfterItsControlChanges() throws Exception {
        OpenForm form = openForm("titulados");
        form.values().remove("meses_vida_profesional");
        form.values().remove("meses_desempleado");
        form.values().set("formacion_complementaria", "NO");
        for (Question question : form.survey().questions()) {
            if ("formacion_complementaria".equals(question.dependsOn())) {
                form.values().set(question.id(), "VALOR_INACTIVO_MANIPULADO");
            }
        }
        var validation = validator.validate(form.survey(), form.values());
        assertThat(validation.valid()).isTrue();
        assertThat(validation.states().get("meses_vida_profesional")).isEqualTo(AnswerState.SIN_RESPUESTA);
        assertThat(validation.states().get("nivel_cursado")).isEqualTo(AnswerState.NO_APLICA);
        assertThat(validation.answers()).doesNotContainKeys("meses_vida_profesional", "nivel_cursado");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, "meses_vida_profesional")).isEqualTo(AnswerState.SIN_RESPUESTA);
        for (Question question : form.survey().questions()) {
            if ("formacion_complementaria".equals(question.dependsOn())) {
                assertThat(state(responseId, question.id())).isEqualTo(AnswerState.NO_APLICA);
                assertThat(answerCount(responseId, question.id())).isZero();
            }
        }
    }

    @Test
    void decliningFuturePostgraduateStudyDoesNotDisableTheGeneralFacultyOpinion() throws Exception {
        OpenForm form = openForm("titulados");
        form.values().set("interes_posgrado", "NO");
        submit(form);
        String responseId = onlyResponseId();
        for (String question : List.of("area_interes", "institucion_interes", "nivel_interes", "financiamiento_futuro")) {
            assertThat(state(responseId, question)).isEqualTo(AnswerState.NO_APLICA);
            assertThat(answerCount(responseId, question)).isZero();
        }
        assertThat(state(responseId, "opinion_posgrado_fcyt")).isEqualTo(AnswerState.CON_VALOR);
    }

    @Test
    void businessOwnerHasAnApplicableBusinessBlockWithoutBeingForcedToDeclareACurrentFirstJob() throws Exception {
        OpenForm form = openForm("titulados", Map.of("estado_laboral", "EMPRENDIMIENTO"));
        submit(form);
        String responseId = onlyResponseId();
        for (Question question : form.survey().questions()) {
            if ("estado_laboral".equals(question.dependsOn()) && "EMPRENDIMIENTO".equals(question.dependsValue())) {
                assertThat(state(responseId, question.id())).isEqualTo(AnswerState.CON_VALOR);
            }
        }
        assertThat(state(responseId, "primer_empleo_actual")).isEqualTo(AnswerState.NO_APLICA);
        assertThat(answerCount(responseId, "primer_empleo_actual")).isZero();
    }

    @Test
    void explicitNoSabeHasAValueAndIsNotAnOmissionOrAScore() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().set("criterio_01", "NO_SABE");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, "criterio_01")).isEqualTo(AnswerState.CON_VALOR);
        assertThat(jdbc.queryForObject("""
                SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'criterio_01'
                """, String.class, responseId)).isEqualTo("NO_SABE");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void lowUtilitySubjectsAreOptionalAndAcceptAtMostTwoSelections(int selections) throws Exception {
        OpenForm form = openForm("titulados");
        Question subjects = form.survey().questions().stream()
                .filter(question -> question.id().equals("asignaturas_poca_utilidad")).findFirst().orElseThrow();
        List<String> selected = subjects.options().stream().limit(selections).map(Question.Option::value).toList();
        form.values().put(subjects.id(), selected);
        form.values().set("otra_asignatura_poca_utilidad", "Otra asignatura de prueba");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(jdbc.queryForList("""
                SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = ? ORDER BY item_index
                """, String.class, responseId, subjects.id())).containsExactlyElementsOf(selected);
        assertThat(state(responseId, subjects.id())).isEqualTo(selections == 0
                ? AnswerState.SIN_RESPUESTA : AnswerState.CON_VALOR);
        assertThat(state(responseId, "otra_asignatura_poca_utilidad")).isEqualTo(AnswerState.CON_VALOR);
    }

    @Test
    void threeLowUtilitySubjectsAreRejectedByTheBackend() throws Exception {
        OpenForm form = openForm("titulados");
        Question subjects = form.survey().questions().stream()
                .filter(question -> question.id().equals("asignaturas_poca_utilidad")).findFirst().orElseThrow();
        form.values().put(subjects.id(), subjects.options().stream().limit(3).map(Question.Option::value).toList());
        MvcResult rejected = mvc.perform(post("/encuestas/titulados").session(form.session())
                        .params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errors")).andReturn();
        assertThat(((Map<?, ?>) rejected.getModelAndView().getModel().get("errors"))
                .containsKey("asignaturas_poca_utilidad")).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isZero();
    }

    @Test
    void zeroMonthsAreExplicitValuesAndAreNotTreatedAsMissing() throws Exception {
        OpenForm form = openForm("titulados");
        form.values().set("meses_vida_profesional", "0");
        form.values().set("meses_desempleado", "0");
        submit(form);
        String responseId = onlyResponseId();
        for (String question : List.of("meses_vida_profesional", "meses_desempleado")) {
            assertThat(state(responseId, question)).isEqualTo(AnswerState.CON_VALOR);
            assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = ?",
                    String.class, responseId, question)).isEqualTo("0");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"negative", "decimal-life", "decimal-unemployed", "too-long", "missing-life", "longer-than-life"})
    void inconsistentOrNonIntegerProfessionalAndUnemploymentMonthsAreRejected(String invalidCase) throws Exception {
        OpenForm form = openForm("titulados");
        switch (invalidCase) {
            case "negative" -> form.values().set("meses_desempleado", "-1");
            case "decimal-life" -> form.values().set("meses_vida_profesional", "120.5");
            case "decimal-unemployed" -> form.values().set("meses_desempleado", "24.5");
            case "too-long" -> form.values().set("meses_vida_profesional", "961");
            case "missing-life" -> form.values().remove("meses_vida_profesional");
            case "longer-than-life" -> {
                form.values().set("meses_vida_profesional", "12");
                form.values().set("meses_desempleado", "13");
            }
            default -> throw new IllegalArgumentException(invalidCase);
        }
        MvcResult rejected = mvc.perform(post("/encuestas/titulados").session(form.session())
                        .params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errors")).andReturn();
        assertThat((Map<?, ?>) rejected.getModelAndView().getModel().get("errors")).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state", Long.class)).isZero();
    }

    @Test
    void someoneWhoNeverWorkedCanSubmitWithoutChoosingAJobCountOrRatingWorkCompetencies() throws Exception {
        OpenForm form = openForm("titulados", Map.of("experiencia_laboral", "SIN_EXPERIENCIA",
                "estado_laboral", "DESEMPLEADO", "sector", "NO_APLICA"));
        form.values().set("cantidad_empleos", "VALOR_DE_BLOQUE_INACTIVO");
        form.values().set("tiempo_primer_empleo", "VALOR_DE_BLOQUE_INACTIVO");
        form.survey().questions().stream().filter(question -> question.id().startsWith("competencia_"))
                .forEach(question -> form.values().set(question.id(), "VALOR_DE_BLOQUE_INACTIVO"));
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, "cantidad_empleos")).isEqualTo(AnswerState.NO_APLICA);
        assertThat(state(responseId, "tiempo_primer_empleo")).isEqualTo(AnswerState.NO_APLICA);
        for (Question question : form.survey().questions()) {
            if (question.id().startsWith("competencia_")) {
                assertThat(state(responseId, question.id())).isEqualTo(AnswerState.NO_APLICA);
                assertThat(answerCount(responseId, question.id())).isZero();
            }
        }
        assertThat(answerCount(responseId, "cantidad_empleos")).isZero();
        assertThat(state(responseId, "satisfaccion_formacion")).isEqualTo(AnswerState.CON_VALOR);
    }

    @Test
    void currentlyUnemployedGraduateWithPreviousUnrelatedWorkKeepsTheirJobHistory() throws Exception {
        OpenForm form = openForm("titulados", Map.of("experiencia_laboral", "OTRAS_AREAS",
                "estado_laboral", "DESEMPLEADO", "sector", "NO_APLICA"));
        form.values().set("cantidad_empleos", "DOS");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, "cantidad_empleos")).isEqualTo(AnswerState.CON_VALOR);
        assertThat(jdbc.queryForObject("""
                SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'cantidad_empleos'
                """, String.class, responseId)).isEqualTo("DOS");
        assertThat(state(responseId, "tiempo_primer_empleo")).isEqualTo(AnswerState.NO_APLICA);
        assertThat(state(responseId, "primer_empleo_actual")).isEqualTo(AnswerState.NO_APLICA);
    }

    @ParameterizedTest
    @ValueSource(strings = {"EMPLEADO", "EMPRENDIMIENTO"})
    void noWorkExperienceCannotBeCombinedWithCurrentEmploymentOrABusiness(String currentStatus) throws Exception {
        OpenForm form = openForm("titulados", Map.of("experiencia_laboral", "SIN_EXPERIENCIA",
                "estado_laboral", currentStatus));
        MvcResult rejected = mvc.perform(post("/encuestas/titulados").session(form.session())
                        .params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errors")).andReturn();
        assertThat((Map<?, ?>) rejected.getModelAndView().getModel().get("errors")).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isZero();
    }

    @Test
    void competenceItemsAreDistinctAndCannotBeRatedRemainsAValueRatherThanANumericScore() throws Exception {
        OpenForm form = openForm("titulados");
        List<Question> competencies = form.survey().questions().stream()
                .filter(question -> question.id().startsWith("competencia_")).toList();
        assertThat(competencies).hasSize(12);
        assertThat(competencies.stream().map(Question::id)).doesNotHaveDuplicates();
        assertThat(competencies.stream().filter(question -> question.id().startsWith("competencia_tecnica_")))
                .hasSize(6);
        assertThat(competencies.stream().filter(question -> question.id().startsWith("competencia_transversal_")))
                .hasSize(6);
        form.values().set(competencies.getFirst().id(), "NO_PUEDE_VALORAR");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, competencies.getFirst().id())).isEqualTo(AnswerState.CON_VALOR);
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = ?",
                String.class, responseId, competencies.getFirst().id())).isEqualTo("NO_PUEDE_VALORAR");
    }

    @Test
    void otherJobDescriptionCannotBeStoredUnlessOtroWasSelected() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().set("otro_cargo", "Este texto no corresponde al cargo elegido");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, "otro_cargo")).isEqualTo(AnswerState.NO_APLICA);
        assertThat(answerCount(responseId, "otro_cargo")).isZero();
    }

    @Test
    void selectedOtroKeepsTheOtherSelectedJobsAndTheDescription() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().put("cargos_desempenados", List.of("APOYO", "OTRO", "ANALISTA"));
        form.values().set("otro_cargo", "Arquitecto de soluciones");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, "otro_cargo")).isEqualTo(AnswerState.CON_VALOR);
        assertThat(jdbc.queryForList("""
                SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'cargos_desempenados'
                ORDER BY item_index
                """, String.class, responseId)).containsExactly("APOYO", "OTRO", "ANALISTA");
        assertThat(jdbc.queryForObject("""
                SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'otro_cargo'
                """, String.class, responseId)).isEqualTo("Arquitecto de soluciones");
    }

    @Test
    void applicableOptionalOtherJobDescriptionMayRemainUnanswered() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().put("cargos_desempenados", List.of("OTRO", "APOYO"));
        form.values().remove("otro_cargo");
        submit(form);
        String responseId = onlyResponseId();
        assertThat(state(responseId, "otro_cargo")).isEqualTo(AnswerState.SIN_RESPUESTA);
        assertThat(answerCount(responseId, "otro_cargo")).isZero();
    }

    @Test
    void retryReturnsTheSameReferenceAndDoesNotDuplicateValuesOrStates() throws Exception {
        OpenForm form = openForm("empleadores");
        submit(form);
        String responseId = onlyResponseId();
        MvcResult firstReceipt = mvc.perform(get("/gracias").session(form.session()))
                .andExpect(status().isOk()).andExpect(model().attribute("responseReference", responseId)).andReturn();
        assertThat(firstReceipt.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains(responseId);
        long values = jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer", Long.class);
        submit(form);
        mvc.perform(get("/gracias").session(form.session()))
                .andExpect(status().isOk()).andExpect(model().attribute("responseReference", responseId));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer", Long.class)).isEqualTo(values);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state", Long.class))
                .isEqualTo(form.survey().questions().size());
    }

    @Test
    void referenceIsVisibleOnlyToTheSubmittingSessionAndCannotBeChosenInTheUrl() throws Exception {
        OpenForm first = openForm("empleadores");
        first.values().set("email_contacto", "comprobante-privado@example.test");
        first.values().set("nombre_organizacion", "Organizacion privada del comprobante");
        submit(first);
        String firstId = onlyResponseId();
        OpenForm second = openForm("empleadores");
        submit(second);
        String secondId = jdbc.queryForObject("SELECT id FROM survey_response WHERE id <> ?", String.class, firstId);
        MvcResult own = mvc.perform(get("/gracias").session(first.session())
                        .param("id", secondId).param("responseId", secondId).param("responseReference", secondId))
                .andExpect(status().isOk()).andExpect(model().attribute("responseReference", firstId)).andReturn();
        String ownHtml = own.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(ownHtml).contains(firstId).doesNotContain(secondId, "comprobante-privado@example.test",
                "Organizacion privada del comprobante");
        MvcResult anonymous = mvc.perform(get("/gracias").param("id", firstId).param("responseReference", secondId))
                .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("responseReference")).andReturn();
        assertThat(anonymous.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(firstId, secondId, "comprobante-privado@example.test");
    }

    @Test
    void validationFailureDoesNotIssueAReceiptOrSaveQuestionStates() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().remove("nombre_organizacion");
        mvc.perform(post("/encuestas/empleadores").session(form.session()).params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errors"));
        mvc.perform(get("/gracias").session(form.session()))
                .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("responseReference"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state", Long.class)).isZero();
    }

    @Test
    void aLateDatabaseFailureRollsBackTheResponseItsAnswersAndItsStates() throws Exception {
        OpenForm form = openForm("empleadores");
        String lastQuestionId = form.survey().questions().getLast().id();
        assertThat(lastQuestionId).matches("[a-z0-9_]+");
        jdbc.execute("ALTER TABLE survey_question_state ADD CONSTRAINT test_reject_last_question "
                + "CHECK (question_id <> '" + lastQuestionId + "')");
        try {
            assertThatThrownBy(() -> mvc.perform(post("/encuestas/empleadores")
                    .session(form.session()).params(form.values()).with(csrf())))
                    .isInstanceOf(ServletException.class).hasRootCauseInstanceOf(SQLException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer", Long.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state", Long.class)).isZero();
            mvc.perform(get("/gracias").session(form.session()))
                    .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("responseReference"));
        } finally {
            jdbc.execute("ALTER TABLE survey_question_state DROP CONSTRAINT test_reject_last_question");
        }
        submit(form);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorsSeeMissingAndInapplicableStatesWhileTheRawCsvRemainsUnchanged() throws Exception {
        OpenForm form = openForm("titulados");
        form.values().set("formacion_complementaria", "NO");
        form.values().remove("meses_vida_profesional");
        form.values().remove("meses_desempleado");
        submit(form);
        String responseId = onlyResponseId();
        String html = mvc.perform(get("/admin/respuestas/" + responseId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Sin respuesta", "No aplica");
        String csv = mvc.perform(get("/admin/exportar.csv")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv.lines().findFirst().orElseThrow()).isEqualTo("\uFEFF\"response_id\",\"survey_slug\","
                + "\"instrument_version\",\"submitted_at\",\"question_id\",\"item_index\",\"answer_value\","
                + "\"question_label\",\"display_value\"");
        assertThat(csv).doesNotContain("\"meses_vida_profesional\"", "\"nivel_cursado\"", "\"SIN_RESPUESTA\"");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aLegacyResponseKeepsItsOriginalQuestionAndDoesNotReceiveInferredStates() throws Exception {
        String responseId = UUID.randomUUID().toString();
        insertLegacyResponse(jdbc, responseId);
        String html = mvc.perform(get("/admin/respuestas/" + responseId)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Pregunta de la primera versión", "Valor histórico original", "2026-10-borrador-1");
        assertThat(html).doesNotContain("<dt>¿En qué año egresó de Ingeniería en Sistemas?</dt>");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state WHERE response_id = ?",
                Long.class, responseId)).isZero();
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'pregunta_historica'",
                String.class, responseId)).isEqualTo("Valor histórico original");
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'anios_vida_profesional'",
                String.class, responseId)).isEqualTo("5");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer WHERE response_id = ? AND question_id = 'meses_vida_profesional'",
                Long.class, responseId)).isZero();
    }

    @Test
    void migratingAnExistingDatabaseDoesNotInventHistoricalStatesOrChangeRawValues() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:legacy-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL");
        config.setUsername("sa");
        config.setPassword("");
        config.setMaximumPoolSize(3);
        config.setMinimumIdle(1);
        // Mantiene vivas las conexiones de migración, igual que el datasource de la aplicación.
        try (HikariDataSource pool = new HikariDataSource(config)) {
            Flyway.configure().dataSource(pool).locations("classpath:db/migration").target("2").load().migrate();
            JdbcTemplate isolated = new JdbcTemplate(pool);
            String responseId = UUID.randomUUID().toString();
            insertLegacyResponse(isolated, responseId);
            Flyway.configure().dataSource(pool).locations("classpath:db/migration").load().migrate();
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM survey_question_state", Long.class)).isZero();
            assertThat(isolated.queryForObject("SELECT instrument_version FROM survey_response WHERE id = ?",
                    String.class, responseId)).isEqualTo("2026-10-borrador-1");
            assertThat(isolated.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'pregunta_historica'",
                    String.class, responseId)).isEqualTo("Valor histórico original");
            assertThat(isolated.queryForObject("SELECT question_label FROM survey_answer WHERE response_id = ? AND question_id = 'pregunta_historica'",
                    String.class, responseId)).isEqualTo("Pregunta de la primera versión");
            assertThat(isolated.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = ? AND question_id = 'anios_vida_profesional'",
                    String.class, responseId)).isEqualTo("5");
            assertThat(isolated.queryForObject("SELECT COUNT(*) FROM survey_answer WHERE response_id = ? AND question_id = 'meses_vida_profesional'",
                    Long.class, responseId)).isZero();
        }
    }

    @Test
    void repositoryRejectsAClaimedValidSubmissionContainingAValueForAnInactiveQuestion() throws Exception {
        OpenForm form = openForm("titulados", Map.of("formacion_complementaria", "NO"));
        ValidationResult validated = validator.validate(form.survey(), form.values());
        assertThat(validated.valid()).isTrue();
        assertThat(validated.states().get("nivel_cursado")).isEqualTo(AnswerState.NO_APLICA);
        Map<String, List<String>> tamperedAnswers = new LinkedHashMap<>(validated.answers());
        tamperedAnswers.put("nivel_cursado", List.of("DIPLOMADO"));
        ValidationResult contradictory = new ValidationResult(tamperedAnswers, Map.of(), validated.states());
        assertThatThrownBy(() -> repository.save(form.survey(), UUID.randomUUID().toString(), contradictory))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_question_state", Long.class)).isZero();
    }

    private static void insertLegacyResponse(JdbcTemplate target, String responseId) {
        target.update("""
                INSERT INTO survey_response (id, submission_token, survey_slug, instrument_version, submitted_at, consent)
                VALUES (?, ?, 'titulados', '2026-10-borrador-1', ?, TRUE)
                """, responseId, UUID.randomUUID().toString(), OffsetDateTime.now(ZoneOffset.UTC));
        target.update("""
                INSERT INTO survey_answer (response_id, question_id, question_index, question_label, item_index, answer_value, display_value)
                VALUES (?, 'pregunta_historica', 0, 'Pregunta de la primera versión', 0, 'Valor histórico original', 'Valor histórico original')
                """, responseId);
        target.update("""
                INSERT INTO survey_answer (response_id, question_id, question_index, question_label, item_index, answer_value, display_value)
                VALUES (?, 'anios_vida_profesional', 1, 'Años de vida profesional de la primera versión', 0, '5', '5')
                """, responseId);
    }

    private OpenForm openForm(String slug) throws Exception {
        return openForm(slug, Map.of());
    }

    private OpenForm openForm(String slug, Map<String, String> overrides) throws Exception {
        MvcResult result = mvc.perform(get("/encuestas/" + slug)).andExpect(status().isOk()).andReturn();
        SurveyDefinition survey = catalog.get(slug);
        MultiValueMap<String, String> values = validAnswers(survey, overrides);
        values.set("formToken", (String) result.getModelAndView().getModel().get("formToken"));
        values.set("consent", "SI");
        return new OpenForm((MockHttpSession) result.getRequest().getSession(false), survey, values);
    }

    private MultiValueMap<String, String> validAnswers(SurveyDefinition survey, Map<String, String> overrides) {
        MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
        for (Question question : survey.questions()) {
            if (question.dependsOn() != null
                    && Arrays.stream(question.dependsValue().split("\\|"))
                    .noneMatch(value -> values.getOrDefault(question.dependsOn(), List.of()).contains(value))) {
                continue;
            }
            if (overrides.containsKey(question.id())) {
                values.set(question.id(), overrides.get(question.id()));
            } else if (!question.options().isEmpty()) {
                int selections = question.type().equals("checkbox") ? Math.max(1, question.minSelections()) : 1;
                question.options().stream().limit(selections).forEach(option -> values.add(question.id(), option.value()));
            } else {
                String value = switch (question.type()) {
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
                };
                values.set(question.id(), value);
            }
        }
        return values;
    }

    private void submit(OpenForm form) throws Exception {
        mvc.perform(post("/encuestas/" + form.survey().slug()).session(form.session()).params(form.values()).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/gracias"));
    }

    private String onlyResponseId() {
        return jdbc.queryForObject("SELECT id FROM survey_response", String.class);
    }

    private AnswerState state(String responseId, String questionId) {
        return AnswerState.valueOf(jdbc.queryForObject("""
                SELECT answer_state FROM survey_question_state WHERE response_id = ? AND question_id = ?
                """, String.class, responseId, questionId));
    }

    private long answerCount(String responseId, String questionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer WHERE response_id = ? AND question_id = ?",
                Long.class, responseId, questionId);
    }

    private record OpenForm(MockHttpSession session, SurveyDefinition survey, MultiValueMap<String, String> values) {}
}

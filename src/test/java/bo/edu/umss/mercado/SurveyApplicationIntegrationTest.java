package bo.edu.umss.mercado;

import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.security.AdministratorService;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifica los límites públicos/privados y la recolección a través del backend real. */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:survey-integration;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SurveyApplicationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SurveyCatalog catalog;
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
    @ValueSource(strings = {"/", "/encuestas/titulados", "/encuestas/empleadores", "/login", "/gracias"})
    void publicPagesRenderWithoutAuthentication(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/html"));
    }

    @Test
    void employersRenderEachOfTheTenCriteriaOnceWithNoSabeAsAnOption() throws Exception {
        var criteria = catalog.get("empleadores").questions().stream()
                .filter(question -> question.id().startsWith("criterio_")).toList();
        assertThat(criteria).hasSize(10);
        assertThat(criteria.stream().map(Question::id)).doesNotHaveDuplicates();
        assertThat(criteria.stream().map(Question::label)).doesNotHaveDuplicates();
        String html = mvc.perform(get("/encuestas/empleadores"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        for (Question criterion : criteria) {
            assertThat(countOccurrences(html, "data-question-id=\"" + criterion.id() + "\""))
                    .as("un solo grupo para %s", criterion.id()).isEqualTo(1);
            assertThat(criterion.options().stream().map(Question.Option::value)).contains("NO_SABE");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/respuestas/123", "/admin/exportar.csv"})
    void privateRoutesRequireLogin(String path) throws Exception {
        mvc.perform(get(path))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin", "/admin/respuestas/123", "/admin/exportar.csv"})
    @WithMockUser(username = "sin-permiso", roles = "USER")
    void authenticatedUsersWithoutAdminRoleCannotReadResponses(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    void persistedCredentialsAllowAdminAccessAndWrongPasswordDoesNot() throws Exception {
        MvcResult login = mvc.perform(formLogin("/login").user("test-admin").password("test-secret-no-real"))
                .andExpect(authenticated().withUsername("test-admin"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/admin").session(session)).andExpect(status().isOk());
        mvc.perform(formLogin("/login").user("test-admin").password("incorrecta"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void submissionWithoutCsrfCannotWriteData() throws Exception {
        OpenForm form = openForm("empleadores");
        mvc.perform(post("/encuestas/empleadores").session(form.session()).params(form.values()))
                .andExpect(status().isForbidden());
        assertThat(responseCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"titulados", "empleadores"})
    void validSubmissionPersistsOriginalAnswersWithInstrumentVersion(String slug) throws Exception {
        OpenForm form = openForm(slug);
        submit(form);
        assertThat(responseCount()).isEqualTo(1);
        String id = onlyResponseId();
        assertThat(jdbc.queryForObject("SELECT survey_slug FROM survey_response WHERE id = ?", String.class, id))
                .isEqualTo(slug);
        assertThat(jdbc.queryForObject("SELECT instrument_version FROM survey_response WHERE id = ?", String.class, id))
                .isEqualTo(form.survey().version());
        assertThat(jdbc.queryForObject("SELECT consent FROM survey_response WHERE id = ?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM survey_response WHERE id = ?", Object.class, id)).isNotNull();
        for (Question question : form.survey().questions()) {
            List<String> expected = form.values().get(question.id());
            if (expected != null) {
                assertThat(jdbc.queryForList("SELECT answer_value FROM survey_answer WHERE response_id = ? "
                                + "AND question_id = ? ORDER BY item_index", String.class, id, question.id()))
                        .as("respuestas originales de %s", question.id()).containsExactlyElementsOf(expected);
            }
        }
    }

    @Test
    void repeatOfSameFormDoesNotCreateAnotherResponse() throws Exception {
        OpenForm form = openForm("empleadores");
        submit(form);
        submit(form);
        assertThat(responseCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer WHERE question_id LIKE 'criterio_%'", Long.class))
                .isEqualTo(10);
    }

    @Test
    void validNoSabeIsStoredAsItsOwnCategory() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().set("criterio_01", "NO_SABE");
        submit(form);
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE question_id = 'criterio_01'", String.class))
                .isEqualTo("NO_SABE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-required", "invalid-option", "duplicate-single", "invalid-integer", "out-of-range-year",
            "invalid-email", "invalid-url", "invalid-phone", "missing-active-child", "too-few-selections", "no-consent",
            "invalid-consent", "oversized-text", "inconsistent-employed-sector", "inconsistent-unemployed-sector"})
    void invalidSubmissionShowsErrorsAndWritesNothing(String invalidCase) throws Exception {
        OpenForm form = openForm("titulados");
        switch (invalidCase) {
            case "missing-required" -> form.values().remove("anio_egreso");
            case "invalid-option" -> form.values().set("estado_laboral", "VALOR_INEXISTENTE");
            case "duplicate-single" -> form.values().add("anio_egreso", "2021");
            case "invalid-integer" -> form.values().set("anio_egreso", "2020.5");
            case "out-of-range-year" -> form.values().set("anio_egreso", "1959");
            case "invalid-email" -> form.values().set("email_contacto", "sin-arroba");
            case "invalid-url" -> form.values().set("linkedin", "javascript:alert(1)");
            case "invalid-phone" -> form.values().set("telefono_contacto", "-----");
            case "missing-active-child" -> form.values().remove("nivel_cursado");
            case "too-few-selections" -> form.values().put("asignaturas_ventaja", List.of("INTRODUCCION", "BASE_DATOS"));
            case "no-consent" -> form.values().remove("consent");
            case "invalid-consent" -> form.values().set("consent", "NO");
            case "oversized-text" -> form.values().set("programa_cursado", "x".repeat(2001));
            case "inconsistent-employed-sector" -> form.values().set("sector", "NO_APLICA");
            case "inconsistent-unemployed-sector" -> {
                form.values().set("estado_laboral", "DESEMPLEADO");
                form.values().remove("primer_empleo_actual");
            }
            default -> throw new IllegalArgumentException(invalidCase);
        }
        MvcResult result = mvc.perform(post("/encuestas/titulados").session(form.session())
                        .params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errors")).andReturn();
        assertThat((Map<?, ?>) result.getModelAndView().getModel().get("errors")).isNotEmpty();
        assertThat(responseCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer", Long.class)).isZero();
    }

    @Test
    void unemployedGraduateCanSubmitWithNoCurrentSectorOrFirstCurrentJob() throws Exception {
        OpenForm form = openForm("titulados");
        form.values().set("estado_laboral", "DESEMPLEADO");
        form.values().set("sector", "NO_APLICA");
        form.values().remove("primer_empleo_actual");
        submit(form);
        assertThat(responseCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE question_id = 'estado_laboral'", String.class))
                .isEqualTo("DESEMPLEADO");
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE question_id = 'sector'", String.class))
                .isEqualTo("NO_APLICA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer WHERE question_id = 'primer_empleo_actual'", Long.class))
                .isZero();
    }

    @Test
    void validationErrorRetainsValuesAndAllowsCorrectedSubmission() throws Exception {
        OpenForm form = openForm("titulados");
        form.values().set("anio_egreso", "año-no-válido");
        MvcResult rejected = mvc.perform(post("/encuestas/titulados").session(form.session())
                        .params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        Map<?, ?> retained = (Map<?, ?>) rejected.getModelAndView().getModel().get("values");
        assertThat(retained.get("email_contacto")).isEqualTo(List.of("test@example.test"));
        assertThat(responseCount()).isZero();
        form.values().set("anio_egreso", "2020");
        submit(form);
        assertThat(responseCount()).isEqualTo(1);
    }

    @Test
    void inactiveConditionalFieldsAreNotStored() throws Exception {
        OpenForm form = openForm("titulados");
        form.values().set("formacion_complementaria", "NO");
        for (Question question : form.survey().questions()) {
            if ("formacion_complementaria".equals(question.dependsOn())) {
                form.values().set(question.id(), "VALOR_MANIPULADO");
            }
        }
        submit(form);
        String id = onlyResponseId();
        for (Question question : form.survey().questions()) {
            if ("formacion_complementaria".equals(question.dependsOn())) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_answer WHERE response_id = ? AND question_id = ?",
                        Long.class, id, question.id())).as("campo inactivo %s", question.id()).isZero();
            }
        }
    }

    @Test
    void requestWithUnknownFieldIsRejectedWithoutPersistingData() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().set("role", "ADMIN");
        MvcResult result = mvc.perform(post("/encuestas/empleadores").session(form.session())
                        .params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errors")).andReturn();
        assertThat(((Map<?, ?>) result.getModelAndView().getModel().get("errors")).containsKey("_form")).isTrue();
        assertThat(responseCount()).isZero();
    }

    @Test
    void oneSurveySessionCannotSubmitAnotherSessionsToken() throws Exception {
        OpenForm first = openForm("empleadores");
        OpenForm second = openForm("empleadores");
        second.values().set("formToken", first.values().getFirst("formToken"));
        MvcResult result = mvc.perform(post("/encuestas/empleadores").session(second.session())
                .params(second.values()).with(csrf())).andReturn();
        assertRejectedForm(result);
        assertThat(responseCount()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "forged"})
    void missingOrForgedSubmissionTokenCannotWriteData(String tokenCase) throws Exception {
        OpenForm form = openForm("empleadores");
        if (tokenCase.equals("absent")) {
            form.values().remove("formToken");
        } else {
            form.values().set("formToken", UUID.randomUUID().toString());
        }
        MvcResult result = mvc.perform(post("/encuestas/empleadores").session(form.session())
                        .params(form.values()).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("errors")).andReturn();
        assertThat(((Map<?, ?>) result.getModelAndView().getModel().get("errors")).containsKey("_form")).isTrue();
        assertThat(responseCount()).isZero();
    }

    @Test
    void unknownSurveyDoesNotExposeOrCreateData() throws Exception {
        mvc.perform(get("/encuestas/desconocida")).andExpect(status().isNotFound());
        mvc.perform(post("/encuestas/desconocida").with(csrf())
                .param("formToken", UUID.randomUUID().toString()).param("consent", "SI"))
                .andExpect(status().isNotFound());
        assertThat(responseCount()).isZero();
    }

    @Test
    void responsesAreEscapedInAdminAndNeverExposedOnPublicPages() throws Exception {
        String text = "<script>alert('dato-privado')</script>";
        OpenForm form = openForm("empleadores");
        form.values().set("areas_emergentes", text);
        form.values().set("email_contacto", "dato-privado@example.test");
        submit(form);
        String id = onlyResponseId();
        for (String path : List.of("/", "/encuestas/empleadores", "/encuestas/titulados", "/gracias", "/login")) {
            String html = mvc.perform(get(path)).andExpect(status().isOk()).andReturn()
                    .getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).doesNotContain("dato-privado@example.test", text);
        }
        for (String path : List.of("/admin", "/admin/respuestas/" + id, "/admin/exportar.csv")) {
            String body = mvc.perform(get(path)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login")).andReturn()
                    .getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(body).doesNotContain("dato-privado@example.test", text);
        }
        MvcResult login = mvc.perform(formLogin("/login").user("test-admin").password("test-secret-no-real"))
                .andExpect(authenticated()).andReturn();
        String html = mvc.perform(get("/admin/respuestas/" + id)
                        .session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("dato-privado@example.test", "&lt;script&gt;").doesNotContain(text);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void csvPreservesUnicodeAndQuotedMultilineValuesAndNeutralizesFormulas() throws Exception {
        OpenForm form = openForm("empleadores");
        form.values().set("nombre_organizacion", "Árbol, \"Ñandú\"\nsegunda línea");
        form.values().set("herramientas_tecnologicas", "=2+2");
        form.values().set("areas_emergentes", "-2+2");
        form.values().add("cargos_desempenados", "OTRO");
        form.values().set("otro_cargo", "+2+2");
        form.values().set("competencias_requeridas", "@SUM(1)");
        submit(form);
        MvcResult exported = mvc.perform(get("/admin/exportar.csv"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andReturn();
        String csv = exported.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains("\"Árbol, \"\"Ñandú\"\"\nsegunda línea\"", "\"'=2+2\"", "\"'+2+2\"",
                "\"'-2+2\"", "\"'@SUM(1)\"", catalog.get("empleadores").version());
        assertThat(exported.getResponse().getHeader("Content-Disposition")).contains("attachment");
        assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE question_id = 'herramientas_tecnologicas'",
                String.class)).isEqualTo("=2+2");
    }

    private OpenForm openForm(String slug) throws Exception {
        MvcResult result = mvc.perform(get("/encuestas/" + slug)).andExpect(status().isOk())
                .andExpect(model().attributeExists("formToken")).andReturn();
        var session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        String token = (String) result.getModelAndView().getModel().get("formToken");
        assertThat(UUID.fromString(token)).isNotNull();
        SurveyDefinition survey = catalog.get(slug);
        MultiValueMap<String, String> values = validAnswers(survey);
        values.set("formToken", token);
        values.set("consent", "SI");
        return new OpenForm(session, survey, values);
    }

    private MultiValueMap<String, String> validAnswers(SurveyDefinition survey) {
        MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
        for (Question question : survey.questions()) {
            if (question.dependsOn() != null && Arrays.stream(question.dependsValue().split("\\|"))
                    .noneMatch(value -> values.getOrDefault(question.dependsOn(), List.of()).contains(value))) {
                continue;
            }
            if (!question.options().isEmpty()) {
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

    private long responseCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class);
    }

    private String onlyResponseId() {
        return jdbc.queryForObject("SELECT id FROM survey_response", String.class);
    }

    private static void assertRejectedForm(MvcResult result) {
        int status = result.getResponse().getStatus();
        if (status >= 400 && status < 500) {
            return;
        }
        assertThat(status).isEqualTo(200);
        assertThat(result.getModelAndView()).isNotNull();
        assertThat((Map<?, ?>) result.getModelAndView().getModel().get("errors")).isNotEmpty();
    }

    private static int countOccurrences(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private record OpenForm(MockHttpSession session, SurveyDefinition survey, MultiValueMap<String, String> values) {}
}

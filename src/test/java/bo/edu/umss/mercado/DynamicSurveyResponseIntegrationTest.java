package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.wrangling.WranglingService;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:dynamic-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username = "dynamic-admin", roles = "ADMIN")
class DynamicSurveyResponseIntegrationTest {
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired SurveyResponseRepository responses;
    @Autowired CaptureStore captures;
    @Autowired SurveyValidator validator;
    @Autowired WranglingService wrangling;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String id;
    @BeforeEach void prepare() {
        clear(); id = drafts.create("Formulario V1", "Datos de prueba", "dynamic-admin");
        drafts.section(id, 0, null, "Preferencias", "dynamic-admin"); String section = drafts.get(id).sections().getFirst().id();
        drafts.question(id, 1, section, new Question("control", "Preferencias", "Control original", "radio", true,
                List.of(new Question.Option("SI", "Sí"), new Question.Option("NO", "No")), null, null, 0, 1, "", null, null), true, "dynamic-admin");
        drafts.question(id, 2, section, new Question("detalle", "Preferencias", "Detalle", "text", true, List.of(), "control", "SI", 0, 1, "", null, null), true, "dynamic-admin");
        drafts.question(id, 3, section, new Question("opciones", "Preferencias", "Opciones", "checkbox", false,
                List.of(new Question.Option("A", "Uno"), new Question.Option("B", "Dos"), new Question.Option("C", "Tres")), null, null, 1, 2, "", null, null), true, "dynamic-admin");
        drafts.question(id, 4, section, new Question("edad", "Preferencias", "Edad", "number", false, List.of(), null, null, 0, 1, "", 0, 120), true, "dynamic-admin");
        publications.publish(id, 5, "dynamic-admin");
    }
    @AfterEach void clear() {
        jdbc.update("DELETE FROM survey_answer WHERE response_id IN (SELECT id FROM survey_response WHERE publication_version_id IS NOT NULL)");
        jdbc.update("DELETE FROM survey_question_state WHERE response_id IN (SELECT id FROM survey_response WHERE publication_version_id IS NOT NULL)");
        jdbc.update("DELETE FROM survey_response WHERE publication_version_id IS NOT NULL");
        jdbc.update("UPDATE survey SET draft_version_id = NULL, active_version_id = NULL");
        for (String table : List.of("survey_definition_event", "survey_question_option", "survey_question", "survey_section", "survey_version", "survey")) jdbc.update("DELETE FROM " + table);
        jdbc.update("DELETE FROM instrument_snapshot WHERE survey_slug LIKE 'encuesta-%'");
    }
    record Opened(SurveyPublicationService.Version version, MockHttpSession session, String token) {}
    Opened open(String versionId, MockHttpSession session) throws Exception {
        var version = publications.get(versionId);
        var request = get(version.publicPath()).with(anonymous()); if (session != null) request.session(session);
        var result = mvc.perform(request).andExpect(status().isOk()).andExpect(view().name("survey")).andReturn();
        return new Opened(version, (MockHttpSession) result.getRequest().getSession(), (String) result.getModelAndView().getModel().get("formToken"));
    }
    LinkedMultiValueMap<String, String> values(Opened opened) {
        var values = new LinkedMultiValueMap<String, String>(); values.set("formToken", opened.token()); values.set("consent", "SI"); values.set("control", "NO"); return values;
    }
    String submit(Opened opened, LinkedMultiValueMap<String, String> fields) throws Exception {
        mvc.perform(post(opened.version().publicPath()).with(anonymous()).with(csrf()).session(opened.session()).params(fields))
                .andExpect(redirectedUrl("/gracias"));
        return (String) opened.session().getAttribute("lastSubmissionReference");
    }
    String next() {
        String next = publications.successor(id, "dynamic-admin"); drafts.updateHeader(next, 0, "Formulario V2", "Nueva descripción", "dynamic-admin");
        publications.publish(next, 1, "dynamic-admin"); return next;
    }
    long count() { return jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE publication_version_id IS NOT NULL", Long.class); }

    @Test void activationIsExplicitAndPrincipalPinsTheVersionInTheRedirect() throws Exception {
        var v1 = publications.get(id);
        mvc.perform(get(v1.principalPath()).with(anonymous())).andExpect(status().isNotFound());
        publications.activate(id, v1.revision(), "dynamic-admin");
        mvc.perform(get(v1.principalPath()).with(anonymous())).andExpect(redirectedUrl(v1.publicPath()));
        mvc.perform(get("/").with(anonymous())).andExpect(content().string(org.hamcrest.Matchers.containsString(v1.principalPath())));
        String next = next();
        assertThat(publications.activeVersion(v1.slug()).id()).isEqualTo(id);
        var v2 = publications.get(next); publications.activate(next, v2.revision(), "dynamic-admin");
        assertThat(publications.activeVersion(v1.slug()).id()).isEqualTo(next);
        assertThat(publications.get(id).status()).isEqualTo("PUBLICADA");
    }

    @Test void activationRequiresAdminCsrfAndCurrentRevision() throws Exception {
        String route = "/admin/encuestas/versiones/" + id + "/activar";
        mvc.perform(post(route).with(anonymous()).with(csrf()).param("revision", "6")).andExpect(status().is3xxRedirection());
        assertThat(publications.get(id).active()).isFalse();
        mvc.perform(post(route).param("revision", "6")).andExpect(status().isForbidden());
        mvc.perform(post(route).with(csrf()).param("revision", "0")).andExpect(status().isConflict());
        mvc.perform(post(route).with(csrf()).param("revision", "6")).andExpect(status().is3xxRedirection());
        assertThat(publications.get(id).active()).isTrue();
    }

    @Test void directVersionLinkWorksBeforeActivation() throws Exception {
        var opened = open(id, null);
        submit(opened, values(opened)); assertThat(count()).isEqualTo(1);
        assertThat(publications.get(id).active()).isFalse();
    }

    @Test void newSubmissionFreezesPublicationAndStatesWithoutTrustingPublicOrigin() throws Exception {
        var opened = open(id, null); var fields = values(opened); fields.set("detalle", "No debe guardarse");
        String responseId = submit(opened, fields);
        var header = jdbc.queryForMap("SELECT * FROM survey_response WHERE id = ?", responseId);
        assertThat(header.get("publication_version_id")).isEqualTo(id);
        assertThat(header.get("publication_hash")).isEqualTo(opened.version().hash());
        var capture = captures.input(responseId);
        assertThat(capture.origin()).isEqualTo("NO_DECLARADO"); assertThat(capture.version()).isEqualTo("v1");
        assertThat(capture.states()).containsEntry("control", "CON_VALOR").containsEntry("detalle", "NO_APLICA").containsEntry("edad", "SIN_RESPUESTA");
        assertThat(capture.answers()).doesNotContainKey("detalle");
        assertThat(capture.hasSnapshot()).isTrue();
    }

    @Test void publicOriginAndForeignQuestionFieldsAreRejected() throws Exception {
        var opened = open(id, null); var fields = values(opened); fields.set("origin", "REAL");
        mvc.perform(post(opened.version().publicPath()).with(anonymous()).with(csrf()).session(opened.session()).params(fields)).andExpect(status().isUnprocessableEntity());
        fields.remove("origin"); fields.set("otra_pregunta", "x");
        mvc.perform(post(opened.version().publicPath()).with(anonymous()).with(csrf()).session(opened.session()).params(fields)).andExpect(status().isUnprocessableEntity());
        assertThat(count()).isZero();
    }

    @Test void validationRequiresConsentConditionsRangesAndSelectionLimits() throws Exception {
        var opened = open(id, null);
        for (String problem : List.of("consent", "condition", "range", "multiple")) {
            var fields = values(opened);
            switch (problem) {
                case "consent" -> fields.remove("consent");
                case "condition" -> fields.set("control", "SI");
                case "range" -> fields.set("edad", "121");
                case "multiple" -> fields.put("opciones", List.of("A", "B", "C"));
            }
            mvc.perform(post(opened.version().publicPath()).with(anonymous()).with(csrf()).session(opened.session()).params(fields)).andExpect(status().isUnprocessableEntity());
        }
        assertThat(count()).isZero();
    }

    @Test void publicPostWithoutCsrfCannotSave() throws Exception {
        var opened = open(id, null);
        mvc.perform(post(opened.version().publicPath()).with(anonymous()).session(opened.session()).params(values(opened))).andExpect(status().isForbidden());
        assertThat(count()).isZero();
    }

    @Test void tokenCannotBeUsedForAnotherVersionOrAnotherSession() throws Exception {
        var original = open(id, null); String next = next(); var other = open(next, original.session());
        mvc.perform(post(other.version().publicPath()).with(anonymous()).with(csrf()).session(original.session()).params(values(original))).andExpect(status().isUnprocessableEntity());
        mvc.perform(post(original.version().publicPath()).with(anonymous()).with(csrf()).session(new MockHttpSession()).params(values(original))).andExpect(status().isUnprocessableEntity());
        assertThat(count()).isZero();
    }

    @Test void openedV1StillSavesV1AfterThePrincipalSwitchesToV2() throws Exception {
        var v1 = publications.get(id); publications.activate(id, v1.revision(), "dynamic-admin");
        var opened = open(id, null); String next = next(); publications.activate(next, publications.get(next).revision(), "dynamic-admin");
        String responseId = submit(opened, values(opened));
        assertThat(captures.input(responseId).version()).isEqualTo("v1");
        assertThat(responses.detail(responseId).summary().surveyTitle()).isEqualTo("Formulario V1");
        assertThat(publications.get(id).hash()).isEqualTo(v1.hash());
    }

    @Test void aVersionClosedAfterOpeningRejectsTheNewSubmission() throws Exception {
        var opened = open(id, null); publications.close(id, publications.get(id).revision(), "dynamic-admin");
        mvc.perform(post(opened.version().publicPath()).with(anonymous()).with(csrf()).session(opened.session()).params(values(opened))).andExpect(status().isGone());
        assertThat(count()).isZero();
    }

    @Test void acceptedRetryAfterCloseReturnsSameReferenceAndDoesNotOverwrite() throws Exception {
        var opened = open(id, null); String reference = submit(opened, values(opened));
        String hash = captures.input(reference).captureHash();
        publications.close(id, publications.get(id).revision(), "dynamic-admin");
        assertThat(submit(opened, values(opened))).isEqualTo(reference); assertThat(count()).isEqualTo(1);
        assertThat(captures.input(reference).captureHash()).isEqualTo(hash);
        var different = values(opened); different.set("control", "SI"); different.set("detalle", "Otra respuesta");
        mvc.perform(post(opened.version().publicPath()).with(anonymous()).with(csrf()).session(opened.session()).params(different)).andExpect(status().isConflict());
        assertThat(captures.input(reference).captureHash()).isEqualTo(hash);
    }

    @Test void retryRemainsOwnedAfterOpeningANewFormOfTheSameVersion() throws Exception {
        var opened = open(id, null); String reference = submit(opened, values(opened));
        var another = open(id, opened.session()); assertThat(another.token()).isNotEqualTo(opened.token());
        assertThat(submit(opened, values(opened))).isEqualTo(reference);
        submit(another, values(another)); assertThat(count()).isEqualTo(2);
    }

    @Test void multipleChoiceOrderDoesNotDuplicateAnAcceptedRetry() throws Exception {
        var opened = open(id, null); var fields = values(opened); fields.put("opciones", List.of("A", "B"));
        String reference = submit(opened, fields); fields.put("opciones", List.of("B", "A"));
        assertThat(submit(opened, fields)).isEqualTo(reference); assertThat(count()).isEqualTo(1);
    }

    @Test void closingActiveVersionClearsPrincipalWithoutChangingDefinition() throws Exception {
        var v1 = publications.get(id); publications.activate(id, v1.revision(), "dynamic-admin");
        publications.close(id, publications.get(id).revision(), "dynamic-admin");
        var closed = publications.get(id); assertThat(closed.active()).isFalse(); assertThat(closed.hash()).isEqualTo(v1.hash());
        assertThatThrownBy(() -> publications.activate(id, closed.revision(), "dynamic-admin")).isInstanceOf(ResponseStatusException.class);
        mvc.perform(get(v1.principalPath()).with(anonymous())).andExpect(status().isNotFound());
    }

    @Test void closingInactiveV1DoesNotClearActiveV2() {
        String next = next(); publications.activate(next, publications.get(next).revision(), "dynamic-admin");
        publications.close(id, publications.get(id).revision(), "dynamic-admin");
        assertThat(publications.activeVersion(publications.get(next).slug()).id()).isEqualTo(next);
    }

    @Test void adminFiltersSeparateVersionsAndUseTheirFrozenTitles() throws Exception {
        var v1 = open(id, null); String first = submit(v1, values(v1)); String next = next();
        var v2 = open(next, null); submit(v2, values(v2));
        mvc.perform(get("/admin/respuestas").param("survey", v1.version().slug()).param("version", "v1"))
                .andExpect(status().isOk()).andExpect(model().attribute("total", 1L));
        assertThat(responses.selected(v1.version().slug(), "v1")).extracting(r -> r.surveyTitle()).containsExactly("Formulario V1");
        assertThat(responses.selected(v1.version().slug(), "v2")).extracting(r -> r.surveyTitle()).containsExactly("Formulario V2");
        mvc.perform(get("/admin/respuestas/" + first)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Origen sin verificar")));
        mvc.perform(get("/admin")).andExpect(status().isOk());
    }

    @Test void responsesAndFiltersRemainPrivateAndInvalidSelectionsAreRejected() throws Exception {
        var opened = open(id, null); String reference = submit(opened, values(opened));
        mvc.perform(get("/admin/respuestas").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/respuestas/" + reference).with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/respuestas").param("survey", "foreign")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/respuestas").param("survey", opened.version().slug()).param("version", "v999")).andExpect(status().isBadRequest());
    }

    @Test void originClassificationIsTraceableButDoesNotApproveTheInstrument() throws Exception {
        var opened = open(id, null); String reference = submit(opened, values(opened));
        mvc.perform(post("/admin/respuestas/" + reference + "/origen").with(csrf()).param("origin", "SINTETICO").param("reason", "Prueba local"))
                .andExpect(status().is3xxRedirection());
        assertThat(wrangling.origin(reference)).isEqualTo("SINTETICO");
        assertThat(captures.instrumentStatus(captures.input(reference))).isEqualTo("BORRADOR");
        mvc.perform(get("/admin/respuestas/" + reference + "/revision")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Registrar corrección"))));
    }

    @Test void dynamicSubmissionsAreExplicitlyExcludedFromTheLegacyAnalyticProducts() throws Exception {
        var opened = open(id, null); String reference = submit(opened, values(opened));
        var run = wrangling.detail(wrangling.create("NO_DECLARADO", "BORRADOR", null, "dynamic-admin"));
        var member = run.members().stream().filter(m -> reference.equals(m.get("response_id"))).findFirst().orElseThrow();
        assertThat(member).containsEntry("included", false).containsEntry("exclusion_reason", "ANALISIS_DINAMICO_PENDIENTE");
        assertThat(run.employers()).noneMatch(r -> reference.equals(r.get("response_id")));
        assertThat(run.graduates()).noneMatch(r -> reference.equals(r.get("response_id")));
        assertThat(run.manifest().get("rules_version")).isEqualTo("2026-10-wrangling-4");
        assertThatThrownBy(() -> wrangling.correct(reference, "control", List.of("SI"), "Prueba", "dynamic-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(count()).isEqualTo(1);
    }

    @Test void databasePreventsUnboundOrCrossVersionDynamicResponses() throws Exception {
        var opened = open(id, null); String reference = submit(opened, values(opened));
        assertThatThrownBy(() -> jdbc.update("UPDATE survey_response SET publication_hash = ? WHERE id = ?", "0".repeat(64), reference))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        var validation = validator.validate(opened.version().content().definition(), values(opened));
        assertThatThrownBy(() -> responses.save(opened.version().content().definition(), java.util.UUID.randomUUID().toString(), validation))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(count()).isEqualTo(1);
    }

    @Test void concurrentRetriesStoreOnlyOneCompleteSubmission() throws Exception {
        var opened = open(id, null); var validation = validator.validate(opened.version().content().definition(), values(opened));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = List.of(1, 2).stream().map(n -> executor.submit(() -> { start.await(); return responses.savePublished(id, opened.token(), validation); })).toList();
            start.countDown(); assertThat(futures.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(futures.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM response_capture c JOIN survey_response r ON r.id = c.response_id WHERE r.publication_version_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test void closingAndSubmittingConcurrentlyCannotCreateAPartialCapture() throws Exception {
        var opened = open(id, null); var validation = validator.validate(opened.version().content().definition(), values(opened));
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var submit = executor.submit(() -> { start.await(); try { return responses.savePublished(id, opened.token(), validation); } catch (ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(410); return null; } });
            var close = executor.submit(() -> { start.await(); publications.close(id, publications.get(id).revision(), "dynamic-admin"); return true; });
            start.countDown(); String result = submit.get(10, java.util.concurrent.TimeUnit.SECONDS); close.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(count()).isEqualTo(result == null ? 0 : 1);
            if (result != null) assertThat(captures.input(result).hasSnapshot()).isTrue();
        }
        assertThat(publications.get(id).status()).isEqualTo("CERRADA");
    }

    @Test void acceptedTextIsEscapedInTheProtectedDetailAndExportIsStillAdminOnly() throws Exception {
        var opened = open(id, null); var fields = values(opened); fields.set("control", "SI"); fields.set("detalle", "<script>respuesta</script>");
        String reference = submit(opened, fields);
        mvc.perform(get("/admin/respuestas/" + reference)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>respuesta</script>"))));
        mvc.perform(get("/admin/exportar.csv").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/exportar.csv")).andExpect(status().isOk());
    }

    @Test void browserFixturesIncludeActiveManagerPublicFormAndProtectedSelection() throws Exception {
        var directory = Path.of(".codex-tmp", "dynamic-browser"); Files.createDirectories(directory);
        publications.activate(id, publications.get(id).revision(), "dynamic-admin"); var opened = open(id, null);
        Files.writeString(directory.resolve("form.html"), mvc.perform(get(opened.version().publicPath()).with(anonymous()).session(opened.session())).andReturn().getResponse().getContentAsString());
        String reference = submit(opened, values(opened));
        Files.writeString(directory.resolve("version.html"), mvc.perform(get("/admin/encuestas/versiones/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(directory.resolve("responses.html"), mvc.perform(get("/admin/respuestas").param("survey", opened.version().slug()).param("version", "v1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(directory.resolve("detail.html"), mvc.perform(get("/admin/respuestas/" + reference)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(directory.resolve("review.html"), mvc.perform(get("/admin/respuestas/" + reference + "/revision")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}

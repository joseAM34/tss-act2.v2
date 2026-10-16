package bo.edu.umss.mercado;

import bo.edu.umss.mercado.survey.DraftSurveyService;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:publication-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}"
})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username = "publication-admin", roles = "ADMIN")
class SurveyPublicationIntegrationTest {
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String id, section;
    @BeforeEach void prepare() {
        clear();
        id = drafts.create("Encuesta de prueba", "Selecciona tu preferencia", "publication-admin");
        drafts.section(id, 0, null, "Preferencias", "publication-admin"); section = drafts.get(id).sections().getFirst().id();
        drafts.question(id, 1, section, new Question("control", "Preferencias", "¿Deseas dar detalles?", "radio", true,
                List.of(new Question.Option("SI", "Sí"), new Question.Option("NO", "No")), null, null, 0, 1, "", null, null), true, "publication-admin");
        drafts.question(id, 2, section, new Question("detalle", "Preferencias", "Escribe el detalle", "text", true, List.of(), "control", "SI", 0, 1, "", null, null), true, "publication-admin");
    }
    @AfterEach void clear() {
        jdbc.update("UPDATE survey SET draft_version_id = NULL, active_version_id = NULL");
        for (String table : List.of("survey_definition_event", "survey_question_option", "survey_question", "survey_section", "survey_version", "survey")) jdbc.update("DELETE FROM " + table);
        jdbc.update("DELETE FROM instrument_snapshot WHERE survey_slug LIKE 'encuesta-%'");
    }
    String draftPath() { return "/admin/encuestas/" + id; }
    String versionPath() { return "/admin/encuestas/versiones/" + id; }
    void publish() { publications.publish(id, 3, "publication-admin"); }

    @Test void previewRendersRealControlsWithoutCreatingResponsesOrEvents() throws Exception {
        long responses = jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class);
        long events = jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event", Long.class);
        mvc.perform(get(draftPath() + "/vista-previa")).andExpect(status().isOk()).andExpect(view().name("survey"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Comprobar prueba")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-depends-on=\"control\"")));
        mvc.perform(post(draftPath() + "/vista-previa").with(csrf()).param("revision", "3").param("consent", "SI").param("control", "NO"))
                .andExpect(status().isOk()).andExpect(model().attribute("previewSuccess", true));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response", Long.class)).isEqualTo(responses);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event", Long.class)).isEqualTo(events);
    }

    @Test void previewChecksMandatoryConditionalQuestionsAndUnknownFields() throws Exception {
        mvc.perform(post(draftPath() + "/vista-previa").with(csrf()).param("revision", "3").param("consent", "SI").param("control", "SI"))
                .andExpect(status().isUnprocessableEntity()).andExpect(model().attributeExists("errors"));
        mvc.perform(post(draftPath() + "/vista-previa").with(csrf()).param("revision", "3").param("consent", "SI").param("control", "NO").param("unknown", "x"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void stalePreviewDoesNotValidateAgainstAnotherRevision() throws Exception {
        drafts.updateHeader(id, 3, "Otro título", "", "publication-admin");
        mvc.perform(post(draftPath() + "/vista-previa").with(csrf()).param("revision", "3").param("consent", "SI").param("control", "NO"))
                .andExpect(status().isConflict());
    }

    @Test void publishingFreezesVersionAndRegistersSnapshotWithoutInstitutionalApproval() throws Exception {
        mvc.perform(get(draftPath() + "/publicar")).andExpect(status().isOk()).andExpect(view().name("survey-publish-confirm"));
        mvc.perform(post(draftPath() + "/publicar").with(csrf()).param("revision", "3"))
                .andExpect(redirectedUrl(versionPath()));
        var version = publications.get(id);
        assertThat(version.code()).isEqualTo("v1"); assertThat(version.status()).isEqualTo("PUBLICADA");
        assertThat(version.content().definition().questions()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT status FROM instrument_snapshot WHERE survey_slug = ? AND instrument_version = 'v1'", String.class, version.slug())).isEqualTo("BORRADOR");
        assertThat(drafts.recent()).isEmpty();
        mvc.perform(get(draftPath())).andExpect(redirectedUrl(versionPath()));
        mvc.perform(get(versionPath())).andExpect(status().isOk()).andExpect(view().name("survey-publication"));
    }

    @Test void everyStructuralEditIsRejectedAfterPublication() {
        publish(); var before = publications.get(id);
        assertThatThrownBy(() -> drafts.updateHeader(id, 4, "Alterado", "", "publication-admin")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> drafts.section(id, 4, section, "Alterada", "publication-admin")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> drafts.removeQuestion(id, 4, "detalle", "publication-admin")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> drafts.moveQuestion(id, 4, "detalle", -1, "publication-admin")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> drafts.duplicate(id, "publication-admin")).isInstanceOf(ResponseStatusException.class);
        assertThat(publications.get(id)).isEqualTo(before);
    }

    @Test void successorRetainsSurveyIdentityButChangesOnlyTheNewDraft() {
        publish(); var before = publications.get(id);
        String next = publications.successor(id, "publication-admin");
        assertThat(publications.successor(id, "publication-admin")).isEqualTo(next);
        assertThat(drafts.get(next).surveyId()).isEqualTo(before.surveyId());
        assertThat(drafts.get(next).slug()).isEqualTo(before.slug());
        drafts.updateHeader(next, 0, "Título de V2", "Descripción nueva", "publication-admin");
        publications.publish(next, 1, "publication-admin");
        assertThat(publications.get(next).code()).isEqualTo("v2");
        assertThat(publications.get(next).title()).isEqualTo("Título de V2");
        assertThat(publications.get(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT predecessor_id FROM survey_version WHERE id = ?", String.class, next)).isEqualTo(id);
    }

    @Test void closePreservesFrozenContentAndPublicLinkReturnsGone() throws Exception {
        publish(); var before = publications.get(id);
        mvc.perform(post(versionPath() + "/cerrar").with(csrf()).param("revision", "4")).andExpect(status().is3xxRedirection());
        var closed = publications.get(id);
        assertThat(closed.status()).isEqualTo("CERRADA"); assertThat(closed.hash()).isEqualTo(before.hash());
        assertThat(closed.content()).isEqualTo(before.content()); assertThat(closed.closedAt()).isNotNull();
        mvc.perform(get(before.publicPath()).with(anonymous())).andExpect(status().isGone());
        assertThat(drafts.get(publications.successor(id, "publication-admin")).items()).hasSize(2);
    }

    @Test void publicLinkIsVersionSpecificAndRejectsAPostWithoutAnOpenedToken() throws Exception {
        publish(); var version = publications.get(id);
        mvc.perform(get(version.publicPath()).with(anonymous())).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("action=\"" + version.publicPath() + "\"")));
        mvc.perform(post(version.publicPath()).with(anonymous()).with(csrf()).param("consent", "SI")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/encuestas/" + version.slug() + "/versiones/v999").with(anonymous())).andExpect(status().isNotFound());
    }

    @Test void readOnlyPublishedPreviewDoesNotExposeSubmitAction() throws Exception {
        publish();
        mvc.perform(get(versionPath() + "/vista-previa")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-preview-readonly=\"true\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("type=\"submit\""))));
    }

    @Test void publicationAndPreviewRequireAdminAndCsrf() throws Exception {
        for (String suffix : List.of("/vista-previa", "/publicar")) {
            mvc.perform(get(draftPath() + suffix).with(anonymous())).andExpect(status().is3xxRedirection());
            mvc.perform(post(draftPath() + suffix).param("revision", "3")).andExpect(status().isForbidden());
            mvc.perform(get(draftPath() + suffix).with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        }
        publish();
        mvc.perform(post(versionPath() + "/nueva-version")).andExpect(status().isForbidden());
        mvc.perform(post(versionPath() + "/cerrar").param("revision", "4")).andExpect(status().isForbidden());
        mvc.perform(get(versionPath() + "/vista-previa").with(anonymous())).andExpect(status().is3xxRedirection());
    }

    @Test void emptyDraftAndEmptySectionCannotBePublished() {
        String empty = drafts.create("Vacía", "", "publication-admin");
        assertThatThrownBy(() -> publications.publish(empty, 0, "publication-admin")).isInstanceOf(IllegalArgumentException.class);
        drafts.section(id, 3, null, "Sección vacía", "publication-admin");
        assertThatThrownBy(() -> publications.publish(id, 4, "publication-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(drafts.editable(id)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM instrument_snapshot WHERE survey_slug LIKE 'encuesta-%'", Integer.class)).isZero();
    }

    @Test void stalePublicationAndCloseCannotChangeTheVersion() throws Exception {
        mvc.perform(post(draftPath() + "/publicar").with(csrf()).param("revision", "2")).andExpect(status().isConflict());
        assertThat(drafts.get(id).revision()).isEqualTo(3);
        publish(); var before = publications.get(id);
        mvc.perform(post(versionPath() + "/cerrar").with(csrf()).param("revision", "3")).andExpect(status().isConflict());
        assertThat(publications.get(id)).isEqualTo(before);
    }

    @Test void invalidActorRollsBackClosingAndItsEvent() {
        publish(); var before = publications.get(id);
        assertThatThrownBy(() -> publications.close(id, 4, "")).isInstanceOf(IllegalArgumentException.class);
        assertThat(publications.get(id)).isEqualTo(before);
    }

    @Test void tamperedPublicationOrInstrumentIsRejected() {
        publish(); var version = publications.get(id);
        jdbc.update("UPDATE survey_version SET definition_hash = ? WHERE id = ?", "0".repeat(64), id);
        assertThatThrownBy(() -> publications.get(id)).isInstanceOf(ResponseStatusException.class);
        jdbc.update("UPDATE survey_version SET definition_hash = ? WHERE id = ?", version.hash(), id);
        jdbc.update("UPDATE instrument_snapshot SET definition_hash = ? WHERE survey_slug = ? AND instrument_version = 'v1'", "0".repeat(64), version.slug());
        assertThatThrownBy(() -> publications.successor(id, "publication-admin")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void concurrentPublicationCreatesOneSnapshotAndOnePublicationNumber() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = List.of(1, 2).stream().map(n -> executor.submit(() -> {
                start.await();
                try { publications.publish(id, 3, "publication-admin"); return 200; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            })).toList(); start.countDown();
            assertThat(List.of(futures.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS), futures.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(publications.get(id).code()).isEqualTo("v1");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM instrument_snapshot WHERE survey_slug LIKE 'encuesta-%'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event WHERE version_id = ? AND operation = 'PUBLICAR'", Integer.class, id)).isEqualTo(1);
    }

    @Test void concurrentSuccessorRequestsReturnOneDraft() throws Exception {
        publish(); var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures = List.of(1, 2).stream().map(n -> executor.submit(() -> { start.await(); return publications.successor(id, "publication-admin"); })).toList();
            start.countDown(); assertThat(futures.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(futures.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(drafts.recent()).hasSize(1);
    }

    @Test void fixturesUseEscapedContentAndIncludeAllPublicationScreens() throws Exception {
        drafts.updateHeader(id, 3, "<script>preview</script>", "Descripción de prueba", "publication-admin");
        var directory = Path.of(".codex-tmp", "publication-browser"); Files.createDirectories(directory);
        String preview = mvc.perform(get(draftPath() + "/vista-previa")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(preview).contains("&lt;script&gt;").doesNotContain("<script>preview</script>");
        Files.writeString(directory.resolve("preview.html"), preview);
        Files.writeString(directory.resolve("confirm.html"), mvc.perform(get(draftPath() + "/publicar")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        publications.publish(id, 4, "publication-admin");
        Files.writeString(directory.resolve("version.html"), mvc.perform(get(versionPath())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(directory.resolve("frozen.html"), mvc.perform(get(versionPath() + "/vista-previa")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(directory.resolve("public.html"), mvc.perform(get(publications.get(id).publicPath()).with(anonymous())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(directory.resolve("draft.html"), mvc.perform(get("/admin/encuestas/" + publications.successor(id, "publication-admin"))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}

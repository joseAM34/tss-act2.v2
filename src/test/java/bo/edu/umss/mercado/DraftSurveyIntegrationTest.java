package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.survey.DraftSurveyService;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:draft-editor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "draft-admin", roles = "ADMIN")
class DraftSurveyIntegrationTest {
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyCatalog catalog;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private String id;
    private String section;

    @BeforeEach void emptyDraftTables() {
        jdbc.update("UPDATE survey SET draft_version_id = NULL, active_version_id = NULL");
        for (String table : List.of("survey_definition_event", "survey_question_option", "survey_question", "survey_section", "survey_version", "survey"))
            jdbc.update("DELETE FROM " + table);
        id = drafts.create("Encuesta de prueba", "Solo datos sintéticos", "draft-admin");
        drafts.section(id, 0, null, "Datos generales", "draft-admin");
        section = drafts.get(id).sections().getFirst().id();
    }

    @Test void editorAndEveryFormAreRenderedWithCsrfAndAnAdminLink() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/admin/encuestas")));
        mvc.perform(get("/admin/encuestas")).andExpect(status().isOk()).andExpect(view().name("survey-drafts"));
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(view().name("survey-draft"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mvc.perform(get(path() + "/preguntas/nueva")).andExpect(status().isOk()).andExpect(view().name("survey-question-editor"));
        var fixtures = java.nio.file.Path.of(".codex-tmp", "draft-browser");
        java.nio.file.Files.createDirectories(fixtures);
        String copied = drafts.copyLegacy("titulados", "draft-admin");
        String copiedPath = "/admin/encuestas/" + copied;
        java.nio.file.Files.writeString(fixtures.resolve("list.html"), mvc.perform(get("/admin/encuestas")).andReturn().getResponse().getContentAsString());
        java.nio.file.Files.writeString(fixtures.resolve("draft.html"), mvc.perform(get(copiedPath)).andReturn().getResponse().getContentAsString());
        java.nio.file.Files.writeString(fixtures.resolve("question.html"), mvc.perform(get(copiedPath + "/preguntas/edad")).andReturn().getResponse().getContentAsString());
    }

    @Test void createFromThePanelAndEditHeaders() throws Exception {
        String location = mvc.perform(post("/admin/encuestas").with(csrf()).param("title", "Nueva").param("description", "Descripción"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String createdId = location.substring(location.lastIndexOf('/') + 1);
        assertThat(drafts.get(createdId).title()).isEqualTo("Nueva");
        mvc.perform(post(location + "/titulo").with(csrf()).param("revision", "0").param("title", "Renombrada").param("description", "Otra"))
                .andExpect(status().is3xxRedirection());
        assertThat(drafts.get(createdId).title()).isEqualTo("Renombrada");
    }

    @Test void anonymousCannotReadOrWriteDrafts() throws Exception {
        mvc.perform(get(path()).with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/encuestas").with(anonymous()).with(csrf()).param("title", "No permitido"))
                .andExpect(status().is3xxRedirection());
        assertThat(drafts.recent()).hasSize(1);
    }

    @Test void userRoleAndMissingCsrfCannotEdit() throws Exception {
        mvc.perform(get(path()).with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post(path() + "/titulo").param("revision", "1").param("title", "No"))
                .andExpect(status().isForbidden());
        assertThat(drafts.get(id).revision()).isEqualTo(1);
    }

    @Test void allMutationEndpointsRequireCsrf() throws Exception {
        for (String suffix : List.of("/duplicar", "/secciones", "/secciones/unknown/eliminar", "/secciones/unknown/mover",
                "/preguntas", "/preguntas/unknown", "/preguntas/unknown/eliminar", "/preguntas/unknown/mover"))
            mvc.perform(post(path() + suffix)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/encuestas/copiar/titulados")).andExpect(status().isForbidden());
        assertThat(drafts.get(id).revision()).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"titulados", "empleadores"})
    void copyLegacyPreservesCodesOptionsRulesAndSnapshots(String slug) throws Exception {
        var original = catalog.get(slug);
        String before = jdbc.queryForObject("SELECT definition_hash FROM instrument_snapshot WHERE survey_slug = ? AND instrument_version = ?",
                String.class, slug, original.version());
        String copiedId = drafts.copyLegacy(slug, "draft-admin");
        var copy = drafts.get(copiedId);
        assertThat(copy.slug()).isNotEqualTo(slug);
        assertThat(copy.sourceVersion()).isEqualTo(original.version());
        assertThat(copy.items()).hasSize(original.questions().size());
        for (int i = 0; i < original.questions().size(); i++) {
            Question old = original.questions().get(i), q = copy.items().get(i).question();
            assertThat(q.id()).isEqualTo(old.id()); assertThat(q.options()).isEqualTo(old.options());
            assertThat(q.label()).isEqualTo(old.label()); assertThat(q.type()).isEqualTo(old.type());
            assertThat(q.required()).isEqualTo(old.required()); assertThat(q.dependsOn()).isEqualTo(old.dependsOn());
            assertThat(q.dependsValue()).isEqualTo(old.dependsValue()); assertThat(q.minSelections()).isEqualTo(old.minSelections());
            assertThat(q.maxSelections()).isEqualTo(old.maxSelections()); assertThat(q.min()).isEqualTo(old.min()); assertThat(q.max()).isEqualTo(old.max());
        }
        drafts.updateHeader(copiedId, 0, "Cambiada solo en borrador", "", "draft-admin");
        assertThat(catalog.get(slug)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT definition_hash FROM instrument_snapshot WHERE survey_slug = ? AND instrument_version = ?",
                String.class, slug, original.version())).isEqualTo(before);
        mvc.perform(get("/encuestas/" + copy.slug())).andExpect(status().isNotFound());
        mvc.perform(get("/encuestas/" + slug)).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings = {"radio", "checkbox", "select", "text", "textarea", "number", "email", "tel", "url"})
    void supportedTypesRoundTripThroughNormalizedTables(String type) throws Exception {
        boolean choice = List.of("radio", "checkbox", "select").contains(type);
        List<Question.Option> options = choice ? List.of(new Question.Option("SI", "Sí"), new Question.Option("NO", "No")) : List.of();
        Question q = new Question("pregunta", "Datos generales", "Enunciado", type, true, options, null, null, 0,
                type.equals("checkbox") ? 2 : 1, "Ayuda", type.equals("number") ? 0 : null, type.equals("number") ? 10 : null);
        drafts.question(id, 1, section, q, true, "draft-admin");
        assertThat(drafts.get(id).items().getFirst().question()).isEqualTo(q);
        String html = mvc.perform(get(path() + "/preguntas/pregunta")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var selector = java.util.regex.Pattern.compile("<select id=\"type\"[^>]*>(.*?)</select>", java.util.regex.Pattern.DOTALL).matcher(html);
        assertThat(selector.find()).isTrue();
        String optionsHtml = selector.group(1);
        assertThat(java.util.regex.Pattern.compile("<option[^>]*>\\s*</option>").matcher(optionsHtml).find()).isFalse();
        assertThat(optionsHtml).contains("value=\"" + type + "\" selected=\"selected\">" + typeLabel(type) + "</option>");
    }

    @Test void webQuestionEditorSavesOptionsAndPreservesInvalidInput() throws Exception {
        mvc.perform(post(path() + "/preguntas").with(csrf()).param("revision", "1").param("questionId", "preferencia")
                .param("sectionId", section).param("label", "¿Qué prefieres?").param("type", "radio").param("options", "A | Opción A\nB | Opción B"))
                .andExpect(status().is3xxRedirection());
        assertThat(drafts.get(id).items().getFirst().question().options()).hasSize(2);
        mvc.perform(post(path() + "/preguntas/preferencia").with(csrf()).param("revision", "2").param("sectionId", section)
                .param("label", "Texto que se conserva").param("type", "radio").param("options", "A | Primera\nA | Duplicada"))
                .andExpect(status().isUnprocessableEntity()).andExpect(view().name("survey-question-editor"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Texto que se conserva")));
        assertThat(drafts.get(id).revision()).isEqualTo(2);
    }

    @Test void duplicateIsIndependentAndHasItsOwnAudit() {
        add(text("primera"));
        String copyId = drafts.duplicate(id, "another-admin");
        drafts.updateHeader(copyId, 0, "Copia independiente", "", "another-admin");
        assertThat(drafts.get(id).title()).isEqualTo("Encuesta de prueba");
        assertThat(drafts.get(copyId).surveyId()).isNotEqualTo(drafts.get(id).surveyId());
        assertThat(drafts.get(copyId).items()).isEqualTo(drafts.get(id).items());
        assertThat(jdbc.queryForObject("SELECT actor FROM survey_definition_event WHERE version_id = ? AND revision = 0", String.class, copyId))
                .isEqualTo("another-admin");
    }

    @Test void staleRevisionCannotOverwriteOrAddAnAuditEvent() throws Exception {
        drafts.updateHeader(id, 1, "Primera pestaña", "", "draft-admin");
        mvc.perform(post(path() + "/titulo").with(csrf()).param("revision", "1").param("title", "Segunda pestaña"))
                .andExpect(status().isConflict()).andExpect(content().string(org.hamcrest.Matchers.containsString("otra pestaña")));
        assertThat(drafts.get(id).title()).isEqualTo("Primera pestaña");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event WHERE version_id = ?", Integer.class, id)).isEqualTo(3);
    }

    @Test void sectionAndQuestionOrderingAndDeletionWork() {
        add(text("primera")); add(text("segunda"));
        drafts.moveQuestion(id, 3, "segunda", -1, "draft-admin");
        assertThat(drafts.get(id).items().stream().map(i -> i.question().id())).containsExactly("segunda", "primera");
        drafts.section(id, 4, section, "Renombrada", "draft-admin");
        assertThat(drafts.get(id).items().getFirst().question().section()).isEqualTo("Renombrada");
        drafts.section(id, 5, null, "Otra sección", "draft-admin");
        String other = drafts.get(id).sections().getLast().id();
        drafts.moveSection(id, 6, other, -1, "draft-admin");
        assertThat(drafts.get(id).sections().getFirst().id()).isEqualTo(other);
        drafts.removeSection(id, 7, other, "draft-admin");
        drafts.removeQuestion(id, 8, "primera", "draft-admin"); drafts.removeQuestion(id, 9, "segunda", "draft-admin");
        drafts.removeSection(id, 10, section, "draft-admin");
        assertThat(drafts.get(id).sections()).isEmpty(); assertThat(drafts.get(id).items()).isEmpty();
    }

    @Test void conditionPreventsDeletingOrMovingItsControlAfterTheDependent() {
        add(new Question("control", "Datos generales", "Control", "radio", true, List.of(new Question.Option("SI", "Sí")), null, null, 0, 1, "", null, null));
        add(new Question("detalle", "Datos generales", "Detalle", "text", false, List.of(), "control", "SI", 0, 1, "", null, null));
        assertThatThrownBy(() -> drafts.removeQuestion(id, 3, "control", "draft-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> drafts.moveQuestion(id, 3, "control", 1, "draft-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(drafts.get(id).revision()).isEqualTo(3);
        assertThat(drafts.get(id).items()).hasSize(2);
    }

    @Test void unknownTypesForeignSectionsAndInvalidRangesAreRejectedWithoutWriting() {
        assertThatThrownBy(() -> drafts.question(id, 1, "foreign-section", text("one"), true, "draft-admin")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> add(new Question("one", "Datos generales", "E", "file", false, List.of(), null, null, 0, 1, "", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> add(new Question("one", "Datos generales", "E", "number", false, List.of(), null, null, 0, 1, "", 20, 10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(drafts.get(id).revision()).isEqualTo(1); assertThat(drafts.get(id).items()).isEmpty();
    }

    @Test void invalidCodesDuplicateOptionsAndUnresolvedConditionsAreRejected() {
        assertThatThrownBy(() -> add(text("bad-id"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> add(new Question("q", "Datos generales", "E", "radio", false,
                List.of(new Question.Option("A", "Uno"), new Question.Option("A", "Dos")), null, null, 0, 1, "", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> add(new Question("q", "Datos generales", "E", "text", false, List.of(), "missing", "SI", 0, 1, "", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(drafts.get(id).items()).isEmpty();
    }

    @Test void cannotDeleteANonemptySectionOrMoveBeyondBounds() {
        add(text("primera"));
        assertThatThrownBy(() -> drafts.removeSection(id, 2, section, "draft-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> drafts.moveQuestion(id, 2, "primera", -1, "draft-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(drafts.get(id).revision()).isEqualTo(2);
    }

    @Test void escapedUserTextAndAuditHashesCoverEmptySections() throws Exception {
        drafts.updateHeader(id, 1, "<script>alert('title')</script>", "<img src=x onerror=alert(1)>", "draft-admin");
        mvc.perform(get(path())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>alert('title')</script>"))));
        String before = jdbc.queryForObject("SELECT definition_hash FROM survey_definition_event WHERE version_id = ? AND revision = 2", String.class, id);
        drafts.section(id, 2, section, "Sección sin preguntas renombrada", "draft-admin");
        var event = jdbc.queryForMap("SELECT definition_json, definition_hash FROM survey_definition_event WHERE version_id = ? AND revision = 3", id);
        assertThat(event.get("definition_hash")).isNotEqualTo(before);
        assertThat(CanonicalJson.hash(CanonicalJson.read((String) event.get("definition_json"), java.util.Map.class)))
                .isEqualTo(event.get("definition_hash"));
    }

    @Test void invalidHeaderPreservesInputAndDoesNotWrite() throws Exception {
        mvc.perform(post(path() + "/titulo").with(csrf()).param("revision", "1").param("title", "").param("description", "Conservar texto"))
                .andExpect(status().isUnprocessableEntity()).andExpect(content().string(org.hamcrest.Matchers.containsString("Conservar texto")));
        assertThat(drafts.get(id).revision()).isEqualTo(1);
    }

    @Test void concurrentEditorsSaveExactlyOneRevisionAndOneEvent() throws Exception {
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = List.of("Pestaña A", "Pestaña B").stream().map(title -> executor.submit(() -> {
                start.await();
                try { drafts.updateHeader(id, 1, title, "", "draft-admin"); return 200; }
                catch (ResponseStatusException ex) { return ex.getStatusCode().value(); }
            })).toList();
            start.countDown();
            assertThat(List.of(results.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS),
                    results.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        assertThat(drafts.get(id).revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event WHERE version_id = ?", Integer.class, id)).isEqualTo(3);
    }

    @Test void reservedFormIdentifiersCannotBecomeQuestions() {
        assertThatThrownBy(() -> add(text("consent"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> add(text("formToken"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(drafts.get(id).revision()).isEqualTo(1);
    }

    private String path() { return "/admin/encuestas/" + id; }
    private String typeLabel(String type) {
        return java.util.Map.of("radio", "Selección única", "checkbox", "Selección múltiple", "select", "Desplegable", "text", "Texto corto",
                "textarea", "Texto largo", "number", "Número entero", "email", "Correo electrónico", "tel", "Teléfono", "url", "Enlace web").get(type);
    }
    private Question text(String code) { return new Question(code, "Datos generales", "Pregunta " + code, "text", false, List.of(), null, null, 0, 1, "", null, null); }
    private void add(Question q) { drafts.question(id, drafts.get(id).revision(), section, q, true, "draft-admin"); }
}

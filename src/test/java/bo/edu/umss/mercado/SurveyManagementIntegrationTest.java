package bo.edu.umss.mercado;

import bo.edu.umss.mercado.importing.CsvImportService;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:management-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="management-admin",roles="ADMIN") @Transactional
class SurveyManagementIntegrationTest {
    @Autowired SurveyManagementService management;
    @Autowired SurveyWorkspaceService spaces;
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired SurveyValidator validator;
    @Autowired SurveyResponseRepository responses;
    @Autowired CsvImportService imports;
    @Autowired GenericPreparationService preparations;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    static final String ACTOR = "management-admin";

    String draft() { return drafts.create("Encuesta gestionable <script>alert(1)</script>", "Descripción", ACTOR); }
    String publish() {
        String id = draft(); drafts.section(id, 0, null, "Datos", ACTOR);
        drafts.question(id, 1, drafts.get(id).sections().getFirst().id(), new Question("opcion", "Datos", "Opción", "radio", false,
                List.of(new Question.Option("SI", "Sí"), new Question.Option("NO", "No")), null, null, 0, 1, "", null, null), true, ACTOR);
        publications.publish(id, 2, ACTOR); publications.activate(id, publications.get(id).revision(), ACTOR); return id;
    }
    String response(String version) {
        var values = new LinkedMultiValueMap<String,String>(); values.set("consent", "SI"); values.set("opcion", "SI");
        return responses.savePublished(version, UUID.randomUUID().toString(), validator.validate(publications.get(version).content().definition(), values));
    }

    @Test void emptyInstallationDoesNotInventRequiredSurveys() throws Exception {
        assertThat(spaces.all()).isEmpty();
        mvc.perform(get("/admin/espacio")).andExpect(status().isOk()).andExpect(content().string(containsString("Aún no hay encuestas")));
        mvc.perform(get("/").with(anonymous())).andExpect(status().isOk()).andExpect(content().string(containsString("Todavía no hay encuestas disponibles")))
                .andExpect(content().string(not(containsString("href=\"/encuestas/titulados\""))));
        // El historial y los enlaces anteriores se conservan; no se usan como filas predefinidas vacías.
        assertThat(spaces.open("titulados", "").legacy()).isTrue();
    }

    @Test void existingOriginalResponsesRemainVisibleWithoutReconstructingTheirInstrument() {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO survey_response (id, submission_token, survey_slug, instrument_version, submitted_at, consent) VALUES (?, ?, 'titulados', 'historica-sin-definicion', CURRENT_TIMESTAMP, TRUE)",
                id, UUID.randomUUID().toString());
        assertThat(spaces.all()).hasSize(1).allMatch(c -> c.legacy() && c.slug().equals("titulados") && c.responses() == 1);
        assertThat(spaces.open("titulados", "historica-sin-definicion").selected().status()).isEqualTo("HISTORICA");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM instrument_snapshot WHERE survey_slug = 'titulados' AND instrument_version = 'historica-sin-definicion'", Long.class)).isZero();
    }

    @Test void draftCanBeDeletedAndLeavesOnlyAnAdministrativeTombstone() throws Exception {
        String id = draft(), slug = drafts.get(id).slug(); var state = management.get(slug);
        mvc.perform(get("/admin/espacio/" + slug + "/gestion")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("Eliminar encuesta"))).andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        mvc.perform(post("/admin/espacio/" + slug + "/gestion/eliminar").with(csrf()).param("expectedHash", state.hash()).param("confirm", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/espacio"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey WHERE slug = ?", Long.class, slug)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event WHERE version_id = ?", Long.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_management_event WHERE survey_slug = ? AND operation = 'ELIMINAR' AND state_hash = ?", Long.class, slug, state.hash())).isEqualTo(1);
    }

    @Test void allEmptyPublishedAndSuccessorStructuresCanBeDeletedTogether() {
        String id = publish(), slug = publications.get(id).slug();
        String next = publications.successor(id, ACTOR); publications.publish(next, 0, ACTOR); publications.successor(next, ACTOR);
        assertThat(management.get(slug).versions()).isEqualTo(3);
        management.delete(slug, management.get(slug).hash(), ACTOR);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_version WHERE id IN (?, ?)", Long.class, id, next)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM instrument_snapshot WHERE survey_slug = ?", Long.class, slug)).isZero();
    }

    @Test void archivingClosesEveryPublicationButPreservesCapturedDataAndDirectHistory() throws Exception {
        String id = publish(), slug = publications.get(id).slug(), response = response(id), originalHash = publications.get(id).hash();
        String second = publications.successor(id, ACTOR); publications.publish(second, 0, ACTOR);
        var state = management.get(slug); assertThat(state.deletable()).isFalse();
        management.archive(slug, state.hash(), ACTOR);
        assertThat(management.get(slug).archived()).isTrue();
        assertThat(publications.get(id).status()).isEqualTo("CERRADA"); assertThat(publications.get(second).status()).isEqualTo("CERRADA");
        assertThat(publications.get(id).hash()).isEqualTo(originalHash); assertThat(publications.get(id).active()).isFalse();
        assertThat(spaces.all()).noneMatch(c -> c.slug().equals(slug)); assertThat(spaces.all(true)).anyMatch(c -> c.slug().equals(slug) && c.archived());
        assertThat(publications.activeDefinitions()).noneMatch(s -> s.slug().equals(slug));
        assertThat(spaces.counts(spaces.open(slug, "v1")).total()).isEqualTo(1);
        mvc.perform(get("/admin/respuestas/" + response)).andExpect(status().isOk());
        mvc.perform(get("/admin/espacio").param("archivadas", "true")).andExpect(status().isOk()).andExpect(content().string(containsString("Restaurar o gestionar")));
        mvc.perform(get("/encuestas/" + slug + "/versiones/v1").with(anonymous())).andExpect(status().isGone());
        assertThatThrownBy(() -> response(id)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> management.delete(slug, management.get(slug).hash(), ACTOR)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("respuestas");
    }

    @Test void restoringDoesNotReopenPublicationsAndArchivedDraftCannotBePublished() throws Exception {
        String id = publish(), slug = publications.get(id).slug(); String draft = publications.successor(id, ACTOR);
        management.archive(slug, management.get(slug).hash(), ACTOR);
        assertThatThrownBy(() -> publications.publish(draft, 0, ACTOR)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Restaura");
        mvc.perform(post("/admin/espacio/" + slug + "/gestion/restaurar").with(csrf()).param("expectedHash", management.get(slug).hash()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/espacio/" + slug));
        assertThat(management.get(slug).archived()).isFalse(); assertThat(publications.get(id).status()).isEqualTo("CERRADA");
        assertThat(publications.activeDefinitions()).noneMatch(s -> s.slug().equals(slug));
        publications.publish(draft, 0, ACTOR); publications.activate(draft, publications.get(draft).revision(), ACTOR);
        assertThat(publications.activeVersion(slug).id()).isEqualTo(draft);
    }

    @Test void importReviewOrPreparationConfigurationProtectsSurveyEvenWithoutResponses() {
        String id = publish(), slug = publications.get(id).slug();
        imports.upload(id, "respuestas.csv", "consentimiento,opcion\nSI,SI\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), "COMA", "GENERICO", ACTOR);
        assertThat(management.get(slug).responses()).isZero(); assertThat(management.get(slug).imports()).isEqualTo(1);
        assertThat(management.get(slug).deletable()).isFalse();
        assertThatThrownBy(() -> management.delete(slug, management.get(slug).hash(), ACTOR)).isInstanceOf(ResponseStatusException.class);
        String second = publish(), secondSlug = publications.get(second).slug();
        preparations.configure(second, 0, List.of(new GenericPreparationService.Field("opcion", "NOMINAL", "")), ACTOR);
        assertThat(management.get(secondSlug).configurations()).isEqualTo(1); assertThat(management.get(secondSlug).deletable()).isFalse();
    }

    @Test void managementDetectsConcurrentEditsAndNewResponsesBeforeDeleting() throws Exception {
        String id = draft(), slug = drafts.get(id).slug(); String hash = management.get(slug).hash();
        drafts.updateHeader(id, 0, "Nombre editado", "", ACTOR);
        mvc.perform(post("/admin/espacio/" + slug + "/gestion/eliminar").with(csrf()).param("expectedHash", hash).param("confirm", "true"))
                .andExpect(status().isConflict()).andExpect(content().string(containsString("cambiaron")));
        String published = publish(), publicSlug = publications.get(published).slug(), before = management.get(publicSlug).hash(); response(published);
        assertThatThrownBy(() -> management.delete(publicSlug, before, ACTOR)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("cambiaron");
        assertThat(management.get(publicSlug).responses()).isEqualTo(1);
    }

    @Test void operationsRequireAdminCsrfExplicitDeleteConfirmationAndKnownSurvey() throws Exception {
        String id = draft(), slug = drafts.get(id).slug(), path = "/admin/espacio/" + slug + "/gestion";
        String hash = management.get(slug).hash();
        mvc.perform(get(path).with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get(path).with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        for (String op : List.of("archivar", "restaurar", "eliminar")) {
            mvc.perform(post(path + "/" + op).param("expectedHash", hash)).andExpect(status().isForbidden());
            mvc.perform(post(path + "/" + op).with(user("reader").roles("USER")).with(csrf()).param("expectedHash", hash)).andExpect(status().isForbidden());
        }
        mvc.perform(post(path + "/eliminar").with(csrf()).param("expectedHash", hash)).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/espacio/no-existe/gestion")).andExpect(status().isNotFound());
        assertThat(management.get(slug).deletable()).isTrue();
    }
}

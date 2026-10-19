package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.importing.*;
import bo.edu.umss.mercado.survey.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:predefined-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="preset-admin",roles="ADMIN")
class PredefinedSurveysIntegrationTest {
    @Autowired PredefinedSurveyService service;
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired SurveyManagementService management;
    @Autowired SurveyWorkspaceService spaces;
    @Autowired CsvImportService imports;
    @Autowired CaptureStore captures;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private final List<String> keys=List.of("titulados-oficial","empleadores-oficial");
    @BeforeEach @AfterEach void clear(){
        for(String table:List.of("xlsx_import_workbook","csv_import_row","csv_import_confirmation","csv_import_mapping","csv_import_batch","generated_report_cross","generated_report","report_template_revision","report_template","survey_general_cross","survey_general_analysis","survey_preparation_run","survey_preparation_config"))jdbc.update("DELETE FROM "+table);
        jdbc.update("DELETE FROM survey_answer");jdbc.update("DELETE FROM survey_question_state");jdbc.update("DELETE FROM survey_response");
        jdbc.update("UPDATE survey SET draft_version_id=NULL,active_version_id=NULL");
        for(String table:List.of("survey_definition_event","survey_question_option","survey_question","survey_section","survey_version","survey"))jdbc.update("DELETE FROM "+table);
        jdbc.update("DELETE FROM instrument_snapshot WHERE survey_slug LIKE 'encuesta-%'");
    }
    @Test void emptyInstallationIsOptionalAndOrdinaryDraftsAreReusableEditableAndRecoverable(){
        assertThat(spaces.all()).isEmpty();assertThat(drafts.recent()).isEmpty();assertThat(publications.activeDefinitions()).isEmpty();
        var first=service.install(keys,"preset-admin");assertThat(first).allMatch(PredefinedSurveyService.Installed::created);
        assertThat(first.stream().map(i->drafts.get(i.versionId()).items().size()).toList()).containsExactly(49,30);
        assertThat(spaces.all()).hasSize(2).allMatch(c->!c.legacy());
        assertThat(service.install(keys,"preset-admin")).extracting(PredefinedSurveyService.Installed::versionId).containsExactlyElementsOf(first.stream().map(PredefinedSurveyService.Installed::versionId).toList());
        var edited=drafts.get(first.getFirst().versionId());
        drafts.updateHeader(edited.id(),edited.revision(),"Personalizada por el equipo",edited.description(),"preset-admin");
        var recovered=service.install(List.of(keys.getFirst()),"preset-admin").getFirst();
        assertThat(recovered.created()).isTrue();assertThat(recovered.versionId()).isNotEqualTo(edited.id());
        assertThat(drafts.get(edited.id()).title()).isEqualTo("Personalizada por el equipo");
        var empty=drafts.get(recovered.versionId());var state=management.get(empty.slug());
        management.delete(empty.slug(),state.hash(),"preset-admin");
        var again=service.install(List.of(keys.getFirst()),"preset-admin").getFirst();assertThat(again.created()).isTrue();
        assertThat(again.versionId()).isNotEqualTo(empty.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response",Integer.class)).isZero();
    }
    @Test void publicationImportLinkAndVersionEditingUseTheOrdinaryJourney() throws Exception {
        String id=service.install(List.of("empleadores-oficial"),"preset-admin").getFirst().versionId();
        var draft=drafts.get(id);
        mvc.perform(get("/admin/encuestas/"+id)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("criterio_18")));
        publications.publish(id,draft.revision(),"preset-admin");
        var version=publications.get(id);
        mvc.perform(get("/admin/espacio/"+draft.slug()+"/importar").param("version",version.content().definition().version())).andExpect(status().isOk());
        String successor=publications.successor(id,"preset-admin");assertThat(drafts.get(successor).items()).hasSize(30);
        drafts.updateHeader(successor,0,"Edición oficial revisada",draft.description(),"preset-admin");
        var restored=service.install(List.of("empleadores-oficial"),"preset-admin").getFirst();
        assertThat(restored.created()).isTrue();assertThat(publications.get(id).content().definition().title()).isEqualTo(draft.title());
    }
    @Test void regeneratedEmployerPresetImportsObservedTeachingCargoWithoutChangingTheEarlierPublication() {
        var template=service.templates().stream().filter(t->t.key().equals("empleadores-oficial")).findFirst().orElseThrow();
        var previousQuestions=template.questions().stream().map(q->{
            if(!q.id().equals("cargos_desempenados"))return q;
            var options=q.options().stream().filter(o->!o.value().equals("DOCENTES")).toList();
            return new Question(q.id(),q.section(),q.label(),q.type(),q.required(),options,q.dependsOn(),q.dependsValue(),q.minSelections(),options.size(),q.hint(),q.min(),q.max());
        }).toList();
        String previous=drafts.fromTemplate(new SurveyDefinition(template.key(),template.title(),template.description(),"2026-10-fuente-oficial-1",previousQuestions),"preset-admin");
        publications.publish(previous,drafts.get(previous).revision(),"preset-admin");
        var frozen=publications.get(previous).content();

        var installed=service.install(List.of(template.key()),"preset-admin").getFirst();
        assertThat(installed.created()).isTrue();assertThat(installed.versionId()).isNotEqualTo(previous);
        var draft=drafts.get(installed.versionId());
        assertThat(draft.sourceVersion()).isEqualTo("2026-10-fuente-oficial-2");
        var cargo=draft.definition().questions().stream().filter(q->q.id().equals("cargos_desempenados")).findFirst().orElseThrow();
        assertThat(cargo.options()).contains(new Question.Option("DOCENTES","Docentes"));
        assertThat(cargo.maxSelections()).isEqualTo(cargo.options().size());
        publications.publish(draft.id(),draft.revision(),"preset-admin");
        assertThat(publications.get(previous).content()).isEqualTo(frozen);
        assertThat(frozen.definition().questions().stream().filter(q->q.id().equals(cargo.id())).findFirst().orElseThrow().options())
                .extracting(Question.Option::value).doesNotContain("DOCENTES");

        String technical=cargo.options().stream().filter(o->o.value().equals("TECNICO")).findFirst().orElseThrow().label();
        var questions=draft.definition().questions();
        String headers=String.join(";",questions.stream().map(Question::id).toList());
        String values=String.join(";",questions.stream().map(q->q.id().equals(cargo.id())?technical+", Docentes":"").toList());
        byte[] original=(headers+"\n"+values+"\n").getBytes(StandardCharsets.UTF_8);
        String batch=imports.upload(draft.id(),"cargos-ficticios.csv",original,"PUNTO_COMA","GOOGLE_FORMS","preset-admin");
        var proposed=ImportSuggestions.propose(publications.get(draft.id()).content().definition(),imports.batch(batch).table()).mapping();
        assertThat(proposed.multipleSeparator()).isEqualTo("MIXTO_V1");
        var mapping=new CsvImportService.Mapping(proposed.columns(),-2,CsvImportService.HISTORICAL_VALUE,-1,-1,"AUTO","MIXTO_V1",true,"Recepción de prueba revisada",CsvImportService.HISTORICAL_ACK);
        String revision=imports.mapColumns(batch,0,mapping,"preset-admin");
        var preview=imports.preview(batch,revision);assertThat(preview.valid()).isTrue();
        String response=imports.confirm(batch,revision,preview.hash(),false,"preset-admin").getFirst();
        assertThat(captures.input(response).answers().get(cargo.id())).containsExactlyInAnyOrder("TECNICO","DOCENTES");
        assertThat(imports.original(batch)).isEqualTo(original);
        assertThat(publications.get(previous).content()).isEqualTo(frozen);
        var repeated=service.install(List.of(template.key()),"preset-admin").getFirst();
        assertThat(repeated.created()).isFalse();assertThat(repeated.versionId()).isEqualTo(draft.id());
    }
    @Test void installAndFooterAreAdminOnlyCsrfProtectedAndDoNotInstallOnGet() throws Exception {
        mvc.perform(get("/admin/encuestas/predefinidas").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/encuestas/predefinidas").with(user("lector").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/encuestas/predefinidas").param("keys",keys.getFirst())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/encuestas/predefinidas")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        assertThat(drafts.recent()).isEmpty();
        var fixture=java.nio.file.Path.of(".codex-tmp/predefined-browser");java.nio.file.Files.createDirectories(fixture);
        java.nio.file.Files.writeString(fixture.resolve("preset.html"),mvc.perform(get("/admin/encuestas/predefinidas")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get("/").with(anonymous())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Establecer encuestas predefinidas"))));
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Establecer encuestas predefinidas")));
        mvc.perform(post("/admin/encuestas/predefinidas").with(csrf()).param("keys",keys.toArray(String[]::new))).andExpect(status().isOk());
        assertThat(drafts.recent()).hasSize(2);
        java.nio.file.Files.writeString(fixture.resolve("workspace.html"),mvc.perform(get("/admin/espacio")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String slug=drafts.get(drafts.recent().getFirst().id()).slug();
        java.nio.file.Files.writeString(fixture.resolve("manage.html"),mvc.perform(get("/admin/espacio/"+slug+"/gestion")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThatThrownBy(()->service.install(List.of(keys.getFirst(),"inexistente"),"preset-admin")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(drafts.recent()).hasSize(2);
    }
    @Test void simultaneousRequestsDoNotCreateDuplicateOriginals() throws Exception {
        var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{
            var jobs=List.of(1,2).stream().map(n->pool.submit(()->{start.await();return service.install(keys,"preset-admin");})).toList();start.countDown();
            assertThat(jobs.getFirst().get().stream().map(PredefinedSurveyService.Installed::versionId).toList()).isEqualTo(jobs.getLast().get().stream().map(PredefinedSurveyService.Installed::versionId).toList());
            assertThat(drafts.recent()).hasSize(2);
        }finally{pool.shutdownNow();}
    }
}

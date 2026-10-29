package bo.edu.umss.mercado;

import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import bo.edu.umss.mercado.statistics.GenericAnalysisService;
import bo.edu.umss.mercado.reporting.ReportService;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:workspace-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="workspace-admin",roles="ADMIN") @Transactional
class SurveyWorkspaceIntegrationTest {
    @Autowired SurveyWorkspaceService spaces;
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired SurveyResponseRepository responses;
    @Autowired SurveyValidator validator;
    @Autowired GenericPreparationService preparation;
    @Autowired GenericAnalysisService analyses;
    @Autowired ReportService reports;
    @Autowired bo.edu.umss.mercado.reporting.AnalysisDeletionService analysisDeletion;
    @Autowired bo.edu.umss.mercado.importing.CsvImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String first,second,other,slug,firstResponse,secondResponse;
    String published(String title) {
        String id=drafts.create(title,"Encuesta ficticia para pruebas", "workspace-admin");
        drafts.section(id,0,null,"Preferencias","workspace-admin");
        String section=drafts.get(id).sections().getFirst().id();
        drafts.question(id,1,section,new Question("opcion","Preferencias","Opción","radio",true,
                List.of(new Question.Option("A","Categoría A"),new Question.Option("B","Categoría B")),null,null,0,1,"",null,null),true,"workspace-admin");
        publications.publish(id,2,"workspace-admin");return id;
    }
    String response(String id) {
        var values=new LinkedMultiValueMap<String,String>();values.set("consent","SI");values.set("opcion","A");
        return responses.savePublished(id,UUID.randomUUID().toString(),validator.validate(publications.get(id).content().definition(),values));
    }
    @BeforeEach void setup() {
        first=published("Encuesta <script>alert(1)</script>");slug=publications.get(first).slug();firstResponse=response(first);
        second=publications.successor(first,"workspace-admin");publications.publish(second,0,"workspace-admin");
        publications.activate(second,publications.get(second).revision(),"workspace-admin");secondResponse=response(second);
        other=published("Otra encuesta");response(other);
    }
    @Test void entryAndCardsIncludeLegacyAndDraftOnlySurveys() throws Exception {
        var draft=drafts.create("Solo borrador","","workspace-admin");
        mvc.perform(get("/admin")).andExpect(status().isOk()).andExpect(view().name("workspace-list"));
        mvc.perform(get("/admin/espacio")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(containsString("Mis encuestas"))).andExpect(content().string(containsString("Solo borrador")))
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
        assertThat(spaces.all()).noneMatch(c->c.slug().equals("titulados")).anyMatch(c->c.slug().equals(drafts.get(draft).slug()));
    }
    @Test void defaultsToActiveAndRejectsForeignVersion() throws Exception {
        assertThat(spaces.open(slug,"").selected().id()).isEqualTo(second);
        mvc.perform(get("/admin/espacio/"+slug).param("version","does-not-exist")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/espacio/not-found")).andExpect(status().isNotFound());
        mvc.perform(get("/admin/espacio/"+slug+"/unknown")).andExpect(status().isNotFound());
    }
    @Test void responsesAreScopedBySurveyAndVersionAndLinksKeepSelection() throws Exception {
        mvc.perform(get("/admin/espacio/"+slug+"/respuestas").param("version","v1"))
                .andExpect(status().isOk()).andExpect(content().string(containsString(firstResponse)))
                .andExpect(content().string(not(containsString(secondResponse))))
                .andExpect(content().string(containsString("/resultados?version=v1")));
        assertThat(spaces.counts(spaces.open(slug,"v1")).total()).isEqualTo(1);
    }
    @Test void latestOriginRevisionIsCountedWithoutDuplication() {
        var now=java.time.OffsetDateTime.now();
        for(String origin:List.of("REAL","SINTETICO")) jdbc.update("INSERT INTO wrangling_origin_classification (id,response_id,origin,reason,actor,created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID().toString(),firstResponse,origin,"Prueba","workspace-admin",now);
        var counts=spaces.counts(spaces.open(slug,"v1"));
        assertThat(counts.total()).isEqualTo(1);assertThat(counts.synthetic()).isEqualTo(1);assertThat(counts.real()).isZero();assertThat(counts.unverified()).isZero();
        assertThat(spaces.counts(spaces.open(slug,"v2")).unverified()).isEqualTo(1);
    }
    @Test void rendersEverySectionAndDraftState() throws Exception {
        for(String section:List.of("resumen","versiones","respuestas","resultados","importar","reportes")) {
            var result=mvc.perform(get("/admin/espacio/"+slug+"/"+section).param("version","v2"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(content().string(containsString("id=\"workspace-version\""))).andReturn();
            if(Boolean.getBoolean("workspace.capture")) {
                Path dir=Path.of(".codex-tmp/workspace-browser");Files.createDirectories(dir);Files.writeString(dir.resolve(section+".html"),result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        String draft=publications.successor(second,"workspace-admin");
        for(String section:List.of("resumen","respuestas","resultados","importar","reportes")) mvc.perform(get("/admin/espacio/"+slug+"/"+section).param("version",drafts.get(draft).versionCode()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Estás viendo un borrador")))
                .andExpect(content().string(not(containsString("name=\"file\""))));
    }
    @Test void noDraftDefaultAndClosedVersionStillHasHistory() throws Exception {
        publications.close(second,publications.get(second).revision(),"workspace-admin");
        String draft=publications.successor(second,"workspace-admin");
        assertThat(spaces.open(slug,"").selected().draft()).isFalse();
        mvc.perform(get("/admin/espacio/"+slug+"/importar").param("version","v2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Esta versión está cerrada")))
                .andExpect(content().string(not(containsString("name=\"file\""))));
        assertThat(drafts.get(draft).surveyId()).isEqualTo(publications.get(second).surveyId());
    }
    @Test void publicCannotReadAndNonAdminCannotMutateAndCsrfRequired() throws Exception {
        mvc.perform(get("/admin/espacio/"+slug).with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/espacio/"+slug).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/espacio/"+slug+"/variante").param("version","v2")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/espacio").param("title","No CSRF")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/espacio/"+slug+"/variante").with(user("viewer").roles("USER")).with(csrf()).param("version","v2")).andExpect(status().isForbidden());
    }
    @Test void variantCopiesStructureAndReferenceWithoutResponsesOrChangingOriginal() throws Exception {
        String hash=publications.get(first).hash();
        String target=mvc.perform(post("/admin/espacio/"+slug+"/variante").with(csrf()).param("version","v1"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        var copy=drafts.get(target.substring(target.lastIndexOf('/')+1));
        assertThat(copy.surveyId()).isNotEqualTo(publications.get(first).surveyId());
        assertThat(copy.sourceSlug()).isEqualTo(slug);assertThat(copy.sourceVersion()).isEqualTo("v1");
        assertThat(copy.definition().questions()).isEqualTo(publications.get(first).content().definition().questions());
        assertThat(spaces.counts(spaces.open(copy.slug(),"")).total()).isZero();
        assertThat(publications.get(first).hash()).isEqualTo(hash);
    }
    @Test void legacyWorkspaceDoesNotCreateSnapshotsOrReassignResponses() throws Exception {
        long snapshots=jdbc.queryForObject("SELECT COUNT(*) FROM instrument_snapshot",Long.class);
        for(String section:List.of("resumen","versiones","respuestas","resultados","importar","reportes")) mvc.perform(get("/admin/espacio/titulados/"+section)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM instrument_snapshot",Long.class)).isEqualTo(snapshots);
        assertThat(spaces.open("titulados","").legacy()).isTrue();
    }
    String prepare(String version) {
        String cfg=preparation.configure(version,0,List.of(new GenericPreparationService.Field("opcion","NOMINAL","")),"workspace-admin");
        return preparation.prepare(version,cfg,"NO_DECLARADO","BORRADOR",null,null,"workspace-admin");
    }
    @Test void deletingOneAnalysisRemovesItsReportsAndKeepsSourcesAndOtherAnalyses() throws Exception {
        String p=prepare(first),a=analyses.create(p,"workspace-admin"),otherAnalysis=analyses.create(p,"workspace-admin");
        String template=reports.createTemplate(new ReportService.Template("Reporte","",List.of("CONTEXTO"),false),"workspace-admin");
        String revision=reports.latest(template).id();
        String report=reports.generate(revision,a,List.of(),"","workspace-admin"),kept=reports.generate(revision,otherAnalysis,List.of(),"","workspace-admin");
        String url="/admin/espacio/"+slug+"/reportes/analisis/"+a+"/eliminar";
        mvc.perform(get("/admin/espacio/"+slug+"/reportes").param("version","v1")).andExpect(status().isOk()).andExpect(content().string(containsString(url)));
        mvc.perform(get(url).param("version","v1")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().string(containsString("1 reportes guardados asociados")));
        var state=analysisDeletion.state(spaces.open(slug,"v1"),a);
        mvc.perform(post(url).with(csrf()).param("version","v1").param("expectedHash",state.hash()).param("confirm","true")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/espacio/"+slug+"/reportes?version=v1"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_general_analysis WHERE id=?",Integer.class,a)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report WHERE id=?",Integer.class,report)).isZero();
        assertThat(reports.get(kept)).isNotEmpty();assertThat(analyses.get(otherAnalysis)).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_run WHERE id=?",Integer.class,p)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE id=?",Integer.class,firstResponse)).isEqualTo(1);
        assertThat(reports.latest(template).id()).isEqualTo(revision);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_management_event WHERE state_json LIKE ?",Integer.class,"%"+a+"%")).isEqualTo(1);
    }
    @Test void analysisDeletionRequiresAdminCsrfConfirmationExactVersionAndCurrentReportList() throws Exception {
        String a=analyses.create(prepare(first),"workspace-admin"),url="/admin/espacio/"+slug+"/reportes/analisis/"+a+"/eliminar";
        String hash=analysisDeletion.state(spaces.open(slug,"v1"),a).hash();
        mvc.perform(get(url).param("version","v1").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get(url).param("version","v1").with(user("reader").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post(url).param("version","v1").param("expectedHash",hash).param("confirm","true")).andExpect(status().isForbidden());
        mvc.perform(post(url).with(csrf()).param("version","v1").param("expectedHash",hash)).andExpect(status().isBadRequest());
        mvc.perform(get(url).param("version","v2")).andExpect(status().isNotFound());
        mvc.perform(post(url).with(csrf()).param("version","v2").param("expectedHash",hash).param("confirm","true")).andExpect(status().isNotFound());
        String template=reports.createTemplate(new ReportService.Template("Reporte nuevo","",List.of("CONTEXTO"),false),"workspace-admin");
        reports.generate(reports.latest(template).id(),a,List.of(),"","workspace-admin");
        mvc.perform(post(url).with(csrf()).param("version","v1").param("expectedHash",hash).param("confirm","true")).andExpect(status().isConflict());
        assertThat(analyses.get(a)).isNotEmpty();
    }
    @Test void savedResultsAndReportsBelongToExactVersionAndContextFollowsTools() throws Exception {
        String p=prepare(first),p2=prepare(second),a=analyses.create(p,"workspace-admin"),a2=analyses.create(p2,"workspace-admin");
        String template=reports.createTemplate(new ReportService.Template("Reporte","",List.of("CONTEXTO","DESCRIPTIVOS"),false),"workspace-admin");
        String report=reports.generate(reports.latest(template).id(),a,List.of(),"","workspace-admin");
        assertThat(spaces.analyses(spaces.open(slug,"v1"))).extracting(SurveyWorkspaceService.Link::path).containsExactly("/admin/analisis-general/"+a);
        assertThat(spaces.reports(spaces.open(slug,"v2"))).isEmpty();
        mvc.perform(get("/admin/espacio/"+slug+"/resultados").param("version","v1")).andExpect(status().isOk())
                .andExpect(content().string(containsString(a))).andExpect(content().string(not(containsString(a2))));
        for(String path:List.of("/admin/preparacion?versionId="+first,"/admin/preparacion/conjuntos/"+p,"/admin/analisis-general/"+a,"/admin/reportes/"+report,"/admin/respuestas/"+firstResponse,"/admin/encuestas/versiones/"+first)) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(containsString("/resultados?version=v1")));
        }
    }
    @Test void versionSelectionDoesNotLeakBetweenTabsOrSurveys() throws Exception {
        var one=mvc.perform(get("/admin/espacio/"+slug+"/respuestas").param("version","v1")).andReturn();
        var two=mvc.perform(get("/admin/espacio/"+slug+"/respuestas").param("version","v2")).andReturn();
        var back=mvc.perform(get("/admin/respuestas/"+firstResponse)).andReturn();
        assertThat(((SurveyWorkspaceService.Workspace)one.getModelAndView().getModel().get("workspace")).selected().id()).isEqualTo(first);
        assertThat(((SurveyWorkspaceService.Workspace)two.getModelAndView().getModel().get("workspace")).selected().id()).isEqualTo(second);
        assertThat(((SurveyWorkspaceService.Workspace)back.getModelAndView().getModel().get("workspace")).selected().id()).isEqualTo(first);
    }
    @Test void creationValidationAndEditLinksKeepNewSurveyContext() throws Exception {
        mvc.perform(post("/admin/espacio").with(csrf()).param("title"," ")).andExpect(status().isUnprocessableEntity());
        String target=mvc.perform(post("/admin/espacio").with(csrf()).param("title","Nueva encuesta"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(target)).andExpect(status().isOk()).andExpect(content().string(containsString("Espacio de la encuesta")));
    }
    @Test void importToolsKeepVersionOnErrorsAndDoNotListOtherVersionBatches() throws Exception {
        byte[] bytes="consentimiento,opcion\nSI,A\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String batch=imports.upload(first,"archivo-primera.csv",bytes,"COMA","GENERICO","workspace-admin");
        imports.upload(second,"archivo-segunda.csv",bytes,"COMA","GENERICO","workspace-admin");
        mvc.perform(get("/admin/espacio/"+slug+"/importar").param("version","v1")).andExpect(status().isOk())
                .andExpect(content().string(containsString("archivo-primera.csv"))).andExpect(content().string(not(containsString("archivo-segunda.csv"))));
        mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andExpect(content().string(containsString("/importar?version=v1")));
        var file=new org.springframework.mock.web.MockMultipartFile("file","antiguo.xls","application/octet-stream",bytes);
        mvc.perform(multipart("/admin/importaciones").file(file).with(csrf()).param("versionId",first).param("delimiter","COMA").param("platform","GENERICO"))
                .andExpect(status().isBadRequest()).andExpect(model().attribute("selectedVersion",first))
                .andExpect(content().string(not(containsString("archivo-segunda.csv")))).andExpect(content().string(containsString("/importar?version=v1")));
    }
    @Test void reportTemplateReturnsToSurveyAndSavesRevisionWithoutChangingData() throws Exception {
        mvc.perform(get("/admin/reportes/plantillas/nueva").param("workspaceVersionId",first)).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"workspaceVersionId\"")));
        mvc.perform(post("/admin/reportes/plantillas").with(csrf()).param("workspaceVersionId",first).param("expectedRevision","0")
                .param("title","Plantilla del espacio").param("section1","CONTEXTO"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(spaces.open(slug,"v1").link("reportes")));
        assertThat(spaces.counts(spaces.open(slug,"v1")).total()).isEqualTo(1);
    }
}

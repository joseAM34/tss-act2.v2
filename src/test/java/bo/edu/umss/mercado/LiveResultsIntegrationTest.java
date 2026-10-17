package bo.edu.umss.mercado;

import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.statistics.*;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.*;
import java.time.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:live-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="live-admin",roles="ADMIN")
class LiveResultsIntegrationTest {
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired GenericPreparationService preparation;
    @Autowired LiveResultsService live;
    @Autowired GenericAnalysisService analyses;
    @Autowired SurveyResponseRepository responses;
    @Autowired SurveyValidator validator;
    @Autowired bo.edu.umss.mercado.importing.CsvImportService imports;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String version,slug,response;
    List<GenericPreparationService.Field> fields() {return List.of(new GenericPreparationService.Field("filtro","NOMINAL",""),new GenericPreparationService.Field("grupo","NOMINAL",""),new GenericPreparationService.Field("correo","EXCLUIR",""),new GenericPreparationService.Field("meses","MAGNITUD_ENTERA","meses"));}
    @BeforeEach void setup() {
        clear();version=drafts.create("Encuesta en vivo <script>bad()</script>","Datos ficticios","live-admin");drafts.section(version,0,null,"Datos","live-admin");
        String section=drafts.get(version).sections().getFirst().id();
        var questions=List.of(new Question("filtro","Datos","Filtro","radio",true,List.of(new Question.Option("SI","Sí"),new Question.Option("NO_SABE","No sabe")),null,null,0,1,"",null,null),
                new Question("grupo","Datos","Grupo","select",true,List.of(new Question.Option("A","Uno"),new Question.Option("B","Dos")),null,null,0,1,"",null,null),
                new Question("correo","Datos","Correo","email",false,List.of(),null,null,0,1,"",null,null),
                new Question("meses","Datos","Meses","number",false,List.of(),null,null,0,1,"",0,120));
        for(int i=0;i<questions.size();i++)drafts.question(version,i+1,section,questions.get(i),true,"live-admin");
        publications.publish(version,5,"live-admin");slug=publications.get(version).slug();
        preparation.configure(version,0,fields(),"live-admin");response=response(version,"SI");
    }
    @AfterEach void clear() {
        for(String t:List.of("xlsx_import_workbook","csv_import_row","csv_import_confirmation","csv_import_mapping","csv_import_batch","report_presentation_template","generated_report_cross","generated_report","report_template_revision","report_template","survey_general_cross","survey_general_analysis","survey_preparation_run","survey_preparation_config"))jdbc.update("DELETE FROM "+t);
        jdbc.update("DELETE FROM survey_answer WHERE response_id IN (SELECT id FROM survey_response WHERE publication_version_id IS NOT NULL)");
        jdbc.update("DELETE FROM survey_question_state WHERE response_id IN (SELECT id FROM survey_response WHERE publication_version_id IS NOT NULL)");
        jdbc.update("DELETE FROM survey_response WHERE publication_version_id IS NOT NULL");
        jdbc.update("UPDATE survey SET draft_version_id=NULL, active_version_id=NULL");
        for(String t:List.of("survey_definition_event","survey_question_option","survey_question","survey_section","survey_version","survey"))jdbc.update("DELETE FROM "+t);
        jdbc.update("DELETE FROM instrument_snapshot WHERE survey_slug LIKE 'encuesta-%'");
    }
    String response(String id,String option) {var data=new LinkedMultiValueMap<String,String>();data.set("consent","SI");data.set("filtro",option);data.set("grupo","A");data.set("correo","privado@example.test");data.set("meses","0");return responses.savePublished(id,UUID.randomUUID().toString(),validator.validate(publications.get(id).content().definition(),data));}
    LiveResultsService.Selection selection() {return new LiveResultsService.Selection("NO_DECLARADO",null,null,"","");}
    LiveResultsService.Frame current() {return live.current(version,selection(),"live-admin");}
    String url() {return "/admin/espacio/"+slug+"/resultados";}
    @Test void shownCutCanBecomeDirectReportWithItsCrossWithoutTemplates() throws Exception {
        var selection=new LiveResultsService.Selection("NO_DECLARADO",null,null,"filtro","grupo");
        var frame=live.current(version,selection,"live-admin");response(version,"NO_SABE");
        var savedResponse=mvc.perform(post(url()+"/guardar").with(csrf()).param("version","v1")
                .param("origin","NO_DECLARADO").param("row","filtro").param("column","grupo")
                .param("expectedHash",frame.hash()).param("expectedCutoff",frame.cutoff()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var saved=CanonicalJson.read(savedResponse,LiveResultsService.Saved.class);
        String form=mvc.perform(get("/admin/reportes/nuevo").param("analysisId",saved.analysisId()).param("crossId",saved.crossId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("sin plantilla","Corte congelado",frame.cutoff(),saved.crossId(),"report-customizer.js");
        var generated=mvc.perform(post("/admin/reportes").with(csrf()).param("analysisId",saved.analysisId()).param("crossIds",saved.crossId()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String json=mvc.perform(get(generated.getResponse().getRedirectedUrl()+"/contenido.json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var report=CanonicalJson.read(json,Map.class);var content=(Map<?,?>)report.get("content");
        assertThat(((Map<?,?>)content.get("context")).get("cutoff_utc")).isEqualTo(frame.cutoff());
        var analysis=(Map<?,?>)content.get("analysis");
        assertThat(((Map<?,?>)analysis.get("result")).get("submission_count")).isEqualTo(1);
        assertThat((List<?>)content.get("crosses")).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_template",Integer.class)).isZero();
    }
    @Test void customizedReportJourneyReusesTemplateOnNewCutAndKeepsEveryExportFrozen() throws Exception {
        var selected=new LiveResultsService.Selection("NO_DECLARADO",null,null,"filtro","grupo");
        var frame=live.current(version,selected,"live-admin");
        var saved=CanonicalJson.read(mvc.perform(post(url()+"/guardar").with(csrf()).param("version","v1").param("row","filtro").param("column","grupo")
                .param("expectedHash",frame.hash()).param("expectedCutoff",frame.cutoff())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),LiveResultsService.Saved.class);
        mvc.perform(get("/admin/reportes/nuevo").param("analysisId",saved.analysisId()).param("crossId",saved.crossId())).andExpect(status().isOk());
        var defaults=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(saved.analysisId()),List.of(saved.crossId()));
        var config=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(defaults.analysisId(),"Informe <script>ejemplo</script>","Introducción de ejemplo",true,true,true,"Interpretación de este corte",
                defaults.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),true,q.title(),"Nota <img src=x>",q.id().equals("meses")?List.of("table","columns"):List.of("table","columns","bars","pie","donut"))).toList(),
                defaults.crossIds(),List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(saved.crossId(),"Comparación propia","Nota de cruce",List.of("table","heat","grouped","stacked"))));
        String previewPath="/admin/reportes/personalizar/vista-previa",generatePath="/admin/reportes/personalizar/generar",applyPath="/admin/reportes/personalizar/plantillas/aplicar";
        var preview=CanonicalJson.read(mvc.perform(post(previewPath).with(csrf()).contentType("application/json").content(CanonicalJson.json(config)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString(),Map.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_presentation_template",Integer.class)).isZero();
        var request=Map.of("configuration",config,"expectedHash",preview.get("hash"),"saveTemplate",true,"templateName","Plantilla <b>ejemplo</b>");
        mvc.perform(post(generatePath).contentType("application/json").content(CanonicalJson.json(request))).andExpect(status().isForbidden());
        mvc.perform(post(generatePath).with(csrf()).with(user("lector").roles("USER")).contentType("application/json").content(CanonicalJson.json(request))).andExpect(status().isForbidden());
        var generated=CanonicalJson.read(mvc.perform(post(generatePath).with(csrf()).contentType("application/json").content(CanonicalJson.json(request))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),Map.class);
        String report=(String)generated.get("url");
        var html=mvc.perform(get(report)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("Informe &lt;script&gt;","Nota &lt;img","Comparación propia","Guardar como PDF").doesNotContain("<script>ejemplo</script>","privado@example.test");
        var frozen=new LinkedHashMap<String,byte[]>();
        for(String suffix:List.of("/contenido.json","/resultados.csv","/resultados.xlsx")){
            frozen.put(suffix,mvc.perform(get(report+suffix)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray());
            mvc.perform(get(report+suffix).with(anonymous())).andExpect(status().is3xxRedirection());
            mvc.perform(get(report+suffix).with(user("lector").roles("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(get(report+"/nuevo")).andExpect(status().isOk()).andExpect(content().string(containsString("Comparación propia")));
        String template=jdbc.queryForObject("SELECT id FROM report_presentation_template",String.class);
        response(version,"NO_SABE");var nextFrame=live.current(version,selected,"live-admin");
        var next=live.save(version,selected,nextFrame.hash(),nextFrame.cutoff(),"live-admin");
        String applyBody=CanonicalJson.json(Map.of("templateId",template,"analysisId",next.analysisId()));
        mvc.perform(post(applyPath).with(csrf()).with(user("lector").roles("USER")).contentType("application/json").content(applyBody)).andExpect(status().isForbidden());
        var applied=CanonicalJson.read(mvc.perform(post(applyPath).with(csrf()).contentType("application/json").content(applyBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),Map.class);
        var nextConfig=CanonicalJson.read(CanonicalJson.json(applied.get("configuration")),bo.edu.umss.mercado.reporting.ReportCustomization.Configuration.class);
        assertThat(nextConfig.comment()).isEmpty();assertThat(nextConfig.questions()).allMatch(q->q.comment().isEmpty());assertThat(nextConfig.crossIds()).doesNotContain(saved.crossId());
        assertThat(nextConfig.questions().getFirst().representations()).contains("pie","donut","bars");
        var nextPreview=CanonicalJson.read(mvc.perform(post(previewPath).with(csrf()).contentType("application/json").content(CanonicalJson.json(nextConfig))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),Map.class);
        var nextReport=CanonicalJson.read(mvc.perform(post(generatePath).with(csrf()).contentType("application/json").content(CanonicalJson.json(Map.of("configuration",nextConfig,"expectedHash",nextPreview.get("hash"),"saveTemplate",false))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),Map.class);
        var nextJson=CanonicalJson.read(mvc.perform(get(nextReport.get("url")+"/contenido.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),Map.class);
        assertThat(((Map<?,?>)((Map<?,?>)((Map<?,?>)nextJson.get("content")).get("analysis")).get("result")).get("submission_count")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_presentation_template",Integer.class)).isEqualTo(1);
        for(var entry:frozen.entrySet())mvc.perform(get(report+entry.getKey())).andExpect(status().isOk()).andExpect(content().bytes(entry.getValue()));
    }
    @Test void repeatedConsultationsDoNotPersistAndNewResponseUpdatesResult() {
        var first=current();assertThat(current().hash()).isEqualTo(first.hash());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_run",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_general_analysis",Integer.class)).isZero();
        response(version,"NO_SABE");var next=current();assertThat(next.hash()).isNotEqualTo(first.hash());assertThat(next.result().get("submission_count")).isEqualTo(2);
        assertThat(CanonicalJson.json(next)).doesNotContain("privado@example.test","response_id");
    }
    @Test void originsStaySeparateAndZeroIsNotMissing() {
        var real=new LiveResultsService.Selection("REAL",null,null,"","");
        assertThat(live.current(version,real,"live-admin").result().get("submission_count")).isEqualTo(0);
        jdbc.update("INSERT INTO wrangling_origin_classification(id,response_id,origin,reason,actor,created_at) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),response,"REAL","Revisión ficticia","live-admin",OffsetDateTime.now(ZoneOffset.UTC));
        assertThat(current().result().get("submission_count")).isEqualTo(0);
        var frame=live.current(version,real,"live-admin");assertThat(frame.result().get("submission_count")).isEqualTo(1);
        assertThat(CanonicalJson.json(frame.result())).contains("\"min\":0.0");
    }
    @Test void dateFiltersAreInclusiveInLaPaz() {
        LocalDate today=LocalDate.now(ZoneId.of("America/La_Paz"));
        assertThat(live.current(version,new LiveResultsService.Selection("NO_DECLARADO",today,today,"",""),"live-admin").result().get("submission_count")).isEqualTo(1);
        assertThat(live.current(version,new LiveResultsService.Selection("NO_DECLARADO",null,today.minusDays(1),"",""),"live-admin").result().get("submission_count")).isEqualTo(0);
    }
    @Test void savedCutoffIsExactEvenAfterNewReceiptsAndRepeatedSaveIsIdempotent() {
        var first=current();response(version,"NO_SABE");
        var saved=live.save(version,selection(),first.hash(),first.cutoff(),"live-admin");
        var again=live.save(version,selection(),first.hash(),first.cutoff(),"live-admin");assertThat(again).isEqualTo(saved);
        assertThat(preparation.bundle(saved.preparationId()).get("cutoff_utc")).isEqualTo(first.cutoff());
        assertThat(((Map<?,?>)analyses.get(saved.analysisId()).get("result")).get("submission_count")).isEqualTo(1);
        assertThat(current().result().get("submission_count")).isEqualTo(2);
    }
    @Test void changedConfigurationCannotSilentlyReplaceShownCut() {
        var frame=current();preparation.configure(version,1,fields(),"live-admin");
        assertThatThrownBy(()->live.save(version,selection(),frame.hash(),frame.cutoff(),"live-admin")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_run",Integer.class)).isZero();
    }
    @Test void crossingRequiresExplicitPairAndDoesNotAssumeIndependence() {
        var pair=new LiveResultsService.Selection("NO_DECLARADO",null,null,"filtro","grupo");var frame=live.current(version,pair,"live-admin");
        assertThat(frame.cross()).isNotNull();assertThat(((Map<?,?>)frame.cross().get("table")).get("p")).isNull();
        var saved=live.save(version,pair,frame.hash(),frame.cutoff(),"live-admin");assertThat(saved.crossId()).isNotBlank();
        assertThatThrownBy(()->live.current(version,new LiveResultsService.Selection("NO_DECLARADO",null,null,"correo","grupo"),"live-admin")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void endpointChecksAdminCsrfVersionAndInput() throws Exception {
        mvc.perform(get(url()+"/actuales").param("version","v1").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get(url()+"/actuales").param("version","v1").with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post(url()+"/guardar").param("version","v1")).andExpect(status().isForbidden());
        mvc.perform(get(url()+"/actuales").param("version","inexistente")).andExpect(status().isNotFound());
        mvc.perform(get(url()+"/actuales").param("version","v1").param("from","ayer")).andExpect(status().isBadRequest());
        mvc.perform(get(url()+"/actuales").param("version","v1").param("origin","TODOS")).andExpect(status().isBadRequest());
    }
    @Test void pendingConfigurationDoesNotApproveVariablesOrWriteAnything() throws Exception {
        String successor=publications.successor(version,"live-admin");publications.publish(successor,0,"live-admin");
        mvc.perform(get(url()+"/actuales").param("version","v2")).andExpect(status().isUnprocessableEntity());
        assertThat(preparation.configuration(successor).revision()).isZero();
    }
    @Test void htmlAndJsonUseSameFrameAndEscapeLabels() throws Exception {
        var page=mvc.perform(get(url()).param("version","v1")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>bad()</script>")))).andReturn();
        var json=mvc.perform(get(url()+"/actuales").param("version","v1")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        if(Boolean.getBoolean("live.capture")) {var dir=Path.of(".codex-tmp/live-browser");Files.createDirectories(dir);Files.writeString(dir.resolve("live.html"),page.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));Files.writeString(dir.resolve("frame.json"),json.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
        assertThat(json.getResponse().getContentAsString()).contains("sourceHash","resultHash","html").doesNotContain("privado@example.test");
        if(Boolean.getBoolean("live.capture")) {
            var dir=Path.of(".codex-tmp/live-browser");response(version,"NO_SABE");
            Files.writeString(dir.resolve("frame2.json"),mvc.perform(get(url()+"/actuales").param("version","v1")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            Files.writeString(dir.resolve("cross.html"),mvc.perform(get(url()).param("version","v1").param("row","filtro").param("column","grupo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            Files.writeString(dir.resolve("empty.html"),mvc.perform(get(url()).param("version","v1").param("origin","REAL")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
            String next=publications.successor(version,"live-admin");publications.publish(next,0,"live-admin");
            Files.writeString(dir.resolve("pending.html"),mvc.perform(get(url()).param("version","v2")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    @Test void corruptedCaptureFailsClosedInsteadOfReplacingOldResult() throws Exception {
        jdbc.update("UPDATE response_capture SET capture_hash=? WHERE response_id=?","0".repeat(64),response);
        mvc.perform(get(url()+"/actuales").param("version","v1")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").exists());
    }
    @Test void closedVersionStillUpdatesItsSelectionWithoutAcceptingNewSubmissions() {
        publications.close(version,publications.get(version).revision(),"live-admin");assertThat(current().result().get("submission_count")).isEqualTo(1);
        assertThatThrownBy(()->response(version,"SI")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void completeHttpJourneyKeepsVersionAndCutFromCreationThroughPublicReceptionImportAndExport() throws Exception {
        String editor=mvc.perform(post("/admin/espacio").with(csrf()).param("title","Recorrido ficticio"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String id=editor.substring(editor.lastIndexOf('/')+1);
        mvc.perform(post(editor+"/secciones").with(csrf()).param("revision","0").param("title","Datos")).andExpect(status().is3xxRedirection());
        String section=drafts.get(id).sections().getFirst().id();
        mvc.perform(post(editor+"/preguntas").with(csrf()).param("revision","1").param("sectionId",section).param("questionId","opcion")
                .param("label","Opción").param("type","radio").param("required","true").param("options","A | Uno\nB | Dos"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get(editor+"/vista-previa")).andExpect(status().isOk());
        mvc.perform(post(editor+"/publicar").with(csrf()).param("revision","2")).andExpect(status().is3xxRedirection());
        String surveySlug=publications.get(id).slug(),space="/admin/espacio/"+surveySlug,publicPath="/encuestas/"+surveySlug+"/versiones/v1";
        var session=new org.springframework.mock.web.MockHttpSession();
        var form=mvc.perform(get(publicPath).session(session).with(anonymous())).andExpect(status().isOk()).andReturn();
        String token=form.getModelAndView().getModel().get("formToken").toString();
        for(int retry=0;retry<2;retry++) mvc.perform(post(publicPath).session(session).with(anonymous()).with(csrf()).param("formToken",token)
                .param("consent","SI").param("opcion","A")).andExpect(redirectedUrl("/gracias"));
        mvc.perform(post("/admin/preparacion/"+id+"/configuracion").with(csrf()).param("revision","0")
                .param("treatment.opcion","NOMINAL").param("unit.opcion","")).andExpect(status().is3xxRedirection());
        var file=new org.springframework.mock.web.MockMultipartFile("file","respuestas.csv","text/csv","consent,opcion\nSI,B\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String batchPath=mvc.perform(multipart("/admin/importaciones").file(file).with(csrf()).param("versionId",id))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        String batch=batchPath.substring(batchPath.lastIndexOf('/')+1);
        mvc.perform(get(batchPath)).andExpect(status().isOk()).andExpect(content().string(containsString("/resultados?version=v1")));
        mvc.perform(post(batchPath+"/correspondencias").with(csrf()).param("expectedRevision","0").param("column.opcion","1")
                .param("reviewMode","CHECKBOX_V1").param("compatible","true").param("consentReviewed","true")
                .param("consentColumn","0").param("consentValue","SI").param("dateColumn","-1").param("externalIdColumn","-1")
                .param("optionMode","AUTO").param("multipleSeparator","AUTO")).andExpect(status().is3xxRedirection());
        String rev=imports.revisions(batch).getFirst().id();var preview=imports.preview(batch,rev);assertThat(preview.valid()).isTrue();
        for(int retry=0;retry<2;retry++) mvc.perform(post(batchPath+"/confirmar").with(csrf()).param("revisionId",rev).param("previewHash",preview.hash())).andExpect(status().is3xxRedirection());
        String currentPath=space+"/resultados/actuales";
        String frameJson=mvc.perform(get(currentPath).param("version","v1")).andExpect(status().isOk()).andExpect(jsonPath("$.frame.result.submission_count").value(2))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        Map<?,?> frame=(Map<?,?>)CanonicalJson.read(frameJson,Map.class).get("frame");
        String savedJson=mvc.perform(post(space+"/resultados/guardar").with(csrf()).param("version","v1").param("expectedHash",frame.get("hash").toString())
                .param("expectedCutoff",frame.get("cutoff").toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String analysis=CanonicalJson.read(savedJson,Map.class).get("analysisId").toString();
        mvc.perform(post("/admin/reportes/plantillas").with(csrf()).param("expectedRevision","0").param("title","Reporte del recorrido")
                .param("charts","true").param("section1","CONTEXTO").param("section2","DESCRIPTIVOS")).andExpect(status().is3xxRedirection());
        String template=jdbc.queryForObject("SELECT id FROM report_template_revision",String.class);
        String report=mvc.perform(post("/admin/reportes").with(csrf()).param("templateRevisionId",template).param("analysisId",analysis))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(report)).andExpect(status().isOk()).andExpect(content().string(containsString("/resultados?version=v1")))
                .andExpect(content().string(containsString("Guardar como PDF")));
        byte[] frozen=mvc.perform(get(report+"/resultados.csv")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(get(report+"/resultados.xlsx")).andExpect(status().isOk());
        String next=publications.successor(id,"live-admin");publications.publish(next,0,"live-admin");publications.activate(next,publications.get(next).revision(),"live-admin");
        mvc.perform(get(space+"/respuestas").param("version","v1")).andExpect(status().isOk()).andExpect(content().string(containsString("/resultados?version=v1")));
        mvc.perform(get(currentPath).param("version","v1")).andExpect(status().isOk()).andExpect(jsonPath("$.frame.result.submission_count").value(2));
        mvc.perform(get(report+"/resultados.csv")).andExpect(status().isOk()).andExpect(content().bytes(frozen));
        mvc.perform(get(report+"/resultados.xlsx").with(anonymous())).andExpect(status().is3xxRedirection());
    }
    @Test void concurrentSaveOfOneCutCreatesOnlyOneAnalysis() throws Exception {
        var frame=current();var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try{var tasks=List.<java.util.concurrent.Callable<LiveResultsService.Saved>>of(()->live.save(version,selection(),frame.hash(),frame.cutoff(),"live-admin"),()->live.save(version,selection(),frame.hash(),frame.cutoff(),"live-admin"));
            var results=pool.invokeAll(tasks);assertThat(results.get(0).get()).isEqualTo(results.get(1).get());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_general_analysis",Integer.class)).isEqualTo(1);
        }finally{pool.shutdownNow();}
    }
}

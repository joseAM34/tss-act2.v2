package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.importing.*;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.statistics.*;
import bo.edu.umss.mercado.reporting.ReportService;
import bo.edu.umss.mercado.wrangling.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.zip.*;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:survey-deletion-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="deletion-admin",roles="ADMIN") @Transactional
class SurveyDeletionIntegrationTest {
    static final String ACTOR="deletion-admin";
    @Autowired SurveyDeletionService deletion;
    @Autowired SurveyManagementService management;
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired SurveyResponseRepository responses;
    @Autowired SurveyValidator validator;
    @Autowired CsvImportService imports;
    @Autowired XlsxImportService excel;
    @Autowired GenericPreparationService preparations;
    @Autowired GenericAnalysisService analyses;
    @Autowired GenericCrossService crosses;
    @Autowired ReportService reports;
    @Autowired WranglingService wrangling;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    record Fixture(String version,String slug,String response,String batch,String book,String preparation,String analysis,String cross,String report,String template,byte[] csv,byte[] xlsx) {}
    String publish() {
        String id=drafts.create("Encuesta ficticia para respaldo","No contiene datos de campo",ACTOR);
        drafts.section(id,0,null,"Datos",ACTOR);String section=drafts.get(id).sections().getFirst().id();
        for(int i=0;i<2;i++)drafts.question(id,i+1,section,new Question("q"+(i+1),"Datos","Pregunta "+(i+1),"radio",false,
                List.of(new Question.Option("SI","Sí"),new Question.Option("NO","No")),null,null,0,1,"",null,null),true,ACTOR);
        drafts.question(id,3,section,new Question("texto","Datos","Texto","textarea",false,List.of(),null,null,0,1,"",null,null),true,ACTOR);
        publications.publish(id,4,ACTOR);return id;
    }
    String response(String version) {
        var fields=new LinkedMultiValueMap<String,String>();fields.set("consent","SI");fields.set("q1","SI");fields.set("q2","NO");fields.set("texto","Texto ficticio original");
        return responses.savePublished(version,UUID.randomUUID().toString(),validator.validate(publications.get(version).content().definition(),fields));
    }
    CsvImportService.Mapping mapping() {
        return new CsvImportService.Mapping(List.of(new CsvImportService.Column("q1",0),new CsvImportService.Column("q2",1),new CsvImportService.Column("texto",2)),
                3,"SI",-1,-1,"AUTO","AUTO",true,"Revisión ficticia del instrumento","Revisión ficticia de la aceptación");
    }
    Fixture fixture() {
        String v=publish(),slug=publications.get(v).slug(),response=response(v);
        byte[] csv="q1;q2;texto;consentimiento\nNO;SI;=2+3;SI\n".getBytes(StandardCharsets.UTF_8);
        String batch=imports.upload(v,"fuente-ficticia.csv",csv,"PUNTO_COMA","GENERICO",ACTOR);
        String revision=imports.mapColumns(batch,0,mapping(),ACTOR);var preview=imports.preview(batch,revision);
        assertThat(preview.valid()).isTrue();imports.confirm(batch,revision,preview.hash(),false,ACTOR);
        byte[] xlsx=XlsxFileTest.workbook(XlsxFileTest.sheet(List.of(List.of("q1","q2","texto","consentimiento"),List.of("SI","SI","Original ficticio Excel","SI"))));
        String book=excel.upload(v,"fuente-ficticia.xlsx",xlsx,"MICROSOFT_FORMS",ACTOR);excel.select(book,1,ACTOR);
        String config=preparations.configure(v,0,List.of(new GenericPreparationService.Field("q1","NOMINAL",""),new GenericPreparationService.Field("q2","NOMINAL",""),new GenericPreparationService.Field("texto","EXCLUIR","")),ACTOR);
        String prepared=preparations.prepare(v,config,"NO_DECLARADO","BORRADOR",null,null,ACTOR);
        String analysis=analyses.create(prepared,ACTOR),cross=crosses.create(prepared,new GenericCrossService.Spec("q1","q2",.05,false,"","",false),ACTOR);
        String template=reports.createTemplate(new ReportService.Template("Plantilla compartida ficticia","",ReportService.SECTIONS,true),ACTOR);
        String report=reports.generate(reports.latest(template).id(),analysis,List.of(cross),"Comentario ficticio",ACTOR);
        return new Fixture(v,slug,response,batch,book,prepared,analysis,cross,report,template,csv,xlsx);
    }
    SurveyDeletionService.Backup backup(String slug) {return deletion.prepare(slug,management.get(slug).hash(),ACTOR);}
    Map<String,byte[]> unzip(byte[] bytes)throws IOException {
        var files=new LinkedHashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)) {
            for(ZipEntry entry;(entry=zip.getNextEntry())!=null;)files.put(entry.getName(),zip.readAllBytes());
        }return files;
    }
    long count(String table,String column,String id) {return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE "+column+"=?",Long.class,id);}

    @Test void completeArchivePreservesOriginalsStatesRevisionsAndProductsWithoutDeletingAnything() throws Exception {
        var f=fixture();wrangling.classify(f.response(),"NO_DECLARADO","Revisión de prueba",ACTOR);
        var backup=backup(f.slug());var files=unzip(backup.bytes());
        assertThat(files).containsKeys("manifest.json","datos.json","respuestas.csv");
        assertThat(files.values()).anySatisfy(value->assertThat(value).isEqualTo(f.csv())).anySatisfy(value->assertThat(value).isEqualTo(f.xlsx()));
        var data=CanonicalJson.read(new String(files.get("datos.json"),StandardCharsets.UTF_8),Map.class);
        var manifest=CanonicalJson.read(new String(files.get("manifest.json"),StandardCharsets.UTF_8),Map.class);
        assertThat(manifest.get("content_hash")).isEqualTo(CanonicalJson.hash(data));
        String raw=new String(files.get("datos.json"),StandardCharsets.UTF_8);
        assertThat(raw).contains("=2+3","CON_VALOR","response_capture","wrangling_origin_classification",f.preparation(),f.cross(),f.report());
        assertThat(new String(files.get("respuestas.csv"),StandardCharsets.UTF_8)).contains("'=2+3","Texto ficticio original");
        assertThat(count("survey","slug",f.slug())).isEqualTo(1);assertThat(count("survey_response","dynamic_survey_id",publications.get(f.version()).surveyId())).isEqualTo(2);
        assertThat(deletion.latest(f.slug(),ACTOR,management.get(f.slug()).hash())).contains(backup.receipt());
        assertThat(deletion.latest(f.slug(),"different-admin",management.get(f.slug()).hash())).isEmpty();
    }
    @Test void deletesEveryOwnedDependencyButPreservesOtherSurveysSharedTemplatesAndFrozenHistory() throws Exception {
        var f=fixture();String other=publish(),otherResponse=response(other),otherHash=publications.get(other).hash();
        String shared=wrangling.create("NO_DECLARADO","BORRADOR",null,ACTOR),sharedBundle=wrangling.bundle(shared);
        var backup=backup(f.slug());deletion.delete(f.slug(),backup.receipt().id(),backup.receipt().stateHash(),ACTOR);
        assertThat(count("survey","slug",f.slug())).isZero();assertThat(count("survey_version","id",f.version())).isZero();
        for(var entry:Map.of("survey_response",f.response(),"csv_import_batch",f.batch(),"xlsx_import_workbook",f.book(),"survey_preparation_run",f.preparation(),"survey_general_analysis",f.analysis(),"survey_general_cross",f.cross(),"generated_report",f.report()).entrySet())
            assertThat(count(entry.getKey(),"id",entry.getValue())).as(entry.getKey()).isZero();
        assertThat(count("survey_response","id",otherResponse)).isEqualTo(1);assertThat(publications.get(other).hash()).isEqualTo(otherHash);
        assertThat(count("report_template","id",f.template())).isEqualTo(1);assertThat(reports.latest(f.template())).isNotNull();
        assertThat(wrangling.bundle(shared)).isEqualTo(sharedBundle);assertThat(count("wrangling_member","run_id",shared)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT consumed_at FROM survey_deletion_backup WHERE id=?",OffsetDateTime.class,backup.receipt().id())).isNotNull();
        assertThat(jdbc.queryForObject("SELECT state_json FROM survey_management_event WHERE survey_slug=? AND operation='ELIMINAR'",String.class,f.slug()))
                .contains(backup.hash()).doesNotContain("Texto ficticio original");
    }
    @Test void changedDataWithIdenticalManagementCountsRequiresAnotherArchive() {
        String v=publish(),slug=publications.get(v).slug(),response=response(v);var backup=backup(slug);
        wrangling.classify(response,"SINTETICO","Cambio posterior ficticio",ACTOR);
        assertThat(management.get(slug).hash()).isEqualTo(backup.receipt().stateHash());
        assertThatThrownBy(()->deletion.delete(slug,backup.receipt().id(),backup.receipt().stateHash(),ACTOR)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("datos cambiaron");
        assertThat(count("survey_response","id",response)).isEqualTo(1);assertThat(count("survey_management_event","survey_slug",slug)).isZero();
    }
    @Test void newImportMappingInvalidatesTheArchiveEvenThoughBatchCountsStayUnchanged() {
        var f=fixture();var backup=backup(f.slug());imports.mapColumns(f.batch(),1,mapping(),ACTOR);
        assertThat(management.get(f.slug()).hash()).isEqualTo(backup.receipt().stateHash());
        assertThatThrownBy(()->deletion.delete(f.slug(),backup.receipt().id(),backup.receipt().stateHash(),ACTOR)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("datos cambiaron");
        assertThat(imports.revisions(f.batch())).hasSize(2);assertThat(count("generated_report","id",f.report())).isEqualTo(1);
    }
    @Test void refusesForgedExpiredOtherActorsAndOtherSurveyReceiptsWithoutLoss() {
        String v=publish(),slug=publications.get(v).slug();response(v);var backup=backup(slug);
        assertThatThrownBy(()->deletion.delete(slug,"nonexistent",backup.receipt().stateHash(),ACTOR)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(()->deletion.delete(slug,backup.receipt().id(),backup.receipt().stateHash(),"another-admin")).isInstanceOf(ResponseStatusException.class);
        String other=publish(),otherSlug=publications.get(other).slug();
        assertThatThrownBy(()->deletion.delete(otherSlug,backup.receipt().id(),management.get(otherSlug).hash(),ACTOR)).isInstanceOf(ResponseStatusException.class);
        jdbc.update("UPDATE survey_deletion_backup SET created_at=? WHERE id=?",OffsetDateTime.now(ZoneOffset.UTC).minusHours(2),backup.receipt().id());
        assertThat(deletion.latest(slug,ACTOR,backup.receipt().stateHash())).isEmpty();
        assertThatThrownBy(()->deletion.delete(slug,backup.receipt().id(),backup.receipt().stateHash(),ACTOR)).isInstanceOf(ResponseStatusException.class);
        assertThat(count("survey","slug",slug)).isEqualTo(1);assertThat(count("survey","slug",otherSlug)).isEqualTo(1);
    }
    @Test void changedStructureAndCorruptOriginalsCannotBeBackedUpOrDeleted() {
        var f=fixture();String hash=management.get(f.slug()).hash();publications.successor(f.version(),ACTOR);
        assertThatThrownBy(()->deletion.prepare(f.slug(),hash,ACTOR)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("cambiaron");
        jdbc.update("UPDATE csv_import_batch SET source_base64=? WHERE id=?",Base64.getEncoder().encodeToString("tampered".getBytes(StandardCharsets.UTF_8)),f.batch());
        assertThatThrownBy(()->backup(f.slug())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("original");
        assertThat(count("survey_deletion_backup","slug",f.slug())).isZero();assertThat(count("survey","slug",f.slug())).isEqualTo(1);
    }
    @Test void crossOwnerReferencesRejectTheBackupWithoutChangingEitherSurvey() {
        var f=fixture();String v=publish();response(v);
        String config=preparations.configure(v,0,List.of(new GenericPreparationService.Field("q1","NOMINAL",""),new GenericPreparationService.Field("q2","NOMINAL",""),new GenericPreparationService.Field("texto","EXCLUIR","")),ACTOR);
        String run=preparations.prepare(v,config,"NO_DECLARADO","BORRADOR",null,null,ACTOR);
        String foreign=crosses.create(run,new GenericCrossService.Spec("q1","q2",.05,false,"","",false),ACTOR);
        jdbc.update("UPDATE generated_report_cross SET cross_id=? WHERE report_id=?",foreign,f.report());
        assertThatThrownBy(()->backup(f.slug())).isInstanceOf(ResponseStatusException.class).hasMessageContaining("referencias");
        assertThat(count("survey_general_cross","id",foreign)).isEqualTo(1);assertThat(count("generated_report","id",f.report())).isEqualTo(1);
    }
    @Test void httpDownloadAndExplicitConfirmationDeleteOnlyAfterTheZipIsPrepared() throws Exception {
        String version=publish(),slug=publications.get(version).slug();response(version);String path="/admin/espacio/"+slug+"/gestion",hash=management.get(slug).hash();
        var initial=mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(containsString("Descargar respaldo ZIP")))
                .andExpect(content().string(containsString("data-delete-with-backup hidden"))).andReturn();
        var result=mvc.perform(post(path+"/respaldo.zip").with(csrf()).param("expectedHash",hash)).andExpect(status().isOk())
                .andExpect(content().contentType("application/zip")).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().string("Content-Disposition",containsString("attachment"))).andReturn();
        String backupId=result.getResponse().getHeader("X-Survey-Backup-Id");assertThat(unzip(result.getResponse().getContentAsByteArray())).containsKey("datos.json");
        var fixture=java.nio.file.Path.of(".codex-tmp/survey-deletion-browser");java.nio.file.Files.createDirectories(fixture);
        java.nio.file.Files.writeString(fixture.resolve("before.html"),initial.getResponse().getContentAsString(StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(fixture.resolve("ready.html"),mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        java.nio.file.Files.write(fixture.resolve("download.zip"),result.getResponse().getContentAsByteArray());
        java.nio.file.Files.writeString(fixture.resolve("download.json"),CanonicalJson.json(Map.of("id",backupId,"filename","respaldo-prueba.zip")));
        mvc.perform(post(path+"/eliminar-con-respaldo").with(csrf()).param("expectedHash",hash).param("backupId",backupId)).andExpect(status().isBadRequest());
        assertThat(count("survey","slug",slug)).isEqualTo(1);
        mvc.perform(post(path+"/eliminar-con-respaldo").with(csrf()).param("expectedHash",hash).param("backupId",backupId).param("confirm","true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/espacio"));
        assertThat(count("survey","slug",slug)).isZero();
    }
    @Test void adminAndCsrfAreRequiredForBothBackupAndDeletion() throws Exception {
        String v=publish(),slug=publications.get(v).slug();response(v);String path="/admin/espacio/"+slug+"/gestion",hash=management.get(slug).hash();
        for(String endpoint:List.of("respaldo.zip","eliminar-con-respaldo")) {
            mvc.perform(post(path+"/"+endpoint).param("expectedHash",hash).param("backupId","fake").param("confirm","true")).andExpect(status().isForbidden());
            mvc.perform(post(path+"/"+endpoint).with(csrf()).with(user("viewer").roles("USER")).param("expectedHash",hash).param("backupId","fake").param("confirm","true")).andExpect(status().isForbidden());
            mvc.perform(post(path+"/"+endpoint).with(csrf()).with(anonymous()).param("expectedHash",hash).param("backupId","fake").param("confirm","true")).andExpect(status().is3xxRedirection());
        }
        assertThat(count("survey_deletion_backup","slug",slug)).isZero();assertThat(count("survey","slug",slug)).isEqualTo(1);
    }
}

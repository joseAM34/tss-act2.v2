package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.*;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:historical-import-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="historical-admin",roles="ADMIN") @Transactional
class HistoricalImportIntegrationTest {
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired CsvImportService imports;
    @Autowired CaptureStore captures;
    @Autowired SurveyValidator validator;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired bo.edu.umss.mercado.statistics.WorkspaceResultsService workspaceResults;
    String version,batch;
    @BeforeEach void setup() {
        version=drafts.create("Recepción histórica ficticia","Fixture sintético, sin datos de campo","historical-admin");
        drafts.section(version,0,null,"Datos","historical-admin"); String section=drafts.get(version).sections().getFirst().id();
        var questions=List.of(
                new Question("tipo","Datos","Tipo","radio",true,List.of(new Question.Option("SI","Sí"),new Question.Option("NO_SABE","No sabe")),null,null,0,1,"",null,null),
                new Question("opciones","Datos","Selecciones","checkbox",false,List.of(new Question.Option("A","Rojo, oscuro"),new Question.Option("B","Azul")),null,null,1,2,"",null,null),
                new Question("texto","Datos","Texto","textarea",false,List.of(),null,null,0,1,"",null,null));
        for(int i=0;i<questions.size();i++)drafts.question(version,i+1,section,questions.get(i),true,"historical-admin");
        publications.publish(version,4,"historical-admin");
        batch=imports.upload(version,"historico-ficticio.csv","Tipo;Selecciones;Texto\nNo sabe;\"[\"\"Rojo, oscuro\"\",\"\"Azul\"\"]\"; texto original \n".getBytes(StandardCharsets.UTF_8),"PUNTO_COMA","MICROSOFT_FORMS","historical-admin");
    }
    CsvImportService.Mapping mapping(String id) {
        var proposed=ImportSuggestions.propose(publications.get(version).content().definition(),imports.batch(id).table()).mapping();
        return new CsvImportService.Mapping(proposed.columns(),-2,CsvImportService.HISTORICAL_VALUE,-1,-1,"AUTO","JSON",true,"Instrumento revisado",CsvImportService.HISTORICAL_ACK);
    }
    String revision() {return imports.mapColumns(batch,0,mapping(batch),"historical-admin");}
    @Test void groupedResultsVerifyImportedSourceAgainAfterEveryLoad() {
        confirm();
        var first=workspaceResults.load(publications.get(version).slug(),List.of(version),"","","historical-admin");
        assertThat(first.result().get("submission_count")).isEqualTo(1);
        jdbc.update("UPDATE csv_import_batch SET source_base64=? WHERE id=?",Base64.getEncoder().encodeToString("alterado".getBytes(StandardCharsets.UTF_8)),batch);
        assertThatThrownBy(()->workspaceResults.load(publications.get(version).slug(),List.of(version),"","","historical-admin")).isInstanceOf(IllegalStateException.class);
    }
    String confirm() {String r=revision(); var preview=imports.preview(batch,r);assertThat(preview.valid()).isTrue();return imports.confirm(batch,r,preview.hash(),false,"historical-admin").getFirst();}
    MockHttpServletRequestBuilder request() {
        var req=post("/admin/importaciones/"+batch+"/correspondencias").with(csrf()).param("expectedRevision",String.valueOf(imports.revisions(batch).size())).param("reviewMode","CHECKBOX_V1")
                .param("consentColumn","-1").param("consentValue","SI").param("dateColumn","-1").param("externalIdColumn","-1")
                .param("optionMode","AUTO").param("multipleSeparator","JSON").param("compatible","true");
        for(var column:mapping(batch).columns())req.param("column."+column.questionId(),String.valueOf(column.index()));return req;
    }
    @Test void explicitHistoricalReceiptPreservesUnknownConsentAndOriginalWithVerifiedProvenance() {
        byte[] original=imports.original(batch); String id=confirm();var capture=captures.input(id);
        assertThat(jdbc.queryForMap("SELECT consent FROM survey_response WHERE id=?",id).get("consent")).isNull();
        var payload=CanonicalJson.read(jdbc.queryForObject("SELECT capture_json FROM response_capture WHERE response_id=?",String.class,id),CaptureStore.Payload.class);
        assertThat(payload.form().consent()).isFalse();
        assertThat(capture.answers()).containsEntry("tipo",List.of("NO_SABE")).containsEntry("opciones",List.of("A","B")).containsEntry("texto",List.of(" texto original "));
        assertThat(capture.states()).containsEntry("tipo","CON_VALOR");assertThat(capture.origin()).isEqualTo("NO_DECLARADO");
        var evidence=CsvImportEvidence.verify(jdbc,id,version,capture.captureHash());
        assertThat(evidence).containsEntry("receipt_mode",CsvImportService.HISTORICAL_VALUE).containsEntry("consent_status","NO_REGISTRADO_EN_FUENTE");
        assertThat(imports.original(batch)).isEqualTo(original);
    }
    @Test void historicalReceiptNeedsExplicitModeAndAcknowledgementAndNormalModeStillNeedsSourceConsent() throws Exception {
        mvc.perform(request()).andExpect(status().is3xxRedirection());var r=imports.revisions(batch).getFirst();
        assertThat(imports.preview(batch,r.id()).valid()).isFalse();assertThat(imports.imported(batch)).isEmpty();
        mvc.perform(request().param("sourceMode","HISTORICA")).andExpect(status().isBadRequest());
        assertThat(imports.revisions(batch)).hasSize(1);
    }
    @Test void httpHistoricalReceiptRendersItsStatusAndPreservesAdministrativeReference() throws Exception {
        mvc.perform(request().param("sourceMode","HISTORICA").param("historicalReviewed","true").param("historicalEvidence","Entrega ficticia 01"))
                .andExpect(status().is3xxRedirection());
        var r=imports.revisions(batch).getFirst(); assertThat(CsvImportService.historical(r.mapping())).isTrue();
        assertThat(r.mapping().consentEvidence()).contains("Entrega ficticia 01");
        mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andExpect(model().attribute("historicalReference","Entrega ficticia 01"));
        mvc.perform(get("/admin/importaciones/"+batch+"/vista-previa").param("revisionId",r.id())).andExpect(status().isOk())
                .andExpect(content().string(containsString("El consentimiento individual no está registrado")));
    }
    @Test void blockedConsentPreviewOffersHistoricalReviewWithoutChangingTheSavedRevision() throws Exception {
        var m=mapping(batch);
        String normal=imports.mapColumns(batch,0,new CsvImportService.Mapping(m.columns(),-1,"SI",-1,-1,"AUTO","JSON",true,"Revisión anterior","Nota anterior de consentimiento"),"historical-admin");
        var before=imports.revision(batch,normal);assertThat(imports.preview(batch,normal).valid()).isFalse();
        mvc.perform(get("/admin/importaciones/"+batch+"/vista-previa").param("revisionId",normal)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Revisar como archivo histórico")));
        String suggested=mvc.perform(get("/admin/importaciones/"+batch+"/recepcion-historica")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(model().attribute("historicalReceipt",true))
                .andExpect(model().attribute("historicalReference","")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(suggested).doesNotContain("name=\"historicalReviewed\" value=\"true\" checked");
        assertThat(imports.revisions(batch)).hasSize(1);assertThat(imports.revision(batch,normal)).isEqualTo(before);
        mvc.perform(request().param("sourceMode","HISTORICA")).andExpect(status().isBadRequest());
        assertThat(imports.revisions(batch)).hasSize(1);
        mvc.perform(request().param("sourceMode","HISTORICA").param("historicalReviewed","true")).andExpect(status().is3xxRedirection());
        var reviewed=imports.revisions(batch).getFirst();assertThat(CsvImportService.historical(reviewed.mapping())).isTrue();assertThat(imports.preview(batch,reviewed.id()).valid()).isTrue();
        assertThat(imports.preview(batch,normal).valid()).isFalse();assertThat(imports.imported(batch)).isEmpty();
    }
    @Test void missingOrForgedHistoricalEvidenceCannotBypassConsent() {
        var m=mapping(batch);
        var forged=new CsvImportService.Mapping(m.columns(),-2,m.consentValue(),-1,-1,"AUTO","JSON",true,m.compatibilityEvidence(),"Una revisión cualquiera");
        assertThatThrownBy(()->imports.mapColumns(batch,0,forged,"historical-admin")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("recepción histórica");
        var other=new CsvImportService.Mapping(m.columns(),-2,"SI",-1,-1,"AUTO","JSON",true,m.compatibilityEvidence(),m.consentEvidence());
        assertThatThrownBy(()->imports.mapColumns(batch,0,other,"historical-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(imports.revisions(batch)).isEmpty();
    }
    @Test void retriesAreIdempotentAndChangingReceiptMetadataCannotBeConfirmedAgain() {
        String r=revision(); var preview=imports.preview(batch,r); var ids=imports.confirm(batch,r,preview.hash(),false,"historical-admin");
        assertThat(imports.confirm(batch,r,preview.hash(),false,"historical-admin")).isEqualTo(ids);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE publication_version_id=?",Integer.class,version)).isEqualTo(1);
        var m=mapping(batch);String next=imports.mapColumns(batch,1,new CsvImportService.Mapping(m.columns(),m.consentColumn(),m.consentValue(),-1,-1,"AUTO","JSON",true,m.compatibilityEvidence(),m.consentEvidence()+"\nReferencia: Otra revisión"),"historical-admin");
        assertThatThrownBy(()->imports.confirm(batch,next,imports.preview(batch,next).hash(),false,"historical-admin")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void missingReceiptOrAlteredConsentFlagFailsIntegrityVerification() {
        String id=confirm();jdbc.update("UPDATE survey_response SET consent=TRUE WHERE id=?",id);
        assertThatThrownBy(()->captures.input(id)).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE survey_response SET consent=NULL WHERE id=?",id);
        assertThat(captures.input(id).hasSnapshot()).isTrue();jdbc.update("DELETE FROM csv_import_row WHERE response_id=?",id);
        assertThatThrownBy(()->captures.input(id)).isInstanceOf(IllegalStateException.class);
    }
    @Test void historicalValidationDoesNotRelaxPublicConsentOrQuestionValidation() {
        var survey=publications.get(version).content().definition();var fields=new LinkedMultiValueMap<String,String>();fields.set("tipo","NO_SABE");
        assertThat(validator.validate(survey,fields).errors()).containsKey("consent");
        assertThat(validator.validateHistorical(survey,fields).valid()).isTrue();fields.set("tipo","INVALIDO");
        assertThat(validator.validateHistorical(survey,fields).errors()).containsKey("tipo");fields.set("consent","SI");
        assertThatThrownBy(()->validator.validateHistorical(survey,fields)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void actualPublicSubmissionStillRequiresParticipantConsentDespiteHistoricalRequestFields() throws Exception {
        var published=publications.get(version);
        var opened=mvc.perform(get(published.publicPath())).andExpect(status().isOk()).andReturn();
        String token=(String)opened.getModelAndView().getModel().get("formToken");
        var session=(org.springframework.mock.web.MockHttpSession)opened.getRequest().getSession();
        mvc.perform(post(published.publicPath()).session(session).with(csrf()).param("formToken",token).param("tipo","NO_SABE")
                        .param("sourceMode","HISTORICA").param("historicalReviewed","true"))
                .andExpect(status().isUnprocessableEntity()).andExpect(content().string(containsString("Debe aceptar el uso académico")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE publication_version_id=?",Integer.class,version)).isZero();
    }
    @Test void historicalEndpointsStillEnforceAdminCsrfAndDoNotAutoSelectReceipt() throws Exception {
        mvc.perform(request().with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/importaciones/"+batch+"/correspondencias")).andExpect(status().isForbidden());
        mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andExpect(model().attribute("historicalReceipt",false));
        assertThat(imports.revisions(batch)).isEmpty();
        var fixture=java.nio.file.Path.of(".codex-tmp/predefined-browser");java.nio.file.Files.createDirectories(fixture);
        java.nio.file.Files.writeString(fixture.resolve("import.html"),mvc.perform(get("/admin/importaciones/"+batch)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test void historicalTextIsPreservedBeyondPublicFormLimitWithoutRelaxingPublicCapture() {
        String text="x".repeat(3000);
        String source=imports.upload(version,"texto-historico-ficticio.csv",("Tipo;Selecciones;Texto\nSí;;"+text+"\n").getBytes(StandardCharsets.UTF_8),"PUNTO_COMA","GENERICO","historical-admin");
        String revision=imports.mapColumns(source,0,mapping(source),"historical-admin");var preview=imports.preview(source,revision);assertThat(preview.valid()).isTrue();
        String id=imports.confirm(source,revision,preview.hash(),false,"historical-admin").getFirst();
        assertThat(captures.input(id).answers().get("texto")).containsExactly(text);
        var publicValues=new LinkedMultiValueMap<String,String>();publicValues.set("consent","SI");publicValues.set("tipo","SI");publicValues.set("texto",text);
        assertThat(validator.validate(publications.get(version).content().definition(),publicValues).errors()).containsKey("texto");
    }
    @Test void xlsxHistoricalReceiptVerifiesSelectedOriginalSheetAndNullConsent() {
        var table=List.of(List.of("Tipo","Selecciones","Texto"),List.of("Sí","[\"Azul\"]","Histórico ficticio"));
        byte[] source=XlsxFileTest.workbook(XlsxFileTest.sheet(table));
        String excel=imports.upload(version,"historico-ficticio.xlsx",source,"XLSX:1","MICROSOFT_FORMS","historical-admin");
        var m=mapping(excel);String revision=imports.mapColumns(excel,0,m,"historical-admin");var preview=imports.preview(excel,revision);
        assertThat(preview.valid()).isTrue();String id=imports.confirm(excel,revision,preview.hash(),false,"historical-admin").getFirst();
        var capture=captures.input(id);assertThat(capture.provenance()).isEqualTo("IMPORTACION_XLSX");
        assertThat(CsvImportEvidence.verify(jdbc,id,version,capture.captureHash())).containsEntry("source_format","XLSX").containsEntry("consent_status","NO_REGISTRADO_EN_FUENTE");
        assertThat(jdbc.queryForMap("SELECT consent FROM survey_response WHERE id=?",id).get("consent")).isNull();
        assertThat(imports.original(excel)).isEqualTo(source);
    }
    @Test void malformedJsonDoesNotSilentlyDropASelectionOrPartiallyImportGoodRows() {
        String source="Tipo;Selecciones;Texto\nSí;\"[\"\"Azul\"\"]\";Ficticio\nSí;\"[\"\"Azul\"\"\";Mal formado\n";
        batch=imports.upload(version,"incorrecto-ficticio.csv",source.getBytes(StandardCharsets.UTF_8),"PUNTO_COMA","MICROSOFT_FORMS","historical-admin");
        String revision=revision();var preview=imports.preview(batch,revision);assertThat(preview.valid()).isFalse();
        assertThat(preview.rows().get(1).errors()).containsKey("opciones");
        assertThatThrownBy(()->imports.confirm(batch,revision,preview.hash(),false,"historical-admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(imports.imported(batch)).isEmpty();
    }
}

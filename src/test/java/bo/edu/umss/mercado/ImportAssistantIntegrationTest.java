package bo.edu.umss.mercado;

import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.importing.*;
import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.web.CsvImportController;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:assistant-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="assistant-admin",roles="ADMIN") @Transactional
class ImportAssistantIntegrationTest {
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired CsvImportService csv;
    @Autowired CaptureStore captures;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String version,batch;
    byte[] bytes(String row) {return ("Consentimiento;Tipo;Selecciones;Texto;Meses\n"+row+"\n").getBytes(StandardCharsets.UTF_8);}
    @BeforeEach void setup() {
        version=drafts.create("Encuesta del asistente","Datos ficticios","assistant-admin");
        drafts.section(version,0,null,"Datos","assistant-admin");String section=drafts.get(version).sections().getFirst().id();
        var qs=List.of(new Question("tipo","Datos","Tipo","radio",true,List.of(new Question.Option("SI","Sí"),new Question.Option("NO","No"),new Question.Option("NO_SABE","No sabe")),null,null,0,1,"",null,null),
                new Question("opciones","Datos","Selecciones","checkbox",false,List.of(new Question.Option("A","Rojo, oscuro"),new Question.Option("B","Azul"),new Question.Option("C","Verde")),null,null,1,3,"",null,null),
                new Question("texto","Datos","Texto","textarea",false,List.of(),null,null,0,1,"",null,null),
                new Question("meses","Datos","Meses","number",false,List.of(),null,null,0,1,"",0,120));
        for(int i=0;i<qs.size();i++)drafts.question(version,i+1,section,qs.get(i),true,"assistant-admin");
        publications.publish(version,5,"assistant-admin");
        batch=csv.upload(version,"ejemplo.csv",bytes("Sí;Sí;Azul|Verde; Texto original ;0"),"AUTO","GENERICO","assistant-admin");
    }
    CsvImportService.Mapping proposed(String id) {var b=csv.batch(id);return ImportSuggestions.propose(publications.get(version).content().definition(),b.table()).mapping();}
    MockHttpServletRequestBuilder mappingRequest(String id,boolean accept) {
        var p=proposed(id);var request=post("/admin/importaciones/"+id+"/correspondencias").with(csrf()).param("expectedRevision",String.valueOf(csv.revisions(id).size()))
                .param("reviewMode","CHECKBOX_V1")
                .param("consentColumn",String.valueOf(p.consentColumn())).param("consentValue",p.consentValue())
                .param("dateColumn","-1").param("externalIdColumn","-1").param("optionMode","AUTO").param("multipleSeparator","AUTO");
        for(var c:p.columns())request.param("column."+c.questionId(),String.valueOf(c.index()));
        if(accept)request.param("compatible","true").param("consentReviewed","true");return request;
    }
    String save(String id,boolean accept) throws Exception {
        mvc.perform(mappingRequest(id,accept)).andExpect(status().is3xxRedirection());return csv.revisions(id).getFirst().id();
    }
    void capture(String name,MvcResult result) throws Exception {
        if(!Boolean.getBoolean("assistant.capture"))return;
        var dir=Path.of(".codex-tmp/import-assistant-browser");Files.createDirectories(dir);Files.writeString(dir.resolve(name+".html"),result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    @Test void proposesWithoutWritingOrCheckingAcknowledgements() throws Exception {
        var result=mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andReturn();
        var model=result.getModelAndView().getModel();var m=(CsvImportService.Mapping)model.get("mapping");
        assertThat(m.columns()).allMatch(c->c.index()>=0);assertThat(m.compatible()).isFalse();assertThat(m.consentEvidence()).isEmpty();
        assertThat(csv.revisions(batch)).isEmpty();assertThat(csv.imported(batch)).isEmpty();capture("mapping",result);
    }
    @Test void checkboxesCreateTraceableEvidenceWithoutMandatoryEssayAndPreserveOriginal() throws Exception {
        String revision=save(batch,true);var mapping=csv.revision(batch,revision).mapping();
        assertThat(mapping.compatibilityEvidence()).isEqualTo(CsvImportController.COMPATIBILITY_ACK);
        assertThat(mapping.consentEvidence()).isEqualTo(CsvImportController.CONSENT_ACK);
        assertThat(jdbc.queryForObject("SELECT actor FROM csv_import_mapping WHERE id=?",String.class,revision)).isEqualTo("assistant-admin");
        var p=csv.preview(batch,revision);assertThat(p.valid()).isTrue();
        var ids=csv.confirm(batch,revision,p.hash(),false,"assistant-admin");var capture=captures.input(ids.getFirst());
        assertThat(capture.answers()).containsEntry("opciones",List.of("B","C")).containsEntry("texto",List.of(" Texto original ")).containsEntry("meses",List.of("0"));
        assertThat(capture.origin()).isEqualTo("NO_DECLARADO");assertThat(csv.original(batch)).isEqualTo(bytes("Sí;Sí;Azul|Verde; Texto original ;0"));
        assertThat(csv.confirm(batch,revision,p.hash(),false,"assistant-admin")).isEqualTo(ids);
    }
    @Test void missingChecksOrNegativeParticipantConsentCannotBeConfirmed() throws Exception {
        String revision=save(batch,false);assertThat(csv.preview(batch,revision).valid()).isFalse();
        assertThatThrownBy(()->csv.confirm(batch,revision,csv.preview(batch,revision).hash(),false,"assistant-admin")).isInstanceOf(IllegalArgumentException.class);
        String negative=csv.upload(version,"sin-consentimiento.csv",bytes("No;Sí;Azul;Texto;0"),"AUTO","GENERICO","assistant-admin");
        String r=save(negative,true);assertThat(csv.preview(negative,r).valid()).isFalse();assertThat(csv.imported(negative)).isEmpty();
    }
    @Test void discrepanciesShowRowQuestionOriginalAndActionAndEscapeHtml() throws Exception {
        String bad=csv.upload(version,"error.csv",bytes("Sí;<script>evil()</script>;Azul;Texto;0"),"AUTO","GENERICO","assistant-admin");
        String revision=save(bad,true);
        var result=mvc.perform(get("/admin/importaciones/"+bad+"/vista-previa").param("revisionId",revision)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Discrepancias que necesitan atención")))
                .andExpect(content().string(containsString("&lt;script&gt;evil()&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>evil()</script>")))).andReturn();
        assertThat(csv.imported(bad)).isEmpty();capture("errors",result);
    }
    @Test void legacyRevisionsKeepTheirMappingAndPreviewWhenOpened() throws Exception {
        var p=proposed(batch);var legacy=new CsvImportService.Mapping(p.columns(),p.consentColumn(),p.consentValue(),-1,-1,"ETIQUETAS","|",true,"Revisión anterior","Consentimiento anterior");
        String revision=csv.mapColumns(batch,0,legacy,"legacy-admin"),hash=csv.preview(batch,revision).hash();
        mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andExpect(model().attribute("mapping",legacy));
        assertThat(csv.preview(batch,revision).hash()).isEqualTo(hash);assertThat(csv.revisions(batch)).hasSize(1);
    }
    @Test void excelOneSheetOpensReviewDirectlyAndMultipleSheetsAsk() throws Exception {
        String sheet=XlsxFileTest.sheet(CsvFile.parse(bytes("Sí;Sí;Azul;Texto;0"),"PUNTO_COMA"));
        var single=new MockMultipartFile("file","una.xlsx","application/octet-stream",XlsxFileTest.workbook(sheet));
        var r=mvc.perform(multipart("/admin/importaciones").file(single).with(csrf()).param("versionId",version)).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(r.getResponse().getRedirectedUrl()).startsWith("/admin/importaciones/").doesNotContain("/excel/");
        var multiple=new MockMultipartFile("file","varias.xlsx","application/octet-stream",XlsxFileTest.workbook(sheet,sheet));
        var m=mvc.perform(multipart("/admin/importaciones").file(multiple).with(csrf()).param("versionId",version)).andExpect(status().is3xxRedirection()).andReturn();
        assertThat(m.getResponse().getRedirectedUrl()).startsWith("/admin/importaciones/excel/");
        capture("sheets",mvc.perform(get(m.getResponse().getRedirectedUrl())).andExpect(status().isOk()).andReturn());
    }
    @Test void serverEnforcesAdminCsrfAndDoesNotAcceptDuplicateAcknowledgementFields() throws Exception {
        mvc.perform(mappingRequest(batch,true).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/importaciones/"+batch+"/correspondencias")).andExpect(status().isForbidden());
        mvc.perform(mappingRequest(batch,true).param("compatible","true")).andExpect(status().isBadRequest());
        assertThat(csv.revisions(batch)).isEmpty();
    }
    @Test void validPreviewAndCompletionAreNavigableWithoutReenteringSurvey() throws Exception {
        String revision=save(batch,true);var p=csv.preview(batch,revision);
        capture("preview",mvc.perform(get("/admin/importaciones/"+batch+"/vista-previa").param("revisionId",revision)).andExpect(status().isOk()).andReturn());
        mvc.perform(post("/admin/importaciones/"+batch+"/confirmar").with(csrf()).param("revisionId",revision).param("previewHash",p.hash())).andExpect(redirectedUrl("/admin/importaciones/"+batch));
        capture("completed",mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andExpect(content().string(containsString("Importación completada"))).andReturn());
    }
    @Test void optionalReferenceIsRetainedButUncheckingConsentDoesNotApproveIt() throws Exception {
        mvc.perform(mappingRequest(batch,true).param("consentEvidence","Documento de origen, sección 2")).andExpect(status().is3xxRedirection());
        var revision=csv.revisions(batch).getFirst();assertThat(revision.mapping().consentEvidence()).contains(CsvImportController.CONSENT_ACK,"Documento de origen, sección 2");
        mvc.perform(get("/admin/importaciones/"+batch)).andExpect(model().attribute("consentReference","Documento de origen, sección 2"));
        mvc.perform(mappingRequest(batch,false).param("consentEvidence","Documento de origen, sección 2")).andExpect(status().is3xxRedirection());
        var pending=csv.revisions(batch).getFirst();assertThat(csv.preview(batch,pending.id()).valid()).isFalse();
        assertThat(csv.revision(batch,revision.id()).mapping().consentEvidence()).contains("Documento de origen, sección 2");
    }
}

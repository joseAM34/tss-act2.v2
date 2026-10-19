package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CanonicalJson;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:import-catalog-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Transactional @WithMockUser(username="catalog-admin",roles="ADMIN")
class ImportCatalogIntegrationTest {
    private static final String ACTOR="catalog-admin";
    private static final String TECHNICAL="Técnico (desarrollo de software, administración de sistemas, redes, bases de datos, soporte TI)";
    @Autowired ImportCatalogService catalog;
    @Autowired CsvImportService imports;
    @Autowired XlsxImportService excel;
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired CaptureStore captures;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String version,batch,revision;

    @BeforeEach void setup() {
        version=drafts.create("Recepción de opciones ficticias","Fixture sintético; no contiene datos de campo",ACTOR);
        drafts.section(version,0,null,"Datos",ACTOR);
        String section=drafts.get(version).sections().getFirst().id();
        drafts.question(version,1,section,new Question("cargos","Datos","Cargos","checkbox",false,
                List.of(new Question.Option("TECNICO",TECHNICAL),new Question.Option("OTRO","Otro")),null,null,0,2,"Ayuda original",null,null),true,ACTOR);
        publications.publish(version,2,ACTOR);
        batch=imports.upload(version,"opciones-ficticias.csv",("Cargos\n"+TECHNICAL+", Docentes\n").getBytes(StandardCharsets.UTF_8),"PUNTO_COMA","GOOGLE_FORMS",ACTOR);
        revision=imports.mapColumns(batch,0,mapping(),ACTOR);
    }

    private CsvImportService.Mapping mapping() {
        return new CsvImportService.Mapping(List.of(new CsvImportService.Column("cargos",0)),-2,CsvImportService.HISTORICAL_VALUE,-1,-1,
                "AUTO","MIXTO_V1",true,"Revisión ficticia del instrumento",CsvImportService.HISTORICAL_ACK);
    }
    private String prepare() {
        return catalog.prepare(batch,revision,imports.preview(batch,revision).hash(),"cargos",List.of("Docentes"),ACTOR);
    }
    private Question question(String id) { return drafts.get(id).items().getFirst().question(); }

    @Test void proposesOnlyObservedLabelsAndPreparesAnEditableSuccessorWithoutChangingOriginals() {
        String sourceHash=publications.get(version).hash();byte[] original=imports.original(batch);
        int events=jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event",Integer.class);
        assertThat(catalog.proposals(batch,revision)).containsExactly(new ImportCatalogService.Proposal("cargos","Cargos",List.of("Docentes")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_definition_event",Integer.class)).isEqualTo(events);
        assertThat(imports.preview(batch,revision).valid()).isFalse();
        String successor=prepare();
        assertThat(drafts.editable(successor)).isTrue();assertThat(successor).isNotEqualTo(version);
        assertThat(question(successor).options()).containsExactly(new Question.Option("TECNICO",TECHNICAL),new Question.Option("OTRO","Otro"),
                new Question.Option("OBS_"+CanonicalJson.hash("Docentes").substring(0,12),"Docentes"));
        assertThat(question(successor).maxSelections()).isEqualTo(3);
        assertThat(publications.get(version).hash()).isEqualTo(sourceHash);
        assertThat(publications.get(version).content().definition().questions().getFirst().options()).hasSize(2);
        assertThat(imports.original(batch)).isEqualTo(original);assertThat(imports.batch(batch).versionId()).isEqualTo(version);
        assertThat(imports.imported(batch)).isEmpty();assertThat(imports.revisions(batch)).hasSize(1);
    }

    @Test void preservesExistingDraftEditsAndDoesNotDuplicateRepeatedAdaptations() {
        String successor=publications.successor(version,ACTOR);
        var draft=drafts.get(successor);
        drafts.updateHeader(successor,draft.revision(),"Título editado por el equipo","Descripción modificada",ACTOR);
        draft=drafts.get(successor);var q=question(successor);var options=new ArrayList<>(q.options());options.add(new Question.Option("CONSULTOR","Consultor"));
        var edited=new Question(q.id(),q.section(),"Enunciado revisado",q.type(),q.required(),options,q.dependsOn(),q.dependsValue(),q.minSelections(),2,
                "Ayuda revisada por el equipo",q.min(),q.max());
        drafts.question(successor,draft.revision(),draft.sections().getFirst().id(),edited,false,ACTOR);
        assertThat(prepare()).isEqualTo(successor);
        var adapted=drafts.get(successor);assertThat(adapted.title()).isEqualTo("Título editado por el equipo");
        assertThat(question(successor).label()).isEqualTo("Enunciado revisado");
        assertThat(question(successor).hint()).isEqualTo("Ayuda revisada por el equipo");
        assertThat(question(successor).options()).extracting(Question.Option::value).contains("CONSULTOR");
        assertThat(question(successor).maxSelections()).isEqualTo(2);
        long currentRevision=adapted.revision();assertThat(prepare()).isEqualTo(successor);
        assertThat(drafts.get(successor).revision()).isEqualTo(currentRevision);
        assertThat(question(successor).options()).filteredOn(o->o.label().equals("Docentes")).hasSize(1);
    }

    @Test void publishedAdaptationCanReuseTheVerifiedFileButRequiresFreshMappingAndReview() {
        String successor=prepare();publications.publish(successor,drafts.get(successor).revision(),ACTOR);
        String reused=catalog.reuse(batch,successor,ACTOR);
        assertThat(reused).isNotEqualTo(batch);assertThat(catalog.reuse(batch,successor,ACTOR)).isEqualTo(reused);
        assertThat(imports.original(reused)).isEqualTo(imports.original(batch));
        assertThat(imports.batch(reused).sourceHash()).isEqualTo(imports.batch(batch).sourceHash());
        assertThat(imports.batch(reused).delimiter()).isEqualTo("PUNTO_COMA");
        assertThat(imports.revisions(reused)).isEmpty();assertThat(imports.imported(reused)).isEmpty();
        var suggestion=ImportSuggestions.propose(publications.get(successor).content().definition(),imports.batch(reused).table()).mapping();
        assertThat(suggestion.compatible()).isFalse();assertThat(suggestion.consentEvidence()).isEmpty();
        String fresh=imports.mapColumns(reused,0,mapping(),ACTOR);var preview=imports.preview(reused,fresh);
        assertThat(preview.valid()).isTrue();assertThat(preview.overlaps()).isGreaterThan(0);
        String response=imports.confirm(reused,fresh,preview.hash(),true,ACTOR).getFirst();
        assertThat(captures.input(response).answers().get("cargos")).containsExactlyInAnyOrder("TECNICO","OBS_"+CanonicalJson.hash("Docentes").substring(0,12));
        assertThat(captures.input(response).origin()).isEqualTo("NO_DECLARADO");
        assertThat(imports.imported(batch)).isEmpty();assertThat(imports.batch(batch).versionId()).isEqualTo(version);
        assertThat(imports.preview(batch,revision).valid()).isFalse();
    }

    @Test void refusesStalePreviewsAndUnobservedLabelsWithoutCreatingADraft() {
        int versions=jdbc.queryForObject("SELECT COUNT(*) FROM survey_version",Integer.class);
        assertThatThrownBy(()->catalog.prepare(batch,revision,"invalid-hash","cargos",List.of("Docentes"),ACTOR))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("vista previa cambió");
        assertThatThrownBy(()->catalog.prepare(batch,revision,imports.preview(batch,revision).hash(),"cargos",List.of("Inventado"),ACTOR))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("valores observados");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_version",Integer.class)).isEqualTo(versions);
    }

    @Test void refusesOtherSurveysAndDoesNotOverwriteAnIncompatibleExistingDraft() {
        String other=drafts.copyPublished(publications.get(version).content(),ACTOR);
        publications.publish(other,drafts.get(other).revision(),ACTOR);
        assertThatThrownBy(()->catalog.reuse(batch,other,ACTOR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("misma encuesta");
        String successor=publications.successor(version,ACTOR);var draft=drafts.get(successor);var q=question(successor);
        var edited=new Question(q.id(),q.section(),q.label(),q.type(),false,List.of(new Question.Option("NUEVO","Catálogo distinto")),null,null,0,1,"Cambios ajenos",null,null);
        drafts.question(successor,draft.revision(),draft.sections().getFirst().id(),edited,false,ACTOR);
        assertThatThrownBy(this::prepare).isInstanceOf(ResponseStatusException.class).hasMessageContaining("opciones originales");
        assertThat(question(successor)).isEqualTo(edited);
        assertThat(imports.batch(batch).versionId()).isEqualTo(version);assertThat(imports.imported(batch)).isEmpty();
    }

    @Test void reusesAnEquivalentLabelAlreadyEditedInTheDraftWithoutAddingAnAmbiguousDuplicate() {
        String successor=publications.successor(version,ACTOR);var draft=drafts.get(successor);var q=question(successor);
        var options=new ArrayList<>(q.options());options.add(new Question.Option("DOCENCIA","DócEntes"));
        var edited=new Question(q.id(),q.section(),q.label(),q.type(),q.required(),options,q.dependsOn(),q.dependsValue(),q.minSelections(),3,q.hint(),q.min(),q.max());
        drafts.question(successor,draft.revision(),draft.sections().getFirst().id(),edited,false,ACTOR);
        long before=drafts.get(successor).revision();
        assertThat(prepare()).isEqualTo(successor);
        assertThat(question(successor).options()).containsExactlyElementsOf(options);
        assertThat(drafts.get(successor).revision()).isEqualTo(before);
        assertThat(ImportSuggestions.options(question(successor),TECHNICAL+", Docentes","AUTO","MIXTO_V1"))
                .containsExactly("TECNICO","DOCENCIA");
    }

    @Test void refusesAnAmbiguousEditedCatalogWithoutChangingItsDraft() {
        String successor=publications.successor(version,ACTOR);var draft=drafts.get(successor);var q=question(successor);
        var options=new ArrayList<>(q.options());options.add(new Question.Option("DOCENCIA_1","DócEntes"));options.add(new Question.Option("DOCENCIA_2","docentes"));
        var edited=new Question(q.id(),q.section(),q.label(),q.type(),q.required(),options,q.dependsOn(),q.dependsValue(),q.minSelections(),4,q.hint(),q.min(),q.max());
        drafts.question(successor,draft.revision(),draft.sections().getFirst().id(),edited,false,ACTOR);
        var before=drafts.get(successor);
        assertThatThrownBy(this::prepare).isInstanceOfSatisfying(ResponseStatusException.class,
                ex->assertThat(ex.getStatusCode().value()).isEqualTo(409));
        assertThat(drafts.get(successor)).isEqualTo(before);
        assertThat(imports.batch(batch).versionId()).isEqualTo(version);assertThat(imports.imported(batch)).isEmpty();
    }

    @Test void httpReviewShowsTheProposalAndPreparesThenReusesThePublishedAdaptation() throws Exception {
        mvc.perform(get("/admin/importaciones/"+batch+"/vista-previa").param("revisionId",revision))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                .andExpect(content().string(containsString("Conservar una opción que falta en el catálogo")))
                .andExpect(content().string(containsString("name=\"labels\" value=\"Docentes\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        var prepared=mvc.perform(post("/admin/importaciones/"+batch+"/ampliar-catalogo").with(csrf())
                        .param("revisionId",revision).param("previewHash",imports.preview(batch,revision).hash())
                        .param("questionId","cargos").param("labels","Docentes"))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String draft=prepared.getResponse().getRedirectedUrl().substring("/admin/encuestas/".length());
        assertThat(drafts.editable(draft)).isTrue();assertThat(imports.imported(batch)).isEmpty();
        mvc.perform(post("/admin/encuestas/"+draft+"/publicar").with(csrf()).param("revision",String.valueOf(drafts.get(draft).revision())))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Usar este archivo en otra versión")))
                .andExpect(content().string(containsString("value=\""+draft+"\"")));
        var reused=mvc.perform(post("/admin/importaciones/"+batch+"/reutilizar").with(csrf()).param("versionId",draft))
                .andExpect(status().is3xxRedirection()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        String newBatch=reused.getResponse().getRedirectedUrl().substring("/admin/importaciones/".length());
        assertThat(imports.batch(newBatch).versionId()).isEqualTo(draft);
        assertThat(imports.original(newBatch)).isEqualTo(imports.original(batch));
        assertThat(imports.revisions(newBatch)).isEmpty();assertThat(imports.imported(newBatch)).isEmpty();
    }

    @Test void httpCheckboxPreservesOneLiteralLabelContainingACommaAndEscapesSourceHtml() throws Exception {
        String label="Docentes (colegios, institutos) <b>ficticio</b>";
        String source=imports.upload(version,"etiqueta-ficticia.csv",("Cargos\n"+TECHNICAL+", "+label+"\n").getBytes(StandardCharsets.UTF_8),"PUNTO_COMA","GOOGLE_FORMS",ACTOR);
        String mapped=imports.mapColumns(source,0,mapping(),ACTOR);
        mvc.perform(get("/admin/importaciones/"+source+"/vista-previa").param("revisionId",mapped))
                .andExpect(status().isOk()).andExpect(content().string(containsString("&lt;b&gt;ficticio&lt;/b&gt;")));
        var result=mvc.perform(post("/admin/importaciones/"+source+"/ampliar-catalogo").with(csrf())
                        .param("revisionId",mapped).param("previewHash",imports.preview(source,mapped).hash()).param("questionId","cargos").param("labels",label))
                .andExpect(status().is3xxRedirection()).andReturn();
        String draft=result.getResponse().getRedirectedUrl().substring("/admin/encuestas/".length());
        assertThat(question(draft).options()).filteredOn(o->o.label().equals(label)).hasSize(1);
        assertThat(question(draft).options()).hasSize(3);
        String body=mvc.perform(get("/admin/encuestas/"+draft)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body).doesNotContain("<b>ficticio</b>");
    }

    @Test void httpMutationsRequireAdminAndCsrfAndNeverWriteForRejectedRequests() throws Exception {
        String hash=imports.preview(batch,revision).hash();
        int versions=jdbc.queryForObject("SELECT COUNT(*) FROM survey_version",Integer.class);
        int batches=jdbc.queryForObject("SELECT COUNT(*) FROM csv_import_batch",Integer.class);
        for(String endpoint:List.of("ampliar-catalogo","reutilizar")) {
            mvc.perform(post("/admin/importaciones/"+batch+"/"+endpoint)
                            .param("revisionId",revision).param("previewHash",hash).param("questionId","cargos").param("labels","Docentes").param("versionId",version))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/importaciones/"+batch+"/"+endpoint).with(csrf()).with(user("viewer").roles("USER"))
                            .param("revisionId",revision).param("previewHash",hash).param("questionId","cargos").param("labels","Docentes").param("versionId",version))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/admin/importaciones/"+batch+"/"+endpoint).with(csrf()).with(anonymous())
                            .param("revisionId",revision).param("previewHash",hash).param("questionId","cargos").param("labels","Docentes").param("versionId",version))
                    .andExpect(status().is3xxRedirection());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_version",Integer.class)).isEqualTo(versions);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM csv_import_batch",Integer.class)).isEqualTo(batches);
        assertThat(imports.imported(batch)).isEmpty();
    }

    @Test void xlsxReuseKeepsTheSelectedSecondSheetWithoutRequiringAnotherWorkbookRecord() {
        byte[] original=XlsxFileTest.workbook(XlsxFileTest.sheet(List.of(List.of("Auxiliar"),List.of("No importar"))),
                XlsxFileTest.sheet(List.of(List.of("Cargos"),List.of(TECHNICAL+", Docentes"))));
        String workbook=excel.upload(version,"hojas-ficticias.xlsx",original,"GOOGLE_FORMS",ACTOR);
        String selected=excel.select(workbook,2,ACTOR);String mapped=imports.mapColumns(selected,0,mapping(),ACTOR);
        String successor=catalog.prepare(selected,mapped,imports.preview(selected,mapped).hash(),"cargos",List.of("Docentes"),ACTOR);
        publications.publish(successor,drafts.get(successor).revision(),ACTOR);
        String reused=catalog.reuse(selected,successor,ACTOR);
        assertThat(imports.original(reused)).isEqualTo(original);assertThat(imports.batch(reused).delimiter()).isEqualTo("XLSX:2");
        assertThat(imports.batch(reused).table()).containsExactly(List.of("Cargos"),List.of(TECHNICAL+", Docentes"));
        assertThat(excel.recentForVersion(successor)).isEmpty();assertThat(imports.recentForVersion(successor)).extracting(CsvImportService.Summary::id).contains(reused);
        String fresh=imports.mapColumns(reused,0,mapping(),ACTOR);var preview=imports.preview(reused,fresh);assertThat(preview.valid()).isTrue();
        String response=imports.confirm(reused,fresh,preview.hash(),true,ACTOR).getFirst();var capture=captures.input(response);
        assertThat(CsvImportEvidence.verify(jdbc,response,successor,capture.captureHash())).containsEntry("source_format","XLSX")
                .containsEntry("source_sheet",2).containsEntry("source_sheet_name","Respuestas 2");
        assertThat(capture.answers().get("cargos")).hasSize(2);assertThat(imports.imported(selected)).isEmpty();
    }
}

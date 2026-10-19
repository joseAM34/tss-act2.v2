package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.statistics.*;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.wrangling.*;
import bo.edu.umss.mercado.reporting.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
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

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:workspace-results-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}","spring.datasource.username=${TEST_DATABASE_USERNAME:sa}","spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username="results-admin",roles="ADMIN") @Transactional
class WorkspaceResultsIntegrationTest {
    static final String ACTOR="results-admin";
    @Autowired WorkspaceResultsService results;
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired SurveyResponseRepository responses;
    @Autowired SurveyValidator validator;
    @Autowired GenericPreparationService preparation;
    @Autowired GenericAnalysisService analyses;
    @Autowired GenericCrossService crosses;
    @Autowired WranglingService wrangling;
    @Autowired ReportService reports;
    @Autowired SurveyManagementService management;
    @Autowired SurveyDeletionService deletion;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired bo.edu.umss.mercado.security.AdministratorService administrators;
    @Autowired TextDictionaryService dictionaries;
    @org.springframework.test.context.bean.override.mockito.MockitoBean WikidataTerms wikidataRemote;
    @Autowired ReportPresentationTemplates presentations;
    @Test void privateDictionaryIsSharedByResultsAndReportsButIsolatedFromOtherQuestions() throws Exception {
        String v=drafts.create("Contextos ficticios","Prueba",ACTOR);drafts.section(v,0,null,"Datos",ACTOR);String section=drafts.get(v).sections().getFirst().id();
        drafts.question(v,1,section,new Question("programa","Datos","INDIQUE EL NOMBRE DEL PROGRAMA","text",false,List.of(),null,null,0,1,"",null,null),true,ACTOR);
        drafts.question(v,2,section,new Question("experiencia","Datos","Años de experiencia","text",false,List.of(),null,null,0,1,"",null,null),true,ACTOR);publications.publish(v,3,ACTOR);
        for(var pair:List.of(List.of("2","2"),List.of("Maestría en IA","2 años"))){var values=new LinkedMultiValueMap<String,String>();values.set("consent","SI");values.set("programa",pair.get(0));values.set("experiencia",pair.get(1));responses.savePublished(v,UUID.randomUUID().toString(),validator.validate(publications.get(v).content().definition(),values));}
        dictionaries.saveGlobal(0,"2 años=2 años|2\ninteligencia artificial=ia|inteligencia artificial",ACTOR);
        var session=new MockHttpSession();var payload=load(v,session,false);String frameId=(String)payload.get("frameId");var frame=results.load(slug(v),List.of(v),"","",ACTOR);
        var program=results.questionAnswers(frame,"programa",ACTOR);assertThat(program.get("groupable")).isEqualTo(true);assertThat(((Map<?,?>)program.get("keywords")).get("matched")).isEqualTo(1);
        mvc.perform(get(path(v)+"/sugerencias").session(session).param("frameId",frameId).param("questionId","programa")).andExpect(status().isOk());
        String endpoint=path(v)+"/sugerencias/";
        var scope=json(mvc.perform(post(endpoint+"scope").with(csrf()).session(session).param("frameId",frameId).param("questionId","experiencia").param("dictionaryScope","PRIVATE")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(scope.get("rules")).isEqualTo("");
        var imported=dictionaries.scopedImport(frame.logical(),"experiencia","PRIVATE","","dos años=2|2 años");assertThat(imported.get("rules").toString()).doesNotContain("inteligencia artificial");
        mvc.perform(post(endpoint+"guardar").with(csrf()).session(session).param("frameId",frameId).param("questionId","experiencia").param("dictionaryScope","PRIVATE").param("rules",imported.get("rules").toString()).param("term","true")).andExpect(status().isOk());
        assertThat(dictionaries.global().revision()).isEqualTo(1);assertThat(dictionaries.scope(frame.logical(),"programa")).isEqualTo("GLOBAL");
        var experience=results.questionAnswers(frame,"experiencia",ACTOR);assertThat(experience.get("dictionaryScope")).isEqualTo("PRIVATE");assertThat(((Map<?,?>)experience.get("keywords")).get("matched")).isEqualTo(2);
        String analysis=results.materialize(frame,ACTOR);var defaults=ReportCustomization.defaults(analyses.get(analysis),List.of());
        var questions=defaults.questions().stream().map(q->new ReportCustomization.Question(q.id(),true,q.title(),"",List.of("text-table","text-bars"))).toList();var config=new ReportCustomization.Configuration(analysis,"Contextos","",true,true,true,"",questions,List.of());
        var preview=reports.preview(config,ACTOR);var reportVariables=variables((Map<String,Object>)preview.get("display_result"));assertThat(((Map<?,?>)reportVariables.get(1).get("text_counts")).get("dictionary_scope")).isEqualTo("PRIVATE");assertThat(((Map<?,?>)reportVariables.get(1).get("text_counts")).get("matched")).isEqualTo(2);
        String report=reports.generateCustomized(config,CanonicalJson.hash(preview),ACTOR);
        mvc.perform(post("/admin/diccionario/guardar").with(csrf()).param("analysisId",analysis).param("questionId","experiencia").param("dictionaryScope","PRIVATE").param("expectedRevision","1").param("expectedContextRevision","1").param("rules","dos años=2|2 años|dos").param("reviewed","true")).andExpect(status().isOk());
        assertThat(dictionaries.get(frame,"experiencia").revision()).isEqualTo(2);assertThat(dictionaries.global().revision()).isEqualTo(1);assertThat(CanonicalJson.hash(reports.get(report).get("content"))).isEqualTo(CanonicalJson.hash(preview));
        mvc.perform(post(endpoint+"guardar").with(csrf()).session(session).param("frameId",frameId).param("questionId","experiencia").param("dictionaryScope","PRIVATE").param("expectedRevision","1").param("expectedContextRevision","1").param("rules","dos años=2").param("term","true")).andExpect(status().isConflict());
        var dir=java.nio.file.Path.of(".codex-tmp/scoped-dictionary-browser");java.nio.file.Files.createDirectories(dir);java.nio.file.Files.writeString(dir.resolve("private.json"),CanonicalJson.json(scope));java.nio.file.Files.writeString(dir.resolve("import.json"),CanonicalJson.json(imported));
        java.nio.file.Files.writeString(dir.resolve("results.html"),mvc.perform(get(path(v)+"/sugerencias").session(session).param("frameId",frameId).param("questionId","programa")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(dir.resolve("report.html"),mvc.perform(get("/admin/diccionario").param("analysisId",analysis).param("questionId","programa")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        var state=management.get(slug(v));var backup=deletion.prepare(slug(v),state.hash(),ACTOR);String surveySlug=slug(v);deletion.delete(surveySlug,backup.receipt().id(),backup.receipt().stateHash(),ACTOR);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM scoped_text_dictionary_revision",Integer.class)).isZero();assertThat(dictionaries.global().revision()).isEqualTo(1);
    }
    @Test void sensitiveTextNeedsExplicitAcknowledgementAndTemplatesDoNotReuseIt() throws Exception {
        String v=drafts.create("Empresas ficticias","Prueba",ACTOR);drafts.section(v,0,null,"Datos",ACTOR);String section=drafts.get(v).sections().getFirst().id();
        drafts.question(v,1,section,new Question("empresa","Datos","Nombre de la Empresa o Institución","text",false,List.of(),null,null,0,1,"",null,null),true,ACTOR);publications.publish(v,2,ACTOR);
        for(String text:List.of("DATEC","Datec Ltda.")){var values=new LinkedMultiValueMap<String,String>();values.set("consent","SI");values.set("empresa",text);responses.savePublished(v,UUID.randomUUID().toString(),validator.validate(publications.get(v).content().definition(),values));}
        dictionaries.saveGlobal(0,"datec=datec|datec ltda",ACTOR);
        var resultSession=new MockHttpSession();var resultPayload=load(v,resultSession,false);String resultFrame=(String)resultPayload.get("frameId");
        var locked=json(mvc.perform(get(path(v)+"/respuestas-pregunta").session(resultSession).param("frameId",resultFrame).param("questionId","empresa")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat((List<?>)locked.get("rows")).isEmpty();assertThat(locked.get("groupable")).isEqualTo(false);assertThat(locked.get("sensitive")).isEqualTo(true);assertThat(CanonicalJson.json(locked)).doesNotContain("DATEC");
        mvc.perform(get(path(v)+"/sugerencias").session(resultSession).param("frameId",resultFrame).param("questionId","empresa")).andExpect(status().isBadRequest());
        var allowed=json(mvc.perform(get(path(v)+"/respuestas-pregunta").session(resultSession).param("frameId",resultFrame).param("questionId","empresa").param("sensitiveAcknowledged","true")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat((List<?>)allowed.get("rows")).hasSize(2);assertThat(allowed.get("groupable")).isEqualTo(true);assertThat(((Map<?,?>)allowed.get("keywords")).get("matched")).isEqualTo(2);
        mvc.perform(post(path(v)+"/sugerencias/extraer").with(csrf()).session(resultSession).param("frameId",resultFrame).param("questionId","empresa")).andExpect(status().isBadRequest());
        mvc.perform(post(path(v)+"/sugerencias/extraer").with(csrf()).session(resultSession).param("frameId",resultFrame).param("questionId","empresa").param("sensitiveAcknowledged","true")).andExpect(status().isOk());
        var resultBrowser=java.nio.file.Path.of(".codex-tmp/results-sensitive-browser");java.nio.file.Files.createDirectories(resultBrowser);
        java.nio.file.Files.writeString(resultBrowser.resolve("manager.html"),mvc.perform(get(path(v)+"/sugerencias").session(resultSession).param("frameId",resultFrame).param("questionId","empresa").param("sensitiveAcknowledged","true")).andExpect(status().isOk()).andExpect(content().string(containsString("Datos sensibles o identificativos"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(resultBrowser.resolve("total.html"),mvc.perform(get(path(v)).session(resultSession).param("version",publications.get(v).code())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(resultBrowser.resolve("data.json"),CanonicalJson.json(resultPayload));java.nio.file.Files.writeString(resultBrowser.resolve("locked.json"),CanonicalJson.json(locked));java.nio.file.Files.writeString(resultBrowser.resolve("allowed.json"),CanonicalJson.json(allowed));
        var relocked=json(mvc.perform(get(path(v)+"/respuestas-pregunta").session(resultSession).param("frameId",resultFrame).param("questionId","empresa")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));assertThat((List<?>)relocked.get("rows")).isEmpty();
        String analysis=results.materialize(results.load(slug(v),List.of(v),"","",ACTOR),ACTOR);
        var blocked=variables((Map<String,Object>)reports.advancedData(analysis).get("result")).getFirst();
        assertThat(blocked.get("sensitive_text")).isEqualTo(true);assertThat(blocked).doesNotContainKeys("text_counts","text_content");
        var modes=List.of("text-content","text-table","text-bars","text-pie","text-donut");
        var unconfirmed=new ReportCustomization.Configuration(analysis,"Prueba sensible","",true,true,true,"",List.of(new ReportCustomization.Question("empresa",true,"Empresa","",modes)),List.of());
        assertThatThrownBy(()->reports.preview(unconfirmed,ACTOR)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("datos sensibles");
        mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(unconfirmed))).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/diccionario").param("analysisId",analysis).param("questionId","empresa")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/diccionario").param("analysisId",analysis).param("questionId","empresa").param("sensitiveAcknowledged","true")).andExpect(status().isOk()).andExpect(content().string(containsString("Datos sensibles o identificativos")));
        var confirmed=new ReportCustomization.Configuration(analysis,"Prueba sensible","",true,true,true,"",List.of(new ReportCustomization.Question("empresa",true,"Empresa","",modes,true)),List.of());
        var preview=reports.preview(confirmed,ACTOR);var variable=variables((Map<String,Object>)preview.get("display_result")).getFirst();
        assertThat(((Map<?,?>)variable.get("text_counts")).get("matched")).isEqualTo(2);assertThat((List<?>)variable.get("text_content")).hasSize(2);
        String report=presentations.generate(confirmed,CanonicalJson.hash(preview),true,"Presentación sensible ficticia",ACTOR);assertThat(reports.get(report)).isNotEmpty();
        var applied=presentations.apply(presentations.recent().getFirst().id(),analysis,ACTOR);assertThat(applied.questions().getFirst().sensitiveAcknowledged()).isFalse();assertThat(applied.questions().getFirst().representations()).doesNotContain("text-content","text-table");
        var dir=java.nio.file.Path.of(".codex-tmp/report-sensitive-browser");java.nio.file.Files.createDirectories(dir);
        java.nio.file.Files.writeString(dir.resolve("generator.html"),mvc.perform(get("/admin/reportes/nuevo").param("analysisId",analysis)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(dir.resolve("report.html"),mvc.perform(get("/admin/reportes/"+report)).andExpect(status().isOk()).andExpect(content().string(containsString("Datos sensibles o identificativos"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(dir.resolve("report.json"),CanonicalJson.json(preview));
        var defaults=ReportCustomization.defaults(analyses.get(analysis),List.of());
        java.nio.file.Files.writeString(dir.resolve("preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(defaults))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(dir.resolve("confirmed.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(confirmed))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }
    String publish(){String v=drafts.create("Resultados de prueba","Datos ficticios",ACTOR);drafts.section(v,0,null,"Datos",ACTOR);String section=drafts.get(v).sections().getFirst().id();
        for(int i=1;i<=2;i++)drafts.question(v,i,section,new Question("q"+i,"Datos","Pregunta "+i,"radio",false,List.of(new Question.Option("SI","Sí"),new Question.Option("NO_SABE","No sabe")),null,null,0,1,"",null,null),true,ACTOR);
        drafts.question(v,3,section,new Question("duracion","Datos","Duración","number",false,List.of(),null,null,0,1,"",0,100),true,ACTOR);
        drafts.question(v,4,section,new Question("contacto","Datos","Correo de contacto","email",false,List.of(),null,null,0,1,"",null,null),true,ACTOR);publications.publish(v,5,ACTOR);configure(v,"meses");return v;}
    void configure(String v,String unit){preparation.configure(v,0,List.of(new GenericPreparationService.Field("q1","NOMINAL",""),new GenericPreparationService.Field("q2","NOMINAL",""),new GenericPreparationService.Field("duracion","MAGNITUD_ENTERA",unit),new GenericPreparationService.Field("contacto","EXCLUIR","")),ACTOR);}
    String response(String v,String answer,int number){var values=new LinkedMultiValueMap<String,String>();values.set("consent","SI");values.set("q1",answer);values.set("q2","NO_SABE");values.set("duracion",String.valueOf(number));values.set("contacto","contacto-privado@example.test");var validation=validator.validate(publications.get(v).content().definition(),values);assertThat(validation.valid()).isTrue();return responses.savePublished(v,UUID.randomUUID().toString(),validation);}
    String slug(String v){return publications.get(v).slug();}
    String path(String v){return "/admin/espacio/"+slug(v)+"/resultados";}
    @Test void manualDictionaryFeedValidatesBeforeSavingAndIsAvailableFromAdminFooter()throws Exception{
        mvc.perform(get("/admin/diccionario")).andExpect(status().isOk()).andExpect(content().string(containsString("Generar prompt"))).andExpect(content().string(containsString("name=\"_csrf\""))).andExpect(content().string(containsString("Alimentar diccionario")));
        mvc.perform(get("/admin/diccionario").with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/diccionario/alimentar").with(csrf()).param("incoming","Formato inválido")).andExpect(status().isBadRequest());assertThat(dictionaries.global().revision()).isZero();
        var preview=json(mvc.perform(post("/admin/diccionario/alimentar").with(csrf()).param("incoming","Inteligencia Artificial=IA|inteligencia artificial")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));String rules=preview.get("rules").toString();assertThat(preview.get("groupsAfter")).isEqualTo(1);assertThat(dictionaries.global().revision()).isZero();
        var fixture=java.nio.file.Path.of(".codex-tmp/text-mentions-browser");java.nio.file.Files.createDirectories(fixture);java.nio.file.Files.writeString(fixture.resolve("feed-ai.json"),CanonicalJson.json(preview));java.nio.file.Files.writeString(fixture.resolve("global.html"),mvc.perform(get("/admin/diccionario")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(post("/admin/diccionario/guardar").with(csrf()).param("rules",rules)).andExpect(status().isBadRequest());
        mvc.perform(post("/admin/diccionario/guardar").param("rules",rules).param("reviewed","true")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/diccionario/guardar").with(csrf()).param("rules",rules).param("reviewed","true")).andExpect(status().isOk());assertThat(dictionaries.global().revision()).isEqualTo(1);org.mockito.Mockito.verifyNoInteractions(wikidataRemote);
        mvc.perform(post("/admin/diccionario/guardar").with(csrf()).param("rules",rules).param("reviewed","true")).andExpect(status().isConflict());
    }
    @Test void ambiguousLookupDoesNotSelectIowaAndOldUnsafeSuggestionsAreDiscarded(){
        var remote=org.mockito.Mockito.mock(WikidataTerms.class);org.mockito.Mockito.when(remote.search("ia")).thenReturn(List.of(Map.of("id","Q1546","label","Iowa","matched","IA","url","https://www.wikidata.org/wiki/Q1546"),Map.of("id","Q11660","label","inteligencia artificial","matched","IA","url","https://www.wikidata.org/wiki/Q11660")));var lookup=new UniversalTermLookup(jdbc,remote,dictionaries);var found=lookup.lookup("IA");assertThat(found.get("status")).isEqualTo("AMBIGUO");assertThat(found).doesNotContainKeys("aliases","group");org.mockito.Mockito.verify(remote,org.mockito.Mockito.never()).aliases(org.mockito.ArgumentMatchers.anyString());
        var legacy=Map.of("term","artificial","status","REVISAR","group","pelicula","aliases",List.of("pelicula"),"candidates",List.of());jdbc.update("INSERT INTO universal_term_lookup(term_key,term,payload_json,payload_hash,created_at) VALUES(?,?,?,?,?)",CanonicalJson.hash("artificial"),"artificial",CanonicalJson.json(legacy),CanonicalJson.hash(legacy),OffsetDateTime.now());assertThat(lookup.lookup("artificial")).doesNotContainKeys("aliases","group");
    }
    @Test void externalLookupIsDisabledAndDoesNotMarkTheTermAsProcessed()throws Exception{String v=publish();var session=new MockHttpSession();var payload=load(v,session,false);org.mockito.Mockito.when(wikidataRemote.search("bloqueado")).thenThrow(new WikidataTerms.RateLimited(60));mvc.perform(post(path(v)+"/sugerencias/consultar").session(session).with(csrf()).param("frameId",payload.get("frameId").toString()).param("questionId","q1").param("term","bloqueado")).andExpect(status().isBadRequest());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM universal_term_lookup WHERE term_key=?",Integer.class,CanonicalJson.hash("bloqueado"))).isZero();}
    @Test void universalDictionaryAndLookupAreReusedAcrossSurveysAndSurviveSurveyDeletion(){
        String v=publish();var frame=results.load(slug(v),List.of(v),"","",ACTOR);dictionaries.save(frame,"q1",0,"IA=IA|Inteligencia Artificial",ACTOR);
        String second=publish();var other=results.load(slug(second),List.of(second),"","",ACTOR);assertThat(dictionaries.get(other,"q2").rules()).contains("inteligencia artificial");
        var remote=org.mockito.Mockito.mock(WikidataTerms.class);org.mockito.Mockito.when(remote.search("analisis de datos")).thenReturn(List.of());var lookup=new UniversalTermLookup(jdbc,remote,dictionaries);
        assertThat(lookup.lookup("IA").get("status")).isEqualTo("ESTABLECIDO");assertThat(lookup.lookup("Análisis de datos").get("cached")).isEqualTo(false);assertThat(lookup.lookup("ANALISIS DE DATOS").get("cached")).isEqualTo(true);org.mockito.Mockito.verify(remote,org.mockito.Mockito.times(1)).search("analisis de datos");org.mockito.Mockito.verifyNoMoreInteractions(remote);
        org.mockito.Mockito.when(remote.search("nuevo termino")).thenThrow(new IllegalStateException("Sin conexión")).thenReturn(List.of());assertThatThrownBy(()->lookup.lookup("nuevo termino")).isInstanceOf(IllegalStateException.class);assertThat(lookup.lookup("nuevo termino").get("cached")).isEqualTo(false);assertThat(lookup.lookup("nuevo termino").get("cached")).isEqualTo(true);org.mockito.Mockito.verify(remote,org.mockito.Mockito.times(2)).search("nuevo termino");
        String deletedSlug=slug(v);var backup=deletion.prepare(deletedSlug,management.get(deletedSlug).hash(),ACTOR);deletion.delete(deletedSlug,backup.receipt().id(),backup.receipt().stateHash(),ACTOR);assertThat(dictionaries.get(other,"q2").revision()).isEqualTo(1);assertThat(lookup.lookup("analisis de datos").get("cached")).isEqualTo(true);
    }
    @Test void previousQuestionDictionariesRemainAvailableInTheUniversalDictionary(){String v=publish();var frame=results.load(slug(v),List.of(v),"","",ACTOR);String rules="IA=IA|Inteligencia Artificial";var question=((List<Map<String,Object>>)frame.logical().get("dictionary")).getFirst();jdbc.update("INSERT INTO question_text_dictionary(id,version_id,question_key,definition_hash,revision,rules_text,rules_hash,actor,created_at) VALUES(?,?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),v,"q1",CanonicalJson.hash(question),1,rules,CanonicalJson.hash(rules),ACTOR,OffsetDateTime.now());var global=dictionaries.get(frame,"q2");assertThat(global.rules()).contains("inteligencia artificial");assertThat(TextMentions.parse(global.rules())).hasSize(1);dictionaries.save(frame,"q2",0,global.rules(),ACTOR);assertThat(dictionaries.global().revision()).isEqualTo(1);}
    @Test void individualAnswersAreScopedProtectedEscapedAndTextFrequenciesUseExactMatches() throws Exception {
        String v=drafts.create("Texto de prueba","Ficticio",ACTOR);drafts.section(v,0,null,"Datos",ACTOR);String section=drafts.get(v).sections().getFirst().id();
        drafts.question(v,1,section,new Question("comentario","Datos","Opinión libre","textarea",false,List.of(),null,null,0,1,"",null,null),true,ACTOR);
        publications.publish(v,2,ACTOR);
        for(String text:List.of("<script>texto</script>","<script>texto</script>","Texto diferente")){var values=new LinkedMultiValueMap<String,String>();values.set("consent","SI");values.set("comentario",text);responses.savePublished(v,UUID.randomUUID().toString(),validator.validate(publications.get(v).content().definition(),values));}
        var session=new MockHttpSession();var payload=load(v,session,false);String token=payload.get("frameId").toString();
        assertThat(payload.get("html").toString()).contains("data-question-answers").doesNotContain("<script>texto</script>");
        var answer=json(mvc.perform(get(path(v)+"/respuestas-pregunta").session(session).param("frameId",token).param("questionId","comentario")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat((List<?>)answer.get("rows")).hasSize(3);assertThat((List<?>)answer.get("distribution")).hasSize(2);assertThat(answer.get("base")).isEqualTo(3);
        mvc.perform(get(path(v)+"/pregunta").session(session).param("frameId",token).param("questionId","comentario")).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;texto&lt;/script&gt;"))).andExpect(content().string(containsString("Ver envío")));
        var custom=json(mvc.perform(get(path(v)+"/respuestas-pregunta").session(session).param("frameId",token).param("questionId","comentario").param("keywordRules","Texto=texto|diferente")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(((Map<?,?>)custom.get("keywords")).get("matched")).isEqualTo(3);
        var browser=java.nio.file.Path.of(".codex-tmp/text-mentions-browser");java.nio.file.Files.createDirectories(browser);
        java.nio.file.Files.writeString(browser.resolve("before.json"),CanonicalJson.json(payload));java.nio.file.Files.writeString(browser.resolve("answers.json"),CanonicalJson.json(answer));java.nio.file.Files.writeString(browser.resolve("custom.json"),CanonicalJson.json(custom));
        java.nio.file.Files.writeString(browser.resolve("total.html"),mvc.perform(get(path(v)).session(session).param("version",publications.get(v).code())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(browser.resolve("question.html"),mvc.perform(get(path(v)+"/pregunta").session(session).param("frameId",token).param("questionId","comentario")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(browser.resolve("manager.html"),mvc.perform(get(path(v)+"/sugerencias").session(session).param("frameId",token).param("questionId","comentario")).andExpect(status().isOk()).andExpect(content().string(containsString("Extraer sugerencias"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(browser.resolve("feed-text.json"),CanonicalJson.json(dictionaries.importPreview("","Texto=texto|diferente")));
        String endpoint=path(v)+"/sugerencias/";
        var extract=json(mvc.perform(post(endpoint+"extraer").session(session).with(csrf()).param("frameId",token).param("questionId","comentario")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat((List<?>)extract.get("terms")).isNotEmpty();java.nio.file.Files.writeString(browser.resolve("extract.json"),CanonicalJson.json(extract));
        var preview=json(mvc.perform(post(endpoint+"previsualizar").session(session).with(csrf()).param("frameId",token).param("questionId","comentario").param("rules","Texto=texto|diferente")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(preview.toString()).contains("matches","start","end");java.nio.file.Files.writeString(browser.resolve("preview.json"),CanonicalJson.json(preview));
        mvc.perform(post(endpoint+"guardar").session(session).param("frameId",token).param("questionId","comentario").param("rules","Texto=texto").param("term","true")).andExpect(status().isForbidden());
        mvc.perform(post(endpoint+"guardar").session(session).with(csrf()).param("frameId",token).param("questionId","comentario").param("rules","Texto=texto")).andExpect(status().isBadRequest());
        mvc.perform(post(endpoint+"guardar").session(session).with(csrf()).param("frameId",token).param("questionId","comentario").param("rules","Texto=texto|diferente").param("term","true")).andExpect(status().isOk());
        mvc.perform(post(endpoint+"guardar").session(session).with(csrf()).param("frameId",token).param("questionId","comentario").param("rules","Texto=texto").param("term","true")).andExpect(status().isConflict());
        var reloaded=results.questionAnswers(results.load(slug(v),List.of(v),"","",ACTOR),"comentario",ACTOR);assertThat(reloaded.get("dictionaryRevision")).isEqualTo(1);assertThat(((Map<?,?>)reloaded.get("keywords")).get("matched")).isEqualTo(3);
        var reportFrame=results.load(slug(v),List.of(v),"","",ACTOR);String textAnalysis=results.materialize(reportFrame,ACTOR);
        var textConfig=new ReportCustomization.Configuration(textAnalysis,"Coincidencias ficticias","",true,true,true,"",List.of(new ReportCustomization.Question("comentario",true,"Opinión libre","",List.of("text-table","text-bars","text-pie","text-donut"))),List.of());
        var textPreview=reports.preview(textConfig,ACTOR);
        var textVariable=((List<Map<String,Object>>)((Map<?,?>)textPreview.get("display_result")).get("variables")).getFirst();
        assertThat(((Map<?,?>)textVariable.get("text_counts")).get("matched")).isEqualTo(3);
        assertThat(CanonicalJson.json(textPreview)).doesNotContain("<script>","Texto diferente");
        String textReport=reports.generateCustomized(textConfig,CanonicalJson.hash(textPreview),ACTOR);
        String legacyTextReport=reports.generateCustomized(textConfig,CanonicalJson.hash(textPreview),ACTOR);
        var legacyText=json(CanonicalJson.json(textPreview));legacyText.remove("text_projection_version");for(var variable: variables((Map<String,Object>)legacyText.get("display_result"))){variable.remove("text_available");variable.remove("sensitive_text");}
        jdbc.update("UPDATE generated_report SET content_json=?,content_hash=? WHERE id=?",CanonicalJson.json(legacyText),CanonicalJson.hash(legacyText),legacyTextReport);assertThat(reports.get(legacyTextReport)).isNotEmpty();
        var originalConfig=new ReportCustomization.Configuration(textAnalysis,"Textos ficticios","",true,true,true,"",List.of(new ReportCustomization.Question("comentario",true,"Opinión libre","",List.of("text-content"))),List.of());
        var originalPreview=reports.preview(originalConfig,ACTOR);
        assertThat(CanonicalJson.json(originalPreview)).contains("Texto diferente");
        String originalReport=reports.generateCustomized(originalConfig,CanonicalJson.hash(originalPreview),ACTOR);
        mvc.perform(get("/admin/reportes/"+originalReport)).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;texto&lt;/script&gt;"))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("<script>texto</script>"))));
        mvc.perform(get("/admin/diccionario").param("analysisId",textAnalysis).param("questionId","comentario")).andExpect(status().isOk()).andExpect(content().string(containsString("data-initial-terms"))).andExpect(content().string(containsString("Opinión libre")));
        mvc.perform(get("/admin/diccionario").param("analysisId",textAnalysis).param("questionId","otra")).andExpect(status().isBadRequest());
        mvc.perform(get("/admin/diccionario").param("analysisId",textAnalysis).param("questionId","comentario").with(anonymous())).andExpect(status().is3xxRedirection());
        var reportBrowser=java.nio.file.Path.of(".codex-tmp/report-text-browser");java.nio.file.Files.createDirectories(reportBrowser);
        java.nio.file.Files.writeString(reportBrowser.resolve("generator.html"),mvc.perform(get("/admin/reportes/nuevo").param("analysisId",textAnalysis)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(reportBrowser.resolve("preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(textConfig))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        java.nio.file.Files.writeString(reportBrowser.resolve("report.json"),CanonicalJson.json(textPreview));
        dictionaries.saveGlobal(1,"otro=otro",ACTOR);
        assertThat(CanonicalJson.hash(reports.get(textReport).get("content"))).isEqualTo(CanonicalJson.hash(textPreview));
        assertThat(CanonicalJson.hash(reports.get(originalReport).get("content"))).isEqualTo(CanonicalJson.hash(originalPreview));
        java.nio.file.Files.writeString(reportBrowser.resolve("report.html"),mvc.perform(get("/admin/reportes/"+textReport)).andExpect(status().isOk()).andExpect(content().string(containsString("Coincidencias en respuestas abiertas"))).andExpect(content().string(containsString("text-donut"))).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get(path(v)+"/respuestas-pregunta").session(session).param("frameId",token).param("questionId","otra")).andExpect(status().isBadRequest());
        mvc.perform(get(path(v)+"/respuestas-pregunta").param("frameId",token).param("questionId","comentario")).andExpect(status().isConflict());
        mvc.perform(get(path(v)+"/respuestas-pregunta").with(anonymous()).param("frameId",token).param("questionId","comentario")).andExpect(status().is3xxRedirection());
        String other=publish();response(other,"SI",2);var snapshot=results.load(slug(other),List.of(other),"","",ACTOR);assertThat(results.questionAnswers(snapshot,"contacto",ACTOR).get("groupable")).isEqualTo(false);
        var backup=deletion.prepare(slug(v),management.get(slug(v)).hash(),ACTOR);String deletedSlug=slug(v);deletion.delete(deletedSlug,backup.receipt().id(),backup.receipt().stateHash(),ACTOR);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM question_text_dictionary WHERE version_id=?",Integer.class,v)).isZero();
    }
    @Test void renderSyntheticBrowserFixtures() throws Exception {
        String v=publish();response(v,"SI",2);var session=new MockHttpSession();
        var dir=java.nio.file.Path.of(".codex-tmp/workspace-results-browser");java.nio.file.Files.createDirectories(dir);
        var before=load(v,session,false);
        assertThat(before.get("html").toString()).contains("<th>Mínimo</th><td>2.00</td>","<th>Media</th><td>2.00</td>");
        String page=mvc.perform(get("/admin/espacio/"+slug(v)+"/resultados").session(session).param("version",publications.get(v).code())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(dir.resolve("total.html"),page);
        java.nio.file.Files.writeString(dir.resolve("before.json"),CanonicalJson.json(before));response(v,"NO_SABE",6);
        java.nio.file.Files.writeString(dir.resolve("after.json"),CanonicalJson.json(load(v,session,true)));
        java.nio.file.Files.writeString(dir.resolve("answers.json"),CanonicalJson.json(results.questionAnswers(results.load(slug(v),List.of(v),"","",ACTOR),"q1",ACTOR)));
    }
    @SuppressWarnings("unchecked") Map<String,Object> json(String body){return CanonicalJson.read(body,Map.class);}
    @SuppressWarnings("unchecked") List<Map<String,Object>> variables(Map<String,Object> result){return (List<Map<String,Object>>)result.get("variables");}
    Map<String,Object> result(Map<String,Object> payload){return (Map<String,Object>)payload.get("result");}
    Map<String,Object> load(String v,MockHttpSession session,boolean refresh)throws Exception{return json(mvc.perform(get(path(v)+"/datos").session(session).param("version",publications.get(v).code()).param("refresh",String.valueOf(refresh))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
    @Test void totalIncludesEveryOriginKeepsStrataNoSabeCoverageAndDeclaredNumbers(){String v=publish();String first=response(v,"SI",2);wrangling.classify(first,"REAL","Origen ficticio revisado",ACTOR);String second=response(v,"NO_SABE",4);wrangling.classify(second,"SINTETICO","Prueba",ACTOR);response(v,"SI",6);
        var frame=results.load(slug(v),List.of(v),"","",ACTOR);assertThat(frame.result().get("submission_count")).isEqualTo(3);assertThat((List<?>)frame.result().get("strata")).hasSize(3);
        var contact=variables(frame.result()).stream().filter(q->q.get("question_id").equals("contacto")).findFirst().orElseThrow();assertThat(contact.get("coverage_only")).isEqualTo(true);assertThat(((List<?>)contact.get("distribution"))).hasSize(3);
        assertThat(CanonicalJson.json(frame)).doesNotContain("contacto-privado@example.test");var numeric=variables(frame.result()).stream().filter(q->q.get("question_id").equals("duracion")).findFirst().orElseThrow();assertThat(((Map<?,?>)numeric.get("numeric")).get("mean")).isEqualTo(4.0);
        assertThat(variables(frame.result()).getFirst().toString()).contains("NO_SABE");assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_result_cut",Integer.class)).isZero();
    }
    @Test void sameSessionRetainsTheLoadUntilExplicitRefreshAndNewSessionSeesNewData()throws Exception{String v=publish();response(v,"SI",2);var session=new MockHttpSession();var first=load(v,session,false);response(v,"SI",3);
        var cached=load(v,session,false);assertThat(cached.get("frameId")).isEqualTo(first.get("frameId"));assertThat(result(cached).get("submission_count")).isEqualTo(1);
        var refreshed=load(v,session,true);assertThat(result(refreshed).get("submission_count")).isEqualTo(2);assertThat(refreshed.get("frameId")).isNotEqualTo(first.get("frameId"));
        response(v,"SI",4);assertThat(result(load(v,session,false)).get("submission_count")).isEqualTo(2);assertThat(result(load(v,new MockHttpSession(),false)).get("submission_count")).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_run",Integer.class)).isZero();
    }
    @Test void inclusiveDatesUseReceptionInBoliviaAndNotTheExternalSourceDate(){String v=publish();String id=response(v,"SI",1);LocalDate day=jdbc.queryForObject("SELECT submitted_at FROM survey_response WHERE id=?",OffsetDateTime.class,id).atZoneSameInstant(ZoneId.of("America/La_Paz")).toLocalDate();
        assertThat(results.load(slug(v),List.of(v),day.toString(),day.toString(),ACTOR).result().get("submission_count")).isEqualTo(1);assertThat(results.load(slug(v),List.of(v),day.plusDays(1).toString(),"",ACTOR).result().get("submission_count")).isEqualTo(0);
        assertThatThrownBy(()->results.load(slug(v),List.of(v),day.plusDays(1).toString(),day.toString(),ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void multiVersionCutsMergeExactQuestionsAndSeparateChangedScalesAndUnits(){String first=publish();response(first,"SI",2);String next=publications.successor(first,ACTOR);var draft=drafts.get(next);var item=draft.items().getFirst();var q=item.question();var options=new ArrayList<>(q.options());options.add(new Question.Option("OTRA","Otra"));drafts.question(next,0,item.sectionId(),new Question(q.id(),q.section(),q.label(),q.type(),q.required(),options,q.dependsOn(),q.dependsValue(),0,1,q.hint(),null,null),false,ACTOR);publications.publish(next,1,ACTOR);configure(next,"horas");response(next,"OTRA",8);
        var frame=results.load(slug(first),List.of(first,next),"","",ACTOR);var vars=variables(frame.result());assertThat(frame.result().get("submission_count")).isEqualTo(2);assertThat(vars).hasSize(6);
        assertThat(vars.stream().filter(v->v.get("label").equals("Pregunta 1"))).hasSize(2).allSatisfy(v->assertThat(v.get("total")).isEqualTo(1));assertThat(vars.stream().filter(v->v.get("label").equals("Duración"))).hasSize(2);
        assertThat(vars.stream().filter(v->v.get("label").equals("Pregunta 2"))).singleElement().satisfies(v->assertThat(v.get("total")).isEqualTo(2));
        var firstOnly=results.load(slug(first),List.of(first),"","",ACTOR);assertThat(firstOnly.result().get("submission_count")).isEqualTo(1);assertThatThrownBy(()->results.load(slug(first),List.of(first,publish()),"","",ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void totalReportUsesDisplayedSnapshotAndMultipleRepresentationsWithoutCreatingACut()throws Exception{String v=publish();response(v,"SI",2);var session=new MockHttpSession();var displayed=load(v,session,false);response(v,"SI",3);
        assertThat(result(load(v,session,true)).get("submission_count")).isEqualTo(2);
        var result=mvc.perform(post(path(v)+"/reporte-total").session(session).with(csrf()).param("version",publications.get(v).code()).param("frameId",displayed.get("frameId").toString()).param("representations","{\"q1\":[\"table\",\"columns\",\"pie\"]}"))
                .andExpect(status().is3xxRedirection()).andReturn();String url=result.getResponse().getRedirectedUrl(),analysisId=url.substring(url.indexOf("analysisId=")+11);assertThat(((Map<?,?>)analyses.get(analysisId).get("result")).get("submission_count")).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_result_cut",Integer.class)).isZero();
        var generated=mvc.perform(get(url).session(session)).andExpect(status().isOk()).andExpect(content().string(containsString("report-customizer"))).andReturn();assertThat(generated.getResponse().getContentAsString()).contains("pie");
        @SuppressWarnings("unchecked") var prefs=(Map<String,ReportCustomization.Configuration>)session.getAttribute("workspaceReportPreferences");var config=prefs.get(analysisId);var preview=reports.preview(config,ACTOR);String saved=reports.generateCustomized(config,CanonicalJson.hash(preview),ACTOR);assertThat(reports.get(saved)).isNotEmpty();
    }
    @Test void cutCanBeRefreshedAsANewCutAndDeletedWithItsProductsWithoutDeletingAnswers()throws Exception{String v=publish();String original=response(v,"SI",2);var session=new MockHttpSession();var displayed=load(v,session,false);var posted=mvc.perform(post(path(v)+"/cortes").session(session).with(csrf()).param("frameId",displayed.get("frameId").toString()).param("name","Periodo ficticio")).andExpect(status().isOk()).andReturn();String cutUrl=json(posted.getResponse().getContentAsString()).get("url").toString(),cutId=cutUrl.substring(cutUrl.lastIndexOf('/')+1);
        mvc.perform(get(cutUrl)).andExpect(status().isOk()).andExpect(content().string(containsString("Eliminar corte")));var saved=results.cut(slug(v),cutId);response(v,"SI",3);
        var frozen=json(mvc.perform(get(path(v)+"/datos").session(session).param("version",publications.get(v).code()).param("cutId",cutId)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(result(frozen).get("submission_count")).isEqualTo(1);
        var refreshed=json(mvc.perform(get(path(v)+"/datos").session(session).param("version",publications.get(v).code()).param("cutId",cutId).param("refresh","true")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(result(refreshed).get("submission_count")).isEqualTo(2);assertThat(results.cut(slug(v),cutId).snapshot().result().get("submission_count")).isEqualTo(1);
        String newId=results.save(results.load(slug(v),List.of(v),"","",ACTOR),"Actualizado",ACTOR);assertThat(newId).isNotEqualTo(cutId);
        String analysis=results.materialize(saved.snapshot(),ACTOR),cross=crosses.create(saved.snapshot().id(),new GenericCrossService.Spec("q1","q2",.05,false,"","",false),ACTOR);String report=reports.generateDirect(analysis,"Informe ficticio",List.of(cross),"",ACTOR);
        var state=results.cutState(slug(v),cutId);assertThat(state.reports()).isEqualTo(1);mvc.perform(post(path(v)+"/cortes/"+cutId+"/eliminar").with(csrf()).param("expectedHash",state.hash())).andExpect(status().isBadRequest());
        mvc.perform(post(path(v)+"/cortes/"+cutId+"/eliminar").with(csrf()).param("expectedHash",state.hash()).param("confirm","true")).andExpect(status().is3xxRedirection());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report WHERE id=?",Integer.class,report)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE id=?",Integer.class,original)).isEqualTo(1);assertThat(results.cut(slug(v),newId)).isNotNull();
    }
    @Test void cutDeletionRejectsChangedProductsAndTotalsCannotDeclareIndependence(){String v=publish();response(v,"SI",2);var f=results.load(slug(v),List.of(v),"","",ACTOR);String cut=results.save(f,"Corte",ACTOR);var state=results.cutState(slug(v),cut);String analysis=results.materialize(f,ACTOR);reports.generateDirect(analysis,"Nuevo producto",List.of(),"",ACTOR);
        assertThatThrownBy(()->results.deleteCut(slug(v),cut,state.hash(),ACTOR)).isInstanceOf(ResponseStatusException.class);assertThat(results.cut(slug(v),cut)).isNotNull();assertThatThrownBy(()->crosses.create(f.id(),new GenericCrossService.Spec("q1","q2",.05,true,"Un envío","No acredita independencia",false),ACTOR)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void allVersionsCutIncludesNewPublicationsOnlyWhenExplicitlyRefreshed()throws Exception{String first=publish();response(first,"SI",1);String second=publications.successor(first,ACTOR);publications.publish(second,0,ACTOR);configure(second,"meses");response(second,"SI",2);String cut=results.save(results.load(slug(first),List.of(first,second),"","",ACTOR,true),"Todas",ACTOR);String third=publications.successor(second,ACTOR);publications.publish(third,0,ACTOR);configure(third,"meses");response(third,"SI",3);var session=new MockHttpSession();var frozen=json(mvc.perform(get(path(first)+"/datos").session(session).param("version","v1").param("cutId",cut)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(result(frozen).get("submission_count")).isEqualTo(2);var updated=json(mvc.perform(get(path(first)+"/datos").session(session).param("version","v1").param("cutId",cut).param("refresh","true")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());assertThat(result(updated).get("submission_count")).isEqualTo(3);assertThat(results.cut(slug(first),cut).snapshot().result().get("submission_count")).isEqualTo(2);}
    @Test void surveyBackupIncludesNewCutsAndCompleteDeletionHandlesTheirForeignKeys(){String v=publish(),slug=slug(v);response(v,"SI",2);String cut=results.save(results.load(slug,List.of(v),"","",ACTOR),"Corte para respaldo",ACTOR);var backup=deletion.prepare(slug,management.get(slug).hash(),ACTOR);deletion.delete(slug,backup.receipt().id(),backup.receipt().stateHash(),ACTOR);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_result_cut WHERE id=?",Integer.class,cut)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey WHERE slug=?",Integer.class,slug)).isZero();}
    @Test void eachSuccessfulLoginClearsThePreviousLoadsEvenIfTheSessionAttributesWereMigrated()throws Exception{String v=publish();response(v,"SI",2);var session=new MockHttpSession();load(v,session,false);assertThat(session.getAttribute("workspaceResultsCache")).isNotNull();response(v,"SI",3);jdbc.update("DELETE FROM administrator");administrators.register("session.admin","Clave-ficticia-2026");var logged=mvc.perform(post("/login").session(session).with(anonymous()).with(csrf()).param("username","session.admin").param("password","Clave-ficticia-2026")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin")).andReturn();var fresh=(MockHttpSession)logged.getRequest().getSession(false);assertThat(fresh.getAttribute("workspaceResultsCache")).isNull();assertThat(result(load(v,fresh,false)).get("submission_count")).isEqualTo(2);}
    @Test void invalidReportRepresentationsAreRejectedBeforePersistingAnyProducts()throws Exception{String v=publish();response(v,"SI",2);var session=new MockHttpSession();var data=load(v,session,false);mvc.perform(post(path(v)+"/reporte-total").session(session).with(csrf()).param("version",publications.get(v).code()).param("frameId",data.get("frameId").toString()).param("representations","{\"q1\":[\"scatter\"]}")).andExpect(status().isBadRequest());assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_run",Integer.class)).isZero();}
    @Test void navigationUnifiesResultsAndProtectedActionsRequireAdminAndCsrf()throws Exception{String v=publish();response(v,"SI",2);var page=mvc.perform(get(path(v)).param("version",publications.get(v).code())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);assertThat(page).contains("Actualizar todos","Ver envíos","Reporte del total actual").doesNotContain("Reportes y plantillas","Actualización automática");mvc.perform(get(path(v)+"/cortes/nuevo").param("version",publications.get(v).code()).param("allVersions","true")).andExpect(status().isOk());
        mvc.perform(get(path(v)+"/datos").param("version",publications.get(v).code()).with(anonymous())).andExpect(status().is3xxRedirection());mvc.perform(get(path(v)+"/datos").param("version",publications.get(v).code()).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        for(String endpoint:List.of("/cortes","/reporte-total","/cortes/fake/eliminar"))mvc.perform(post(path(v)+endpoint).param("frameId","fake").param("version",publications.get(v).code())).andExpect(status().isForbidden());
    }
}

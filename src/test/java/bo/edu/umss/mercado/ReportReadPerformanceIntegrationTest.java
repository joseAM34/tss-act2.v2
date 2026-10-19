package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.*;
import bo.edu.umss.mercado.importing.CsvImportService;
import bo.edu.umss.mercado.reporting.ReportTextProjection;
import bo.edu.umss.mercado.statistics.TextDictionaryService;
import bo.edu.umss.mercado.survey.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:report-read-performance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@ActiveProfiles("test") @Transactional
class ReportReadPerformanceIntegrationTest {
    @Autowired CaptureStore captures;@Autowired DraftSurveyService drafts;@Autowired SurveyPublicationService publications;@Autowired CsvImportService csv;@Autowired TextDictionaryService dictionaries;@Autowired ReportTextProjection projection;
    @MockitoSpyBean JdbcTemplate jdbc;
    String publish(){String v=drafts.create("Lectura agrupada ficticia","Prueba", "admin");drafts.section(v,0,null,"Datos","admin");String section=drafts.get(v).sections().getFirst().id();drafts.question(v,1,section,new Question("opinion","Datos","Opinión","textarea",false,List.of(),null,null,0,1,"",null,null),true,"admin");publications.publish(v,2,"admin");return v;}
    List<String> imported(String version,int size){var source=new StringBuilder("consent,opinion\n");for(int i=0;i<size;i++)source.append("SI,IA respuesta ficticia ").append(i).append('\n');String batch=csv.upload(version,"ficticio.csv",source.toString().getBytes(StandardCharsets.UTF_8),"COMA","GENERICO","admin");var mapping=new CsvImportService.Mapping(List.of(new CsvImportService.Column("opinion",1)),0,"SI",-1,-1,"AUTO","AUTO",true,"Se revisó el formulario ficticio de prueba.","La fuente ficticia contiene aceptación académica.");String revision=csv.mapColumns(batch,0,mapping,"admin");var preview=csv.preview(batch,revision);assertThat(preview.valid()).isTrue();return csv.confirm(batch,revision,preview.hash(),true,"admin");}
    long listQueries(){return mockingDetails(jdbc).getInvocations().stream().filter(i->i.getMethod().getName().equals("queryForList")).count();}
    @Test void exactMembersAcrossVersionsKeepAllChecksWithFewerDatabaseReads(){
        String a=publish(),b=publish();var ids=new ArrayList<>(imported(a,12));ids.addAll(imported(b,8));
        clearInvocations(jdbc);var expected=ids.stream().map(captures::input).toList();long individual=listQueries();
        clearInvocations(jdbc);var grouped=captures.inputs(ids);long batch=listQueries();
        assertThat(grouped).isEqualTo(expected);assertThat(batch).isLessThanOrEqualTo(10).isLessThan(individual/3);
        System.out.println("Capture reads: individual="+individual+", grouped="+batch+", submissions="+ids.size());
        jdbc.update("UPDATE response_capture SET capture_hash='alterado' WHERE response_id=?",ids.getFirst());
        assertThatThrownBy(()->captures.inputs(ids)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->captures.input(ids.getFirst())).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE response_capture SET capture_hash=? WHERE response_id=?",expected.getFirst().captureHash(),ids.getFirst());
        assertThat(captures.inputs(ids)).isEqualTo(expected);
        jdbc.update("UPDATE csv_import_batch SET source_hash='alterado' WHERE id=(SELECT batch_id FROM csv_import_row WHERE response_id=?)",ids.getLast());
        assertThatThrownBy(()->captures.inputs(ids)).isInstanceOf(IllegalStateException.class);
        assertThat(captures.input(ids.getFirst())).isEqualTo(expected.getFirst());
    }
    @Test void textProjectionReadsOnceAndChecksFrozenHashes(){
        String v=publish();var ids=imported(v,20);var inputs=captures.inputs(ids);var members=new ArrayList<Map<String,Object>>();var matrix=new ArrayList<Map<String,Object>>();
        for(var input:inputs){members.add(new LinkedHashMap<>(Map.of("response_id",input.responseId(),"capture_hash",input.captureHash())));matrix.add(Map.of("response_id",input.responseId(),"states",Map.of("opinion","CON_VALOR")));}
        var question=Map.<String,Object>of("question_id","opinion","original_id","opinion","original_type","textarea","label","Opinión");
        var logical=Map.<String,Object>of("dictionary",List.of(question),"members",members,"matrix",matrix);var result=Map.<String,Object>of("variables",List.of(Map.of("question_id","opinion")));
        clearInvocations(jdbc);var enriched=projection.enrich(result,logical,"inteligencia artificial=ia|inteligencia artificial",1,Set.of("opinion"));long reads=listQueries();
        assertThat(reads).isLessThanOrEqualTo(7);assertThat(CanonicalJson.json(enriched)).contains("\"count\":20","IA respuesta ficticia 0");
        members.getFirst().put("capture_hash","alterado");assertThatThrownBy(()->projection.enrich(result,logical,"",1)).isInstanceOf(IllegalStateException.class);
    }
    @Test void dictionarySelectionsAreBatchedAndKeepPrivateOverrides(){
        String v=publish();String survey=publications.get(v).content().surveyId();var dictionary=new ArrayList<Map<String,Object>>();for(int i=0;i<40;i++)dictionary.add(Map.of("question_id","q"+i,"original_id","q"+i,"original_type","textarea","label","Pregunta "+i));
        var logical=Map.<String,Object>of("survey_id",survey,"dictionary",dictionary);
        dictionaries.saveGlobal(0,"inteligencia artificial=ia|inteligencia artificial\ndos años=2|dos años","admin");dictionaries.saveScoped(logical,"q0","PRIVATE",0,0,"dos años=2|dos años","admin");
        var expected=new LinkedHashMap<String,Map<String,Object>>();for(var q:dictionary){String key=(String)q.get("question_id");var editor=dictionaries.editor(logical,key,"");String rules=(String)editor.get("rules");if("GLOBAL".equals(editor.get("dictionaryScope")))rules=TextDictionaryService.globalRules(rules);expected.put(key,Map.of("scope",editor.get("dictionaryScope"),"revision",editor.get("revision"),"rules",rules,"hash",CanonicalJson.hash(rules)));}
        clearInvocations(jdbc);assertThat(dictionaries.selections(logical)).isEqualTo(expected);assertThat(listQueries()).isEqualTo(2);
        assertThat(dictionaries.selections(logical).get("q0").get("scope")).isEqualTo("PRIVATE");
        assertThat(dictionaries.selections(logical).get("q0").get("rules").toString()).contains("|2").doesNotContain("inteligencia artificial");
    }
}

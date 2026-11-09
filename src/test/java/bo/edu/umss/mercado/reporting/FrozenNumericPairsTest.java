package bo.edu.umss.mercado.reporting;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import bo.edu.umss.mercado.data.*;
import bo.edu.umss.mercado.statistics.NumericNormalization;
class FrozenNumericPairsTest {
 @Test void pairsFrozenConversionsBySubmissionAndExcludesPendingWithoutMutatingSource(){
  var store=mock(CaptureStore.class);
  var definition=NumericNormalization.generic(true,"Año","1.5=18 meses","Año","Meses");
  var selection=Map.<String,Map<String,Object>>of("y",Map.of("numeric",Map.of("definition",definition,"hash",CanonicalJson.hash(CanonicalJson.json(definition)))));
  var fields=List.of(Map.<String,Object>of("question_id","x","label","Edad","unit","Año","treatment","MAGNITUD_ENTERA"),Map.<String,Object>of("question_id","y","original_id","vida","original_type","text","label","Vida profesional","treatment","NOMINAL"));
  var rows=new ArrayList<Map<String,Object>>();var members=new ArrayList<Map<String,Object>>();var inputs=new ArrayList<CaptureStore.Input>();
  for(int i=0;i<2;i++){String id="r"+i;rows.add(Map.of("response_id",id,"values",Map.of("x",30+i),"states",Map.of("x","CON_VALOR","y","CON_VALOR")));members.add(Map.of("response_id",id,"capture_hash","hash"));inputs.add(new CaptureStore.Input(id,"s","v","date","study","REAL","hash","",null,Map.of("vida",List.of(i==0?"18 meses":"no sabe")),Map.of(),true));}
  when(store.inputs(anyList())).thenReturn(inputs);
  var logical=Map.<String,Object>of("dictionary",fields,"matrix",rows,"members",members);
  var projection=new ReportTextProjection(store).numericLogical(logical,selection);
  var setting=new ReportCustomization.Advanced("scatter",List.of("x","y"),"Pares","",true);
  var config=new ReportCustomization.Configuration("a","Reporte","",true,true,true,"",List.of(),List.of(),List.of(),List.of(setting));
  var chart=ReportAdvancedProjection.charts(config,projection).getFirst();
  assertThat(chart).containsEntry("n",1).containsEntry("excluded",1);
  assertThat(chart.get("points")).isEqualTo(List.of(List.of(30.,1.5,1)));
  assertThat(((Map<?,?>)rows.getFirst().get("values")).containsKey("y")).isFalse();
 }
}

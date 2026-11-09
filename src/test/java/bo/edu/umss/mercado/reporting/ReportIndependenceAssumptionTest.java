package bo.edu.umss.mercado.reporting;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import bo.edu.umss.mercado.statistics.StatisticalMath;
import bo.edu.umss.mercado.data.CanonicalJson;
class ReportIndependenceAssumptionTest {
 ReportCustomization.Configuration config(Boolean assume){return new ReportCustomization.Configuration("a","Reporte","",true,true,true,"",List.of(),List.of("c"),List.of(),List.of(),assume);}
 @Test void assumptionRunsOnlyTheReportProjectionAndDoesNotClaimVerification(){
  var table=StatisticalMath.contingency(new long[][]{{10,20},{20,10}},false,false,.05);
  var source=Map.<String,Object>of("id","c","result",Map.of("table",table));String before=CanonicalJson.json(source);
  var shown=ReportCustomization.displayCrosses(config(true),List.of(source)).getFirst();
  var derived=(Map<?,?>)((Map<?,?>)shown.get("result")).get("table");
  assertThat(derived.get("p")).isNotNull();assertThat(derived.get("independence_assumed")).isEqualTo(true);assertThat(derived.get("independence_documented")).isEqualTo(false);
  assertThat(CanonicalJson.json(source)).isEqualTo(before);
  assertThat(ReportCustomization.displayCrosses(config(false),List.of(source)).getFirst().get("result")).isEqualTo(source.get("result"));
 }
 @Test void assumptionDoesNotBypassExpectedCountsAndLegacyConfigurationsOmitTheNewFlag(){
  var table=StatisticalMath.contingency(new long[][]{{1,2},{2,1}},false,false,.05);
  var source=Map.<String,Object>of("id","c","result",Map.of("table",table));
  var derived=(Map<?,?>)((Map<?,?>)ReportCustomization.displayCrosses(config(true),List.of(source)).getFirst().get("result")).get("table");
  assertThat(derived.get("p")).isNull();assertThat(derived.get("status")).isEqualTo("FRECUENCIAS_INSUFICIENTES");
  assertThat(CanonicalJson.json(config(null))).doesNotContain("assumeIndependent");
 }
 @Test void exploratoryOverrideWarnsButNeverBypassesEmptyTablesOrStructuralZeros(){
  var result=StatisticalMath.contingency(new long[][]{{1,2},{2,1}},true,false,.05,true);
  assertThat(result).containsEntry("status","EXPLORATORIO").containsEntry("exploratory",true);assertThat(result.get("p")).isNotNull();
  assertThat(StatisticalMath.contingency(new long[][]{{0,0},{0,0}},true,false,.05,true).get("p")).isNull();
  assertThat(StatisticalMath.contingency(new long[][]{{1,2},{2,1}},true,true,.05,true).get("p")).isNull();
  var source=Map.<String,Object>of("id","c","result",Map.of("table",StatisticalMath.contingency(new long[][]{{1,2},{2,1}},false,false,.05)));
  var setting=new ReportCustomization.Configuration("a","Reporte","",true,true,true,"",List.of(),List.of("c"),List.of(),List.of(),true,true);
  var table=(Map<?,?>)((Map<?,?>)ReportCustomization.displayCrosses(setting,List.of(source)).getFirst().get("result")).get("table");
  assertThat(table.get("exploratory")).isEqualTo(true);assertThat(table.get("independence_documented")).isEqualTo(false);
 }
}

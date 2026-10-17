package bo.edu.umss.mercado.reporting;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReportAdvancedProjectionTest {
    @SuppressWarnings("unchecked") List<Map<String,Object>> list(Object value){return (List<Map<String,Object>>)value;}
    Map<String,Object> row(Map<String,Object> values,String date){return new LinkedHashMap<>(Map.of("values",values,"states",values.keySet().stream().collect(java.util.stream.Collectors.toMap(k->k,k->"CON_VALOR")),"submitted_at",date));}
    Map<String,Object> numeric(String id){return Map.of("question_id",id,"label",id,"treatment","MAGNITUD_ENTERA","type","number","unit","meses","options",List.of());}
    Map<String,Object> logical(List<Map<String,Object>> dictionary,List<Map<String,Object>> rows){return new LinkedHashMap<>(Map.of("dictionary",dictionary,"matrix",rows));}
    ReportCustomization.Configuration config(ReportCustomization.Advanced setting){return new ReportCustomization.Configuration("a","Reporte","",true,true,true,"",List.of(),List.of(),List.of(),List.of(setting));}
    @Test void histogramConservesCountsNegativeValuesAndConstantPointIntervals(){
        var histogram=ReportAdvancedProjection.histogram(List.of(-2.,0.,0.,1.,8.));var bins=list(histogram.get("bins"));
        assertThat(bins.stream().mapToInt(b->((Number)b.get("count")).intValue()).sum()).isEqualTo(5);
        assertThat(bins.getFirst()).containsEntry("lower",-2.);assertThat(bins.getLast()).containsEntry("upper",8.).containsEntry("closed_upper",true);
        var constant=list(ReportAdvancedProjection.histogram(List.of(0.,0.,0.)).get("bins"));assertThat(constant).hasSize(1);assertThat(constant.getFirst()).containsEntry("lower",0.).containsEntry("upper",0.).containsEntry("count",3);
    }
    @Test void monthlyReceptionUsesLaPazAndPreservesRealZeroMonths(){
        var logical=logical(List.of(numeric("x")),List.of(row(Map.of("x",0),"2026-03-01T02:00:00Z"),row(Map.of("x",8),"2026-03-01T04:00:00Z")));logical.put("from_date","2026-01-31");
        var result=Map.<String,Object>of("variables",List.of(Map.of("question_id","x","numeric",Map.of("n",2))));
        var variable=list(ReportAdvancedProjection.enrich(result,logical,"2026-04-10T12:00:00Z").get("variables")).getFirst();var advanced=(Map<?,?>)variable.get("advanced");var time=(Map<?,?>)advanced.get("time");
        assertThat(time.get("months")).isEqualTo(List.of("2026-01","2026-02","2026-03","2026-04"));assertThat(time.get("counts")).isEqualTo(List.of(0,1,1,0));
        assertThat(((Map<?,?>)advanced.get("box")).get("values")).isEqualTo(List.of(0.,2.,4.,6.,8.));
    }
    @Test void historicalMissingReceiptMetadataDisablesTimeAndOneValueDisablesBox(){
        var row=row(Map.of("x",7),"2026-03-01T04:00:00Z");row.remove("submitted_at");var logical=logical(List.of(numeric("x")),List.of(row));
        var variable=list(ReportAdvancedProjection.enrich(Map.of("variables",List.of(Map.of("question_id","x","numeric",Map.of("n",1)))),logical,"2026-04-10T12:00:00Z").get("variables")).getFirst();
        var advanced=(Map<?,?>)variable.get("advanced");assertThat(advanced.containsKey("histogram")).isTrue();assertThat(advanced.containsKey("time")).isFalse();assertThat(advanced.containsKey("box")).isFalse();
    }
    @Test void scatterGroupsCoordinatesWithoutIdentifiersAndExcludesIncompletePairs(){
        var logical=logical(List.of(numeric("x"),numeric("y")),List.of(row(Map.of("x",0,"y",10),"2026-01-01T00:00:00Z"),row(Map.of("x",0,"y",10),"2026-01-01T00:00:00Z"),row(Map.of("x",5),"2026-01-01T00:00:00Z")));
        var chart=ReportAdvancedProjection.charts(config(new ReportCustomization.Advanced("scatter",List.of("x","y"),"Pares","",true)),logical).getFirst();
        assertThat(chart).containsEntry("n",2).containsEntry("excluded",1).containsEntry("points",List.of(List.of(0.,10.,2)));
        assertThat(chart.toString()).doesNotContain("response_id","submitted_at");
    }
    Map<String,Object> employerLogical(boolean incomplete){
        var fields=new ArrayList<Map<String,Object>>();var first=new LinkedHashMap<String,Object>();var second=new LinkedHashMap<String,Object>();
        for(int i=1;i<=5;i++){String id="criterio_%02d".formatted(i);fields.add(Map.of("question_id",id,"label",id,"treatment","NOMINAL","type","radio","options",List.of("TOTALMENTE_ACUERDO","PARCIALMENTE_ACUERDO","TOTALMENTE_DESACUERDO","NO_SABE").stream().map(value->Map.of("value",value)).toList()));first.put(id,incomplete&&i==5?"NO_SABE":"PARCIALMENTE_ACUERDO");second.put(id,i==1?"NO_SABE":incomplete&&i==5?"NO_SABE":"TOTALMENTE_DESACUERDO");}
        return logical(fields,List.of(row(first,"2026-01-01T00:00:00Z"),row(second,"2026-01-01T00:00:00Z")));
    }
    @Test void employerRadarKeepsCriterionBasesAndNoSabeSeparate(){
        var ids=java.util.stream.IntStream.rangeClosed(1,5).mapToObj(i->"criterio_%02d".formatted(i)).toList();
        var setting=config(new ReportCustomization.Advanced("radar",ids,"Radar","",true));var chart=ReportAdvancedProjection.charts(setting,employerLogical(false)).getFirst();
        var criteria=list(chart.get("criteria"));assertThat(chart.get("available")).isEqualTo(true);assertThat(criteria.getFirst()).containsEntry("base",1).containsEntry("no_sabe",1).containsEntry("percentage",100.);assertThat(criteria.get(1)).containsEntry("base",2).containsEntry("percentage",50.);
        var incomplete=ReportAdvancedProjection.charts(setting,employerLogical(true)).getFirst();assertThat(incomplete.get("available")).isEqualTo(false);assertThat(list(incomplete.get("criteria")).getLast()).containsEntry("percentage",null).containsEntry("base",0);
        var withStates=employerLogical(false);var members=new ArrayList<>(list(withStates.get("matrix")));
        for(String state:List.of("SIN_RESPUESTA","NO_APLICA"))members.add(Map.of("values",Map.of(),"states",ids.stream().collect(java.util.stream.Collectors.toMap(id->id,id->state))));
        withStates.put("matrix",members);var coverage=list(ReportAdvancedProjection.charts(setting,withStates).getFirst().get("criteria")).getFirst();assertThat(coverage).containsEntry("missing",1).containsEntry("not_applicable",1).containsEntry("no_sabe",1).containsEntry("base",1);
    }
    @Test void incompatibleAdvancedQuestionsAndInventedRadarAreRejected(){
        var logical=employerLogical(false);
        assertThatThrownBy(()->ReportAdvancedProjection.charts(config(new ReportCustomization.Advanced("scatter",List.of("criterio_01","criterio_02"),"Mal","",true)),logical)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ReportAdvancedProjection.charts(config(new ReportCustomization.Advanced("radar",List.of("criterio_01","criterio_02"),"Mal","",true)),logical)).isInstanceOf(IllegalArgumentException.class);
    }
}

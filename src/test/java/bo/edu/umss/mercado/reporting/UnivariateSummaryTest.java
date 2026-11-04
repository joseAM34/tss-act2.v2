package bo.edu.umss.mercado.reporting;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class UnivariateSummaryTest {
    @SuppressWarnings("unchecked") static List<Map<String,Object>> rows(Object value){return (List<Map<String,Object>>)value;}
    static Object measure(Map<String,Object> summary,String key){return rows(summary.get("measures")).stream().filter(m->key.equals(m.get("key"))).findFirst().orElseThrow().get("value");}
    @Test void presentationGroupsFrozenValuesWithFormulaeAndNoInterpretation(){
        var summary=UnivariateSummary.calculate(List.of(1.,2.,2.,5.),"meses");String before=summary.toString();
        var groups=UnivariatePresentation.groups(summary);
        assertThat(groups).extracting(UnivariatePresentation.Group::title).containsExactly("Medidas de tendencia central","Medidas de dispersión","Medidas de posición");
        assertThat(groups.getFirst().measures().getFirst().formula()).isEqualTo("x̄ = Σxi / n");
        assertThat(groups.getFirst().measures().getFirst().display()).isEqualTo("2.50");
        assertThat(groups.getFirst().measures().getLast().display()).isEqualTo("2.0");
        assertThat(groups.get(1).measures()).filteredOn(m->m.label().equals("Varianza muestral")).allSatisfy(m->{assertThat(m.unit()).isEqualTo("meses²");assertThat(m.formula()).contains("n − 1");});
        assertThat(summary.toString()).isEqualTo(before);
        assertThat(UnivariatePresentation.groups(UnivariateSummary.calculate(List.of(),"meses")).getFirst().measures().getFirst().display()).isEqualTo("Sin datos suficientes o no definido");
    }
    @Test void exactAndGroupedFrequenciesKeepTheirBasesAndMatchKnownStatistics(){
        var s=UnivariateSummary.calculate(List.of(1.,2.,2.,5.),"meses");
        assertThat(measure(s,"mean")).isEqualTo(2.5);assertThat((Double)measure(s,"sample_variance")).isCloseTo(3.,within(1e-12));
        assertThat(measure(s,"q1")).isEqualTo(1.75);assertThat(measure(s,"q3")).isEqualTo(2.75);
        assertThat((Double)measure(s,"p90")).isCloseTo(4.1,within(1e-12));assertThat(measure(s,"range")).isEqualTo(4.);
        assertThat((Double)measure(s,"cv")).isCloseTo(Math.sqrt(3)/2.5*100,within(1e-10));
        assertThat(s.get("modes")).isEqualTo(List.of(2.));
        var exact=rows(s.get("frequencies"));assertThat(exact.get(1)).containsEntry("count",2).containsEntry("relative",.5).containsEntry("cumulative",3).containsEntry("cumulative_percent",75.);
        assertThat(rows(s.get("intervals")).stream().mapToInt(b->((Number)b.get("count")).intValue()).sum()).isEqualTo(4);
        assertThat(rows(s.get("intervals")).getLast()).containsEntry("cumulative",4).containsEntry("cumulative_percent",100.);
    }
    @Test void emptySingletonZeroAndTiedModesDoNotInventStatistics(){
        var empty=UnivariateSummary.calculate(List.of(),"años");assertThat(measure(empty,"p90")).isNull();assertThat(rows(empty.get("frequencies"))).isEmpty();
        var one=UnivariateSummary.calculate(List.of(7.),"años");assertThat(measure(one,"sample_variance")).isNull();assertThat(measure(one,"cv")).isNull();assertThat(one.get("mode_status")).isEqualTo("SIN_REPETICIONES");
        var zero=UnivariateSummary.calculate(List.of(0.,0.),"meses");assertThat(measure(zero,"cv")).isNull();assertThat(measure(zero,"sample_variance")).isEqualTo(0.);
        var tied=UnivariateSummary.calculate(List.of(2.,2.,4.,4.),"años");assertThat(tied.get("modes")).isEqualTo(List.of(2.,4.));
        assertThat(measure(UnivariateSummary.calculate(List.of(2020.,2024.),"año calendario"),"cv")).isNull();
        assertThatThrownBy(()->UnivariateSummary.calculate(List.of(Double.NaN),"años")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void detailedProjectionIsOptInAndLeavesTheLegacyResultUnchanged(){
        var variable=new LinkedHashMap<String,Object>();variable.put("question_id","age");variable.put("unit","años");variable.put("numeric",Map.of("n",2));
        var result=Map.<String,Object>of("variables",List.of(variable));
        var logical=Map.<String,Object>of("matrix",List.of(Map.of("values",Map.of("age",3),"states",Map.of("age","CON_VALOR")),Map.of("values",Map.of("age",5),"states",Map.of("age","CON_VALOR")),Map.of("values",Map.of("age",99),"states",Map.of("age","NO_APLICA"))));
        var old=ReportAdvancedProjection.enrich(result,logical,"2026-11-04T00:00:00Z");
        var updated=ReportAdvancedProjection.enrich(result,logical,"2026-11-04T00:00:00Z",true);
        assertThat(rows(old.get("variables")).getFirst()).doesNotContainKey("univariate");assertThat(variable).doesNotContainKey("univariate");
        assertThat(updated).containsEntry("univariate_projection_version",1);
        var detailed=(Map<?,?>)rows(updated.get("variables")).getFirst().get("univariate");assertThat(detailed.get("n")).isEqualTo(2);
    }
}

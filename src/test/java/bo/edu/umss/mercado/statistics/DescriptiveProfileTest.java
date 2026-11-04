package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DescriptiveProfileTest {
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> profile,String key){return (List<Map<String,Object>>)profile.get(key);}
    Object measure(Map<String,Object> profile,String label){return rows(profile,"measures").stream().filter(m->label.equals(m.get("label"))).findFirst().orElseThrow().get("value");}
    @Test void numericProfileUsesSampleDispersionTypeSevenQuantilesAndExplicitFrequencyBases(){
        var p=DescriptiveProfile.numeric(List.of(0.,2.,4.,6.,8.),"meses");
        assertThat(measure(p,"Media")).isEqualTo(4.);assertThat(measure(p,"Rango")).isEqualTo(8.);
        assertThat(measure(p,"Varianza muestral (unidad al cuadrado)")).isEqualTo(10.);
        assertThat((Double)measure(p,"Desviación estándar muestral")).isCloseTo(Math.sqrt(10),within(1e-12));
        assertThat(measure(p,"Q1 / P25")).isEqualTo(2.);assertThat(measure(p,"Q3 / P75")).isEqualTo(6.);
        assertThat((Double)measure(p,"Percentil 90 (tipo 7)")).isCloseTo(7.2,within(1e-12));
        assertThat((Double)measure(p,"Coeficiente de variación (%)")).isCloseTo(Math.sqrt(10)/4*100,within(1e-12));
        var f=rows(p,"frequencies");assertThat(f).hasSize(5);assertThat(f.getFirst()).containsEntry("value",0.).containsEntry("relative",.2).containsEntry("base",5);assertThat(f.getLast()).containsEntry("cumulative",5).containsEntry("cumulative_percent",100.);
        var bins=rows(p,"intervals");assertThat(bins.stream().mapToInt(b->(Integer)b.get("count")).sum()).isEqualTo(5);assertThat(bins.getLast()).containsEntry("closed_upper",true).containsEntry("cumulative",5);
    }
    @Test void absenceZeroMeanSmallSamplesAndUnknownUnitsDoNotProduceInventedStatistics(){
        var empty=DescriptiveProfile.numeric(List.of(),"meses");assertThat(rows(empty,"intervals")).isEmpty();assertThat(measure(empty,"Media")).isNull();assertThat(measure(empty,"Moda")).isEqualTo("Sin datos");
        var one=DescriptiveProfile.numeric(List.of(3.),"meses");assertThat(measure(one,"Varianza muestral (unidad al cuadrado)")).isNull();assertThat(measure(one,"Percentil 90 (tipo 7)")).isEqualTo(3.);
        assertThat(measure(DescriptiveProfile.numeric(List.of(0.,0.),"meses"),"Coeficiente de variación (%)")).isNull();
        assertThat(measure(DescriptiveProfile.numeric(List.of(-1.,3.),"meses"),"Coeficiente de variación (%)")).isNull();
        assertThat(measure(DescriptiveProfile.numeric(List.of(1.,3.),"grados Celsius"),"Coeficiente de variación (%)")).isNull();
        var constant=DescriptiveProfile.numeric(List.of(2.,2.,2.),"años");assertThat(measure(constant,"Coeficiente de variación (%)")).isEqualTo(0.);assertThat(rows(constant,"intervals")).hasSize(1);
        assertThat(measure(DescriptiveProfile.numeric(List.of(2.,2.),"meses completos"),"Coeficiente de variación (%)")).isEqualTo(0.);
        assertThat(measure(DescriptiveProfile.numeric(List.of(2020.,2024.),"año calendario"),"Coeficiente de variación (%)")).isNull();
        assertThat(rows(DescriptiveProfile.numeric(List.of(1.,1.,1.,1.,1.,100.),"meses"),"outliers")).containsExactly(Map.of("value",100.,"count",1));
    }
    @Test void nominalCodesAndNoSabeRemainCategoriesAndSourceResultsStayUnmodified(){
        var variable=Map.<String,Object>of("question_id","q","type","radio","treatment","NOMINAL","distribution",List.of(Map.of("label","No sabe","count",2,"respondent_base",3,"mention_base",3),Map.of("label","1","count",1,"respondent_base",3,"mention_base",3)));
        var result=Map.<String,Object>of("variables",List.of(variable));String original=CanonicalJson.json(result);
        var enriched=DescriptiveProfile.enrich(result,Map.of("matrix",List.of()));
        @SuppressWarnings("unchecked") var first=((List<Map<String,Object>>)enriched.get("variables")).getFirst();
        @SuppressWarnings("unchecked") var profile=(Map<String,Object>)first.get("quality");
        assertThat(profile).containsEntry("kind","NOMINAL").doesNotContainKey("frequencies");assertThat(measure(profile,"Moda categórica")).isEqualTo("No sabe");assertThat(CanonicalJson.json(result)).isEqualTo(original);
    }
}

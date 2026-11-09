package bo.edu.umss.mercado.statistics;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class NumericNormalizationTest {
    @Test void fullAnswersPreserveDecimalsAndRepeatedObservations(){
        var rules=NumericNormalization.definition(true,"años","1=1 año|un año\n1,5=1,5 años|18 meses\n10=10 años");
        var result=NumericNormalization.project(rules,List.of("1 año","18 meses","1,5 años","10 años","1 a 5 años","1 5 anos"),"Experiencia");
        assertThat(result.get("converted")).isEqualTo(4);assertThat(result.get("pending")).isEqualTo(2);
        var summary=(Map<?,?>)result.get("univariate");assertThat(summary.get("modes")).isEqualTo(List.of(1.5));
        assertThat(((Map<?,?>)result.get("numeric")).get("mean")).isEqualTo(3.5);
        assertThat(NumericNormalization.project(NumericNormalization.definition(false,"años","1=1 año"),List.of("1 año"),"Q")).isEmpty();
        assertThat(NumericNormalization.project(NumericNormalization.definition(true,"años",""),List.of("1 año"),"Q")).isEmpty();
    }
    @Test void rejectsAmbiguousMappingsInvalidNumbersAndUnits(){
        assertThatThrownBy(()->NumericNormalization.definition(true,"años","1=un año\n2=un año")).isInstanceOf(IllegalArgumentException.class);
        for(String rules:List.of("NaN=uno","Infinity=uno","-1=uno","1=","1e999=uno","1=18 meses","2=1 a 5 años"))assertThatThrownBy(()->NumericNormalization.definition(true,"años",rules)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->NumericNormalization.definition(true,"kg","1=uno")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void principalUnitAndClockConversionsRejectImplicitCalendarAssumptions(){
        assertThat(NumericNormalization.convert(18,"meses","años")).isEqualTo(1.5);
        assertThat(NumericNormalization.convert(1,"mes","años")).isCloseTo(1d/12,within(1e-12));
        assertThat(NumericNormalization.convert(2,"horas","minutos")).isEqualTo(120);
        assertThat(NumericNormalization.convert(1,"día","segundos")).isEqualTo(86400);
        for(var pair:List.of(List.of("días","meses"),List.of("años","horas")))assertThatThrownBy(()->NumericNormalization.convert(1,pair.get(0),pair.get(1))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pendiente");
        assertThatThrownBy(()->NumericNormalization.contextual(true,"años","0.1666666666667=2")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->NumericNormalization.contextual(true,"años","1=30 días")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->NumericNormalization.contextual(true,"años","1=un día")).isInstanceOf(IllegalArgumentException.class);
        assertThat(NumericNormalization.contextual(true,"minutos","120=2 horas|120\n1=60 segundos|1").get("unitless_policy")).isEqualTo("UNIDAD_PRINCIPAL");
    }
    @Test void genericContextsSupportWeightCountsSignedQuantitiesAndPreserveGuidance(){
        var weights=NumericNormalization.generic(true,"Peso en kilogramos","1=1000 gramos|1 kg\n1.5=1500 gramos","Peso en kilogramos","1 kg = 1000 g");
        assertThat(weights.get("version")).isEqualTo(3);assertThat(weights.get("proportions")).isEqualTo("1 kg = 1000 g");
        assertThat(((Map<?,?>)NumericNormalization.project(weights,List.of("1000 gramos","1500 gramos"),"Peso").get("numeric")).get("mean")).isEqualTo(1.25);
        var temperatures=NumericNormalization.generic(true,"grados Celsius","-10=-10 grados\n30=30 grados","Temperatura en grados Celsius","");
        var summary=(Map<?,?>)NumericNormalization.project(temperatures,List.of("-10 grados","30 grados"),"Temperatura").get("univariate");
        assertThat(((List<Map<String,Object>>)summary.get("measures")).stream().filter(m->"cv".equals(m.get("key"))).findFirst().orElseThrow().get("value")).isNull();
        assertThatThrownBy(()->NumericNormalization.generic(true,"kg","1=1", "", "1 kg = 1000 g")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->NumericNormalization.generic(true,"kg","NaN=uno", "Peso", "")).isInstanceOf(IllegalArgumentException.class);
        String json="""
        {"unidad_destino":"Peso en kilogramos","propuestas":[{"original":"1000 gramos","estado":"CONVERTIBLE","valor":1,"unidad":"Peso en kilogramos"},{"original":"entre 2 y 5 kg","estado":"PENDIENTE","valor":null}]}
        """;
        assertThat(NumericNormalization.importJson(json,"Peso en kilogramos",true)).isEqualTo("1=1000 gramos");
    }
    @Test void refusesFormatExamplesAndUnsupportedAiStatesInsteadOfClearingTheDictionary(){
        for(String proposal:List.of("{\"id\":\"EJEMPLO\",\"original\":\"1\",\"estado\":\"CONVERTIBLE\",\"valor\":1,\"unidad\":\"Año\"}","{\"original\":\"1\",\"estado\":\"PROCESADO\",\"valor\":1,\"unidad\":\"Año\"}")){
            String json="{\"unidad_destino\":\"Año\",\"propuestas\":["+proposal+"]}";assertThatThrownBy(()->NumericNormalization.importJson(json,"Año",true)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void importsOnlyExplicitConvertibleProposals(){
        String json="""
          {"unidad_destino":"años","propuestas":[{"original":"18 meses","estado":"CONVERTIBLE","valor":1.5,"unidad":"años"},{"original":"2 a 5 años","estado":"PENDIENTE","valor":null}]}
          """;
        assertThat(NumericNormalization.importJson(json,"años")).isEqualTo("1.5=18 meses");
        assertThatThrownBy(()->NumericNormalization.importJson(json,"meses")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void importsSimpleLinesAndMergesWithoutLosingSavedAliases(){
        String rules=NumericNormalization.importProposal("1.5=18 meses|1,5 años\n2=dos años", "1=un año\n1.5=18 meses", "Año", true);
        var definition=NumericNormalization.generic(true,"Año",rules,"Año", "Meses");
        assertThat((java.util.Map<?,?>)definition.get("mappings")).hasSize(4);
        assertThat(rules).contains("1=un año", "2=dos años");
        assertThatThrownBy(()->NumericNormalization.importProposal("2=un año","1=un año","Año",true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->NumericNormalization.importProposal("# Sin cantidades convertibles","1=un año","Año",true)).isInstanceOf(IllegalArgumentException.class);
    }
}

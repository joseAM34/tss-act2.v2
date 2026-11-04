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
        assertThatThrownBy(()->NumericNormalization.definition(true,"días","1=uno")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void importsOnlyExplicitConvertibleProposals(){
        String json="""
          {"unidad_destino":"años","propuestas":[{"original":"18 meses","estado":"CONVERTIBLE","valor":1.5,"unidad":"años"},{"original":"2 a 5 años","estado":"PENDIENTE","valor":null}]}
          """;
        assertThat(NumericNormalization.importJson(json,"años")).isEqualTo("1.5=18 meses");
        assertThatThrownBy(()->NumericNormalization.importJson(json,"meses")).isInstanceOf(IllegalArgumentException.class);
    }
}

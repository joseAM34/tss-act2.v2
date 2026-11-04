package bo.edu.umss.mercado.reporting;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class FrequencyPresentationTest {
    @Test void relativeFrequencyUsesTheStatedDenominatorAndPreservesStoredPercentages(){
        var option=Map.<String,Object>of("label","Opción de prueba","count",13,"respondent_base",123,"respondent_percent",10.57,"mention_base",123,"mention_percent",10.57);
        var variable=Map.<String,Object>of("distribution",List.of(option));
        var row=FrequencyPresentation.rows(variable).getFirst();
        assertThat(row.relative()).isCloseTo(13d/123,within(1e-12));assertThat(row.percent()).isEqualTo(10.57);
        assertThat(row.relativeText()).isEqualTo("0.1057");assertThat(row.percentText()).isEqualTo("10.57");
        assertThat(option).hasSize(6).doesNotContainKey("relative");
    }
    @Test void multipleSelectionBasesStayDistinctAndZerosAreNotMissing(){
        var variable=Map.<String,Object>of("distribution",List.of(Map.of("count",3,"respondent_base",5,"mention_base",10)));
        var row=FrequencyPresentation.rows(variable).getFirst();
        assertThat(row.relative()).isEqualTo(.6);assertThat(row.selectionRelative()).isEqualTo(.3);
        assertThat(FrequencyPresentation.relative(0,5)).isEqualTo(0.);
        assertThat(FrequencyPresentation.relative(0,0)).isNull();assertThat(FrequencyPresentation.relative(1,null)).isNull();
    }
}

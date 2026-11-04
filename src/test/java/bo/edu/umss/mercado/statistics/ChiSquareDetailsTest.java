package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ChiSquareDetailsTest {
    @SuppressWarnings("unchecked") List<Map<String,Object>> cells(Map<String,Object> detail){return (List<Map<String,Object>>)detail.get("cells");}
    @Test void cellContributionsSumToTheExecutedStatisticAndResidualSignsDescribeDirection(){
        var table=StatisticalMath.contingency(new long[][]{{10,20},{20,10}},true,false,.05);String before=CanonicalJson.json(table);
        var detail=ChiSquareDetails.describe(table);assertThat((Double)detail.get("diagnostic_sum")).isCloseTo(((Number)table.get("chi_square")).doubleValue(),within(1e-12));
        var first=cells(detail).getFirst();assertThat(first).containsEntry("expected",15.).containsEntry("difference",-5.);
        assertThat((Double)first.get("contribution")).isCloseTo(25d/15,within(1e-12));assertThat((Double)first.get("pearson_residual")).isCloseTo(-5/Math.sqrt(15),within(1e-12));
        assertThat((Double)cells(detail).get(1).get("pearson_residual")).isPositive();assertThat(detail.get("decision").toString()).contains("Se rechaza H₀");assertThat(CanonicalJson.json(table)).isEqualTo(before);
    }
    @Test void aDiagnosticSumDoesNotAuthorizeInferenceWhenIndependenceIsUndocumented(){
        var table=StatisticalMath.contingency(new long[][]{{10,20},{20,10}},false,false,.05);var detail=ChiSquareDetails.describe(table);
        assertThat(detail).containsEntry("test_executed",false);assertThat(detail.get("diagnostic_sum")).isNotNull();assertThat(detail.get("decision").toString()).contains("Prueba no ejecutada","no se emite una decisión");assertThat(table.get("p")).isNull();assertThat(table.get("chi_square")).isNull();
    }
    @Test void emptyAndZeroMarginCellsHaveNoInventedResidualsAndNonRejectionDoesNotProveIndependence(){
        var empty=ChiSquareDetails.describe(StatisticalMath.contingency(new long[][]{{0,0},{0,0}},true,false,.05));assertThat(empty.get("diagnostic_sum")).isNull();assertThat(empty.get("uncalculated_cells")).isEqualTo(4);assertThat(cells(empty)).allSatisfy(c->assertThat(c.get("pearson_residual")).isNull());
        var withZero=ChiSquareDetails.describe(StatisticalMath.contingency(new long[][]{{10,10},{10,10},{0,0}},true,false,.05));assertThat(withZero.get("uncalculated_cells")).isEqualTo(2);assertThat(withZero.get("diagnostic_sum")).isEqualTo(0.);assertThat(withZero.get("decision").toString()).contains("No se rechaza H₀","no demuestra independencia");assertThat(cells(withZero).getLast().get("contribution")).isNull();
    }
}

package bo.edu.umss.mercado.statistics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static bo.edu.umss.mercado.statistics.StatisticalMath.map;

class GenericCrossTest {
    GenericCrossService engine = new GenericCrossService(null, null);
    GenericCrossService.Spec spec(boolean documented, boolean zeros) { return new GenericCrossService.Spec("a", "b", .05, documented, documented ? "Envíos independientes documentados" : "", documented ? "Revisión metodológica ficticia para la prueba" : "", zeros); }
    Map<String, Object> source(int[][] counts) {
        var matrix = new ArrayList<Map<String, Object>>();
        for (int r = 0; r < counts.length; r++) for (int c = 0; c < counts[r].length; c++) for (int i = 0; i < counts[r][c]; i++) matrix.add(map("values", map("a", "R" + r, "b", "C" + c), "states", map("a", "CON_VALOR", "b", "CON_VALOR")));
        var rowOptions = new ArrayList<Map<String, Object>>(); var columnOptions = new ArrayList<Map<String, Object>>();
        for (int r = 0; r < counts.length; r++) rowOptions.add(map("value", "R" + r, "label", "Fila " + r));
        for (int c = 0; c < counts[0].length; c++) columnOptions.add(map("value", "C" + c, "label", "Columna " + c));
        return map("rules_version", "PREPARACION_GENERAL_V1", "definition", map("title", "Prueba"), "matrix", matrix, "dictionary", List.of(
                map("question_id", "a", "label", "A", "type", "radio", "treatment", "NOMINAL", "options", rowOptions),
                map("question_id", "b", "label", "B", "type", "select", "treatment", "NOMINAL", "options", columnOptions)));
    }
    @SuppressWarnings("unchecked") Map<String, Object> table(Map<String, Object> source, GenericCrossService.Spec spec) { return (Map<String, Object>) engine.calculate(source, spec).get("table"); }
    @Test void knownTableCalculatesChiSquarePAndCramersV() {
        var t = table(source(new int[][]{{10,20},{20,10}}), spec(true, false));
        assertThat(t).containsEntry("status", "CALCULADO").containsEntry("n", 60L).containsEntry("df", 1).containsEntry("reject_h0", true);
        assertThat(((Number)t.get("chi_square")).doubleValue()).isCloseTo(6.6666666667, within(.000001));
        assertThat(((Number)t.get("p")).doubleValue()).isCloseTo(.0098232745, within(.000001));
        assertThat(((Number)t.get("cramers_v")).doubleValue()).isCloseTo(1d/3, within(.000001));
        assertThat(t.get("row_percentages")).isEqualTo(List.of(List.of(100d/3,200d/3),List.of(200d/3,100d/3)));
    }
    @Test void independenceIsNotAssumedFromHavingManySubmissions() {
        var t = table(source(new int[][]{{10,20},{20,10}}), spec(false, false));
        assertThat(t).containsEntry("status", "INDEPENDENCIA_NO_DOCUMENTADA"); assertThat(t.get("p")).isNull(); assertThat(t.get("cramers_v")).isNull();
    }
    @Test void lowExpectedCountsAndStructuralZerosSuspendInference() {
        var small = table(source(new int[][]{{1,2},{2,1}}), spec(true, false)); assertThat(small).containsEntry("status", "FRECUENCIAS_INSUFICIENTES"); assertThat(small.get("p")).isNull();
        var structural = table(source(new int[][]{{10,20},{20,10}}), spec(true, true)); assertThat(structural).containsEntry("status", "ESTRUCTURA_INCOMPATIBLE"); assertThat(structural.get("p")).isNull();
    }
    @Test void emptyAndConstantDataAreDifferentFromNoAssociation() {
        assertThat(table(source(new int[][]{{0,0},{0,0}}), spec(false, false))).containsEntry("status", "SIN_DATOS");
        assertThat(table(source(new int[][]{{10,10},{0,0}}), spec(true, false))).containsEntry("status", "SIN_VARIACION");
    }
    @Test void zeroCategoriesAreRetainedInTableAndRemovedOnlyForTheTest() {
        var t = table(source(new int[][]{{10,20},{20,10},{0,0}}), spec(true, false));
        assertThat(t).containsEntry("removed_rows", List.of(2)).containsEntry("status", "CALCULADO");
        assertThat(t.get("observed")).isEqualTo(List.of(List.of(10L,20L),List.of(20L,10L),List.of(0L,0L)));
    }
    @SuppressWarnings("unchecked") @Test void missingPairStatesAreCountedWithoutBeingCodedAsCategories() {
        var data = source(new int[][]{{10,20},{20,10}}); var matrix = (List<Map<String,Object>>) data.get("matrix");
        matrix.add(map("values", map("a", null, "b", "C0"), "states", map("a", "NO_APLICA", "b", "CON_VALOR")));
        matrix.add(map("values", map("a", "R0", "b", null), "states", map("a", "CON_VALOR", "b", "SIN_RESPUESTA")));
        var result = engine.calculate(data,spec(true,false)); assertThat(result).containsEntry("total_submissions",62).containsEntry("excluded_pairs",2);
        assertThat(((Map<?,?>) result.get("pair_states")).get("NO_APLICA|CON_VALOR")).isEqualTo(1);
        assertThat(((Map<?,?>)result.get("table")).get("n")).isEqualTo(60L);
    }
    @Test void invalidAlphaSameQuestionForeignQuestionAndUndocumentedEvidenceAreRejected() {
        var data = source(new int[][]{{10,20},{20,10}});
        for (var invalid : List.of(new GenericCrossService.Spec("a","a",.05,false,"","",false),new GenericCrossService.Spec("a","foreign",.05,false,"","",false),
                new GenericCrossService.Spec("a","b",Double.NaN,false,"","",false),new GenericCrossService.Spec("a","b",.05,true,"","",false))) assertThatThrownBy(() -> engine.calculate(data,invalid)).isInstanceOf(IllegalArgumentException.class);
    }
    @SuppressWarnings("unchecked") @Test void multipleAndNumericVariablesAreRejected() {
        var data = source(new int[][]{{10,20},{20,10}}); var dictionary = (List<Map<String,Object>>)data.get("dictionary");
        dictionary.getFirst().put("type","checkbox"); assertThatThrownBy(() -> engine.calculate(data,spec(false,false))).isInstanceOf(IllegalArgumentException.class);
        dictionary.getFirst().put("type","number"); dictionary.getFirst().put("treatment","MAGNITUD_ENTERA"); assertThatThrownBy(() -> engine.calculate(data,spec(false,false))).isInstanceOf(IllegalArgumentException.class);
    }
}

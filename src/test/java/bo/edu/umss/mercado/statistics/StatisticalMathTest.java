package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Vectores de referencia independientes para los cálculos y sus límites de aplicabilidad. */
class StatisticalMathTest {
    @Test
    void numericSummaryUsesSampleDispersionAndInterpolatedQuartiles() {
        var result = StatisticalMath.numeric(List.of(2d, 4d, 4d, 4d, 5d, 5d, 7d, 9d));
        assertThat(number(result, "n")).isEqualTo(8);
        assertThat(number(result, "mean")).isEqualTo(5);
        assertThat(number(result, "median")).isEqualTo(4.5);
        assertThat(number(result, "sample_variance")).isCloseTo(32d / 7, within(1e-12));
        assertThat(number(result, "sample_standard_deviation")).isCloseTo(2.138089935299395, within(1e-12));
        assertThat(number(result, "min")).isEqualTo(2);
        assertThat(number(result, "max")).isEqualTo(9);
        assertThat(number(result, "q1")).isEqualTo(4);
        assertThat(number(result, "q3")).isEqualTo(5.5);
        assertThat(number(result, "iqr")).isEqualTo(1.5);
    }

    @Test
    void anEmptyNumericSampleHasNullMeasuresInsteadOfZeroOrNaN() {
        var result = StatisticalMath.numeric(List.of());
        assertThat(number(result, "n")).isZero();
        for (String measure : List.of("mean", "median", "sample_variance", "sample_standard_deviation", "min", "max", "q1", "q3", "iqr")) {
            assertThat(result.get(measure)).as(measure).isNull();
        }
        assertThat(CanonicalJson.json(result)).doesNotContain("NaN", "Infinity");
    }

    @Test
    void aSingleNumericObservationHasALocationButNoSampleStandardDeviation() {
        var result = StatisticalMath.numeric(List.of(12d));
        assertThat(number(result, "n")).isEqualTo(1);
        assertThat(number(result, "mean")).isEqualTo(12);
        assertThat(number(result, "median")).isEqualTo(12);
        assertThat(result.get("sample_variance")).isNull();
        assertThat(result.get("sample_standard_deviation")).isNull();
    }

    @Test
    void aConstantSampleHasZeroDispersionWithoutLosingTheObservedZero() {
        var result = StatisticalMath.numeric(List.of(0d, 0d, 0d));
        assertThat(number(result, "mean")).isZero();
        assertThat(number(result, "sample_variance")).isZero();
        assertThat(number(result, "sample_standard_deviation")).isZero();
        assertThat(number(result, "iqr")).isZero();
    }

    @Test
    void numericSummaryDoesNotDependOnInputOrdering() {
        var shuffled = StatisticalMath.numeric(List.of(9d, 2d, 7d, 4d, 5d, 4d, 5d, 4d));
        var sorted = StatisticalMath.numeric(List.of(2d, 4d, 4d, 4d, 5d, 5d, 7d, 9d));
        for (String measure : List.of("n", "mean", "median", "sample_variance", "sample_standard_deviation", "min", "max", "q1", "q3", "iqr")) {
            assertThat(number(shuffled, measure)).as(measure).isCloseTo(number(sorted, measure), within(1e-12));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"nan", "positive-infinity", "negative-infinity", "null"})
    void nonFiniteOrUnrecordedValuesCannotSilentlyEnterTheNumericCalculation(String value) {
        Double invalid = switch (value) {
            case "nan" -> Double.NaN;
            case "positive-infinity" -> Double.POSITIVE_INFINITY;
            case "negative-infinity" -> Double.NEGATIVE_INFINITY;
            default -> null;
        };
        assertThatThrownBy(() -> StatisticalMath.numeric(Arrays.asList(1d, invalid)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pearsonTwoByTwoMatchesTheReferenceStatisticPValueAndCramersV() {
        var result = StatisticalMath.contingency(new long[][]{{20, 10}, {10, 20}}, true, false, .05);
        assertThat(result.get("status")).isEqualTo("CALCULADO");
        assertThat(number(result, "n")).isEqualTo(60);
        assertThat(number(result, "chi_square")).isCloseTo(6.666666666666667, within(1e-12));
        assertThat(number(result, "df")).isEqualTo(1);
        assertThat(number(result, "p")).isCloseTo(.009823274507519235, within(1e-14));
        assertThat(number(result, "cramers_v")).isCloseTo(1d / 3, within(1e-12));
        assertThat(result.get("reject_h0")).isEqualTo(true);
        assertThat(matrixNumber(result.get("expected"), 0, 0)).isEqualTo(15);
        assertThat(matrixNumber(result.get("row_percentages"), 0, 0)).isCloseTo(200d / 3, within(1e-12));
        assertThat(matrixNumber(result.get("column_percentages"), 0, 0)).isCloseTo(200d / 3, within(1e-12));
        assertThat(matrixNumber(result.get("total_percentages"), 0, 0)).isCloseTo(100d / 3, within(1e-12));
    }

    @Test
    void pearsonTwoByThreeMatchesAnIndependentReference() {
        var result = StatisticalMath.contingency(new long[][]{{10, 20, 30}, {15, 15, 30}}, true, false, .05);
        assertThat(result.get("status")).isEqualTo("CALCULADO");
        assertThat(number(result, "n")).isEqualTo(120);
        assertThat(number(result, "chi_square")).isCloseTo(1.7142857142857144, within(1e-12));
        assertThat(number(result, "df")).isEqualTo(2);
        assertThat(number(result, "p")).isCloseTo(.42437284567695, within(1e-13));
        assertThat(result.get("reject_h0")).isEqualTo(false);
        assertThat(matrixNumber(result.get("expected"), 0, 0)).isEqualTo(12.5);
        assertThat(matrixNumber(result.get("expected"), 0, 1)).isEqualTo(17.5);
        assertThat(matrixNumber(result.get("expected"), 0, 2)).isEqualTo(30);
    }

    @Test
    void zeroMarginsAreRemovedForInferenceButTheFullObservedTableIsPreserved() {
        long[][] observed = {{0, 0, 0}, {0, 20, 10}, {0, 10, 20}};
        var result = StatisticalMath.contingency(observed, true, false, .05);
        assertThat(result.get("status")).isEqualTo("CALCULADO");
        assertThat(CanonicalJson.json(result.get("observed"))).isEqualTo("[[0,0,0],[0,20,10],[0,10,20]]");
        assertThat(CanonicalJson.json(result.get("active_observed"))).isEqualTo("[[20,10],[10,20]]");
        assertThat(result.get("removed_rows")).isEqualTo(List.of(0));
        assertThat(result.get("removed_columns")).isEqualTo(List.of(0));
        assertThat(number(result, "p")).isCloseTo(.009823274507519235, within(1e-14));
    }

    @Test
    void expectedFrequenciesBelowFiveSuppressTheAsymptoticTest() {
        var result = StatisticalMath.contingency(new long[][]{{1, 4}, {4, 1}}, true, false, .05);
        assertThat(result.get("status")).isEqualTo("FRECUENCIAS_INSUFICIENTES");
        assertThat(number(result, "min_expected")).isEqualTo(2.5);
        assertThat(number(result, "below_five_count")).isEqualTo(4);
        assertNoInference(result);
    }

    @Test
    void anUndocumentedIndependenceAssumptionCannotProduceAPValue() {
        var result = StatisticalMath.contingency(new long[][]{{20, 10}, {10, 20}}, false, false, .05);
        assertThat(result.get("status")).isEqualTo("INDEPENDENCIA_NO_DOCUMENTADA");
        assertThat(matrixNumber(result.get("expected"), 0, 0)).isEqualTo(15);
        assertNoInference(result);
    }

    @Test
    void declaredStructuralZerosMakeTheOrdinaryPearsonTestIncompatible() {
        var result = StatisticalMath.contingency(new long[][]{{20, 10}, {10, 20}}, true, true, .05);
        assertThat(result.get("status")).isEqualTo("ESTRUCTURA_INCOMPATIBLE");
        assertNoInference(result);
    }

    @Test
    void allZeroCountsHaveNoInferenceAndContainNoUndefinedFloatingPointValues() {
        var result = StatisticalMath.contingency(new long[][]{{0, 0}, {0, 0}}, true, false, .05);
        assertThat(result.get("status")).isEqualTo("SIN_DATOS");
        assertThat(number(result, "n")).isZero();
        assertNoInference(result);
        assertThat(CanonicalJson.json(result)).doesNotContain("NaN", "Infinity");
    }

    @Test
    void aTableWithOnlyOneActiveLevelHasNoDegreesOfFreedomForInference() {
        var result = StatisticalMath.contingency(new long[][]{{10, 20}}, true, false, .05);
        assertThat(result.get("status")).isEqualTo("SIN_VARIACION");
        assertNoInference(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"negative", "ragged", "null-row"})
    void invalidCountTablesAreRejectedInsteadOfBeingRoundedOrFilled(String invalidCase) {
        long[][] counts = switch (invalidCase) {
            case "negative" -> new long[][]{{-1, 20}, {10, 30}};
            case "ragged" -> new long[][]{{10, 20}, {30}};
            default -> new long[][]{{10, 20}, null};
        };
        assertThatThrownBy(() -> StatisticalMath.contingency(counts, true, false, .05))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void holmUsesThePlannedFamilyAndKeepsResultsInTheOriginalOrder() {
        var result = StatisticalMath.holm(List.of(.01, .04, .03), 3, .05);
        assertThat(number(result, "planned_count")).isEqualTo(3);
        assertThat(number(result, "executed_count")).isEqualTo(3);
        List<?> adjusted = (List<?>) result.get("adjusted_p");
        assertThat(((Number) adjusted.get(0)).doubleValue()).isCloseTo(.03, within(1e-15));
        assertThat(((Number) adjusted.get(1)).doubleValue()).isCloseTo(.06, within(1e-15));
        assertThat(((Number) adjusted.get(2)).doubleValue()).isCloseTo(.06, within(1e-15));
        assertThat(result.get("original_p")).isEqualTo(List.of(.01, .04, .03));
        assertThat(result.get("reject_h0")).isEqualTo(List.of(true, false, false));
    }

    @Test
    void holmDoesNotDropUnexecutedPlannedComparisonsFromItsMultiplicityFactor() {
        var result = StatisticalMath.holm(Arrays.asList(.01, null, .04, .03), 10, .05);
        assertThat(number(result, "planned_count")).isEqualTo(10);
        assertThat(number(result, "executed_count")).isEqualTo(3);
        List<?> adjusted = (List<?>) result.get("adjusted_p");
        assertThat(((Number) adjusted.get(0)).doubleValue()).isCloseTo(.1, within(1e-15));
        assertThat(adjusted.get(1)).isNull();
        assertThat(((Number) adjusted.get(2)).doubleValue()).isCloseTo(.32, within(1e-15));
        assertThat(((Number) adjusted.get(3)).doubleValue()).isCloseTo(.27, within(1e-15));
        assertThat(result.get("reject_h0")).isEqualTo(Arrays.asList(false, null, false, false));
    }

    @Test
    void holmBoundsAdjustedProbabilitiesAtOne() {
        var result = StatisticalMath.holm(List.of(.8, .9), 2, .05);
        assertThat(result.get("adjusted_p")).isEqualTo(List.of(1d, 1d));
        assertThat(result.get("reject_h0")).isEqualTo(List.of(false, false));
    }

    @Test
    void decisionsUseFullPrecisionInsteadOfTheRoundedPValueDisplayedToTheReader() {
        assertThat(StatisticalMath.holm(List.of(.0496), 1, .05).get("reject_h0")).isEqualTo(List.of(true));
        assertThat(StatisticalMath.holm(List.of(.0504), 1, .05).get("reject_h0")).isEqualTo(List.of(false));
        assertThat(StatisticalMath.holm(List.of(.05), 1, .05).get("reject_h0")).isEqualTo(List.of(false));
    }

    @Test
    void anUnexecutedFamilyHasNullResultsRatherThanZeroPValues() {
        var result = StatisticalMath.holm(Arrays.asList(null, null), 10, .05);
        assertThat(number(result, "executed_count")).isZero();
        assertThat(result.get("adjusted_p")).isEqualTo(Arrays.asList(null, null));
        assertThat(result.get("reject_h0")).isEqualTo(Arrays.asList(null, null));
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY})
    void holmRejectsValuesOutsideTheProbabilityDomain(double invalid) {
        assertThatThrownBy(() -> StatisticalMath.holm(List.of(invalid), 1, .05))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePlannedFamilyCannotBeSmallerThanTheExecutedComparisons() {
        assertThatThrownBy(() -> StatisticalMath.holm(List.of(.01, .03), 1, .05))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, 1, -.1, Double.NaN})
    void alphaMustBeAFiniteProbabilityStrictlyBetweenZeroAndOne(double alpha) {
        assertThatThrownBy(() -> StatisticalMath.contingency(new long[][]{{20, 10}, {10, 20}}, true, false, alpha))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StatisticalMath.holm(List.of(.01), 1, alpha))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertNoInference(Map<String, Object> result) {
        for (String key : List.of("chi_square", "df", "p", "cramers_v", "reject_h0")) assertThat(result.get(key)).as(key).isNull();
        assertThat(result.get("reason")).isNotNull().asString().isNotBlank();
    }

    private static double number(Map<String, Object> result, String key) {
        return ((Number) result.get(key)).doubleValue();
    }

    private static double matrixNumber(Object matrix, int row, int column) {
        Object selectedRow = matrix instanceof List<?> list ? list.get(row) : Array.get(matrix, row);
        Object cell = selectedRow instanceof List<?> list ? list.get(column) : Array.get(selectedRow, column);
        return ((Number) cell).doubleValue();
    }
}

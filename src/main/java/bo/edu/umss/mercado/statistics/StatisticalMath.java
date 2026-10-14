package bo.edu.umss.mercado.statistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.statistics.inference.ChiSquareTest;

/** Convenciones numéricas de PROTOCOLO_ESTADISTICO_V1; sin inferencias sobre el diseño de campo. */
public final class StatisticalMath {
    private StatisticalMath() {}

    public static Map<String, Object> numeric(List<Double> values) {
        if (values == null || values.stream().anyMatch(x -> x == null || !Double.isFinite(x))) {
            throw new IllegalArgumentException("Las medidas requieren una lista de valores finitos.");
        }
        int n = values.size();
        var result = map("status", n == 0 ? "SIN_DATOS" : "CALCULADO", "n", n,
                "mean", null, "median", null, "sample_variance", null, "sample_standard_deviation", null,
                "min", null, "max", null, "q1", null, "q3", null, "iqr", null,
                "reason", n == 0 ? "SIN_VALORES_UTILIZABLES" : n < 2 ? "MUESTRA_MENOR_2" : "",
                "quantile_method", "TIPO_7", "variance_divisor", "N_MENOS_1");
        if (n == 0) return result;
        double mean = 0, m2 = 0;
        int count = 0;
        for (double value : values) {
            count++;
            double delta = value - mean;
            mean += delta / count;
            m2 += delta * (value - mean);
        }
        double[] ordered = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double q1 = quantile(ordered, .25), q3 = quantile(ordered, .75);
        result.put("mean", finite(mean));
        result.put("median", finite(quantile(ordered, .5)));
        result.put("min", ordered[0]);
        result.put("max", ordered[n - 1]);
        result.put("q1", finite(q1));
        result.put("q3", finite(q3));
        result.put("iqr", finite(q3 - q1));
        if (n >= 2) {
            double variance = Math.max(0, m2 / (n - 1));
            result.put("sample_variance", finite(variance));
            result.put("sample_standard_deviation", finite(Math.sqrt(variance)));
        }
        if (!Double.isFinite(mean) || !Double.isFinite(q3 - q1) || (n >= 2 && !Double.isFinite(m2))) {
            result.put("status", "ERROR_CALCULO");
            result.put("reason", "RESULTADO_NO_FINITO");
        }
        return result;
    }

    private static double quantile(double[] sorted, double p) {
        double h = 1 + (sorted.length - 1) * p;
        int j = (int) Math.floor(h);
        if (j >= sorted.length) return sorted[sorted.length - 1];
        double fraction = h - j;
        return (1 - fraction) * sorted[j - 1] + fraction * sorted[j];
    }

    public static Map<String, Object> contingency(long[][] counts, boolean independenceDocumented,
                                                  boolean structuralZeros, double alpha) {
        validateAlpha(alpha);
        if (counts == null) throw new IllegalArgumentException("La tabla no puede ser nula.");
        int rows = counts.length, cols = rows == 0 ? 0 : counts[0] == null ? -1 : counts[0].length;
        if (cols < 0 || Arrays.stream(counts).anyMatch(row -> row == null || row.length != cols)) {
            throw new IllegalArgumentException("La tabla debe ser rectangular.");
        }
        long[] rt = new long[rows], ct = new long[cols];
        long n = 0;
        try {
            for (int i = 0; i < rows; i++) for (int j = 0; j < cols; j++) {
                if (counts[i][j] < 0) throw new IllegalArgumentException("Los conteos no pueden ser negativos.");
                rt[i] = Math.addExact(rt[i], counts[i][j]);
                ct[j] = Math.addExact(ct[j], counts[i][j]);
                n = Math.addExact(n, counts[i][j]);
            }
        } catch (ArithmeticException ex) { throw new IllegalArgumentException("Los conteos exceden la capacidad entera.", ex); }
        var expected = new ArrayList<List<Double>>();
        var rp = new ArrayList<List<Double>>();
        var cp = new ArrayList<List<Double>>();
        var tp = new ArrayList<List<Double>>();
        for (int i = 0; i < rows; i++) {
            var er = new ArrayList<Double>(); var rr = new ArrayList<Double>();
            var cr = new ArrayList<Double>(); var tr = new ArrayList<Double>();
            for (int j = 0; j < cols; j++) {
                er.add(n == 0 ? null : ((double) rt[i] / n) * ct[j]);
                rr.add(rt[i] == 0 ? null : 100d * counts[i][j] / rt[i]);
                cr.add(ct[j] == 0 ? null : 100d * counts[i][j] / ct[j]);
                tr.add(n == 0 ? null : 100d * counts[i][j] / n);
            }
            expected.add(er); rp.add(rr); cp.add(cr); tp.add(tr);
        }
        var ar = new ArrayList<Integer>(); var ac = new ArrayList<Integer>();
        var removedRows = new ArrayList<Integer>(); var removedCols = new ArrayList<Integer>();
        for (int i = 0; i < rows; i++) if (rt[i] > 0) ar.add(i); else removedRows.add(i);
        for (int j = 0; j < cols; j++) if (ct[j] > 0) ac.add(j); else removedCols.add(j);
        long[][] active = new long[ar.size()][ac.size()];
        Double minimum = null;
        int belowFive = 0;
        for (int i = 0; i < ar.size(); i++) for (int j = 0; j < ac.size(); j++) {
            active[i][j] = counts[ar.get(i)][ac.get(j)];
            double e = expected.get(ar.get(i)).get(ac.get(j));
            minimum = minimum == null ? e : Math.min(minimum, e);
            if (e < 5) belowFive++;
        }
        var result = map("status", "CALCULADO", "reason", "", "observed", longs(counts), "expected", expected,
                "row_totals", Arrays.stream(rt).boxed().toList(), "column_totals", Arrays.stream(ct).boxed().toList(), "n", n,
                "row_percentages", rp, "column_percentages", cp, "total_percentages", tp,
                "removed_rows", removedRows, "removed_columns", removedCols, "active_rows", ar, "active_columns", ac,
                "active_observed", longs(active), "min_expected", minimum, "below_five_count", belowFive,
                "expected_policy", "TODAS_ESPERADAS_GE_5", "chi_square", null, "df", null, "p", null,
                "cramers_v", null, "reject_h0", null, "alpha", alpha, "continuity_correction", false,
                "independence_documented", independenceDocumented, "structural_zeros", structuralZeros);
        if (n == 0) return status(result, "SIN_DATOS", "SIN_PARES_VALIDOS");
        if (ar.size() < 2 || ac.size() < 2) return status(result, "SIN_VARIACION", "MENOS_DE_DOS_CATEGORIAS_ACTIVAS");
        if (!independenceDocumented) return status(result, "INDEPENDENCIA_NO_DOCUMENTADA", "FALTA_UNIDAD_Y_EVIDENCIA_DE_INDEPENDENCIA");
        if (structuralZeros) return status(result, "ESTRUCTURA_INCOMPATIBLE", "CEROS_ESTRUCTURALES_DECLARADOS");
        if (belowFive > 0) return status(result, "FRECUENCIAS_INSUFICIENTES", "ALGUNA_ESPERADA_MENOR_5");
        try {
            var test = ChiSquareTest.withDefaults().test(active);
            double chi = test.getStatistic(), p = test.getPValue();
            double v = Math.sqrt(chi / n / Math.min(ar.size() - 1, ac.size() - 1));
            if (!Double.isFinite(chi) || !Double.isFinite(p) || !Double.isFinite(v) || p < 0 || p > 1) {
                return status(result, "ERROR_CALCULO", "RESULTADO_NO_FINITO");
            }
            result.put("chi_square", chi); result.put("df", (ar.size() - 1) * (ac.size() - 1));
            result.put("p", p); result.put("cramers_v", v); result.put("reject_h0", p < alpha);
            return result;
        } catch (RuntimeException ex) { return status(result, "ERROR_CALCULO", "ERROR_BIBLIOTECA_ESTADISTICA"); }
    }

    public static Map<String, Object> holm(List<Double> pValues, int plannedCount, double alpha) {
        validateAlpha(alpha);
        if (pValues == null || plannedCount < pValues.size() || plannedCount < 0
                || pValues.stream().anyMatch(p -> p != null && (!Double.isFinite(p) || p < 0 || p > 1))) {
            throw new IllegalArgumentException("La familia y los valores p deben ser válidos.");
        }
        var indices = new ArrayList<Integer>();
        for (int i = 0; i < pValues.size(); i++) if (pValues.get(i) != null) indices.add(i);
        indices.sort(Comparator.<Integer>comparingDouble(i -> pValues.get(i)).thenComparingInt(i -> i));
        var adjusted = new ArrayList<Double>(java.util.Collections.nCopies(pValues.size(), null));
        var decisions = new ArrayList<Boolean>(java.util.Collections.nCopies(pValues.size(), null));
        double running = 0;
        for (int rank = 0; rank < indices.size(); rank++) {
            int index = indices.get(rank);
            running = Math.min(1, Math.max(running, (plannedCount - rank) * pValues.get(index)));
            adjusted.set(index, running);
            decisions.set(index, running < alpha);
        }
        return map("status", indices.isEmpty() ? "SIN_CONTRASTES_CALCULADOS" : "CALCULADO", "method", "HOLM",
                "planned_count", plannedCount, "executed_count", indices.size(), "original_p", new ArrayList<>(pValues),
                "adjusted_p", adjusted, "reject_h0", decisions, "alpha", alpha);
    }

    static Map<String, Object> map(Object... pairs) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    static Double finite(double value) { return Double.isFinite(value) ? value : null; }
    private static void validateAlpha(double alpha) {
        if (!Double.isFinite(alpha) || alpha <= 0 || alpha >= 1) throw new IllegalArgumentException("Alpha debe estar entre cero y uno.");
    }
    private static Map<String, Object> status(Map<String, Object> result, String status, String reason) {
        result.put("status", status); result.put("reason", reason); return result;
    }
    private static List<List<Long>> longs(long[][] values) {
        return Arrays.stream(values).map(row -> Arrays.stream(row).boxed().toList()).toList();
    }
}

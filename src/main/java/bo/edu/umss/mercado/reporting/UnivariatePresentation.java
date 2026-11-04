package bo.edu.umss.mercado.reporting;

import java.util.*;
import java.util.stream.Collectors;

/** Labels and formulae for the frozen summary; does not calculate or change results. */
public final class UnivariatePresentation {
    private UnivariatePresentation() {}
    public record Measure(String label,String formula,Object value,String unit) {
        public String display(){return value==null?"Sin datos suficientes o no definido":value instanceof Number number?String.format(Locale.ROOT,"%.2f",number.doubleValue()):value.toString();}
    }
    public record Group(String title,List<Measure> measures) {}
    public static final String QUANTILES="Cuantiles tipo 7: con datos ordenados x(1)…x(n), h = 1 + (n−1)p, j = piso(h), g = h−j; Q(p) = (1−g)x(j) + g·x(j+1). Si j = n, Q(p) = x(n). n es la base numérica con valor; las fórmulas no usan los códigos de categorías.";
    @SuppressWarnings("unchecked")
    public static List<Group> groups(Map<String,Object> summary){
        var values=new LinkedHashMap<String,Map<String,Object>>();
        for(var item:(List<Map<String,Object>>)summary.get("measures"))values.put((String)item.get("key"),item);
        var central=new ArrayList<Measure>();
        central.add(measure(values,"mean","x̄ = Σxi / n"));central.add(measure(values,"median","Me = Q(0,50), datos ordenados"));
        var modes=(List<?>)summary.get("modes");Object mode="CALCULADO".equals(summary.get("mode_status"))?modes.stream().map(Object::toString).collect(Collectors.joining(", ")):"SIN_DATOS".equals(summary.get("mode_status"))?null:"Sin moda: ningún valor se repite";
        central.add(new Measure("Moda (Mo)","Valor(es) con frecuencia máxima, si fi > 1",mode,Objects.toString(summary.get("unit"),"")));
        return List.of(new Group("Medidas de tendencia central",central),
            new Group("Medidas de dispersión",List.of(measure(values,"min","Mín = menor valor observado"),measure(values,"max","Máx = mayor valor observado"),measure(values,"range","R = Máx − Mín"),measure(values,"sample_variance","s² = Σ(xi − x̄)² / (n − 1)"),measure(values,"sample_standard_deviation","s = √s²"),measure(values,"cv","CV = (s / x̄) × 100"),measure(values,"iqr","RIC = Q3 − Q1"))),
            new Group("Medidas de posición",List.of(measure(values,"q1","Q1 = P25 = Q(0,25)"),measure(values,"q3","Q3 = P75 = Q(0,75)"),measure(values,"p90","P90 = Q(0,90)"))));
    }
    private static Measure measure(Map<String,Map<String,Object>> values,String key,String formula){var item=values.get(key);return new Measure((String)item.get("label"),formula,item.get("value"),(String)item.get("unit"));}
}

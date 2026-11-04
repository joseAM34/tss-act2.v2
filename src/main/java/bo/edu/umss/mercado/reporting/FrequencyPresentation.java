package bo.edu.umss.mercado.reporting;

import java.util.*;

/** Names and relative-frequency display derived from frozen aggregate counts, without changing their protocol. */
public final class FrequencyPresentation {
    private FrequencyPresentation() {}
    public static final String HELP="Frecuencia absoluta (fi): cantidad de envíos de cada opción o estado. Base de cálculo (n): envíos usados como denominador, indicados en la tabla. Frecuencia relativa (hi) = fi / n. Porcentaje (%) = hi × 100. La frecuencia relativa se muestra con cuatro decimales y el porcentaje con dos; se redondean por separado. Sin base indica que no hay denominador disponible.";
    public static final String MULTIPLE_HELP="Una selección es una opción marcada. En selección múltiple un envío puede aportar varias selecciones: los porcentajes por opción sobre n pueden sumar más de 100%. Total de selecciones (m) es la suma de opciones marcadas; la frecuencia relativa por selección usa fi / m y su porcentaje usa 100 × fi / m.";
    public static final String CUMULATIVE_HELP="Frecuencia absoluta (fi): veces que aparece el valor. Frecuencia relativa (hi) = fi / n. Frecuencia acumulada (Fi): suma de las frecuencias absolutas hasta ese valor, en orden numérico. Porcentaje (%) = 100 × hi; porcentaje acumulado = 100 × Fi / n.";
    public record Row(String label,Object count,Object base,Double relative,Object percent,Object selections,Double selectionRelative,Object selectionPercent) {
        public String relativeText(){return number(relative,4);}
        public String percentText(){return number(percent,2);}
        public String selectionRelativeText(){return number(selectionRelative,4);}
        public String selectionPercentText(){return number(selectionPercent,2);}
    }
    public static String number(Object value,int decimals){return value instanceof Number n&&Double.isFinite(n.doubleValue())?String.format(Locale.ROOT,"%."+decimals+"f",n.doubleValue()):"Sin base";}
    public static Double relative(Object count,Object base){
        if(!(count instanceof Number c)||!(base instanceof Number b)||b.doubleValue()<=0)return null;
        double value=c.doubleValue()/b.doubleValue();return Double.isFinite(value)?value:null;
    }
    @SuppressWarnings("unchecked")
    public static List<Row> rows(Map<String,Object> variable){
        return ((List<Map<String,Object>>)variable.getOrDefault("distribution",List.of())).stream()
            .map(o->new Row(Objects.toString(o.get("label"),"Sin datos"),o.get("count"),o.get("respondent_base"),relative(o.get("count"),o.get("respondent_base")),o.get("respondent_percent"),o.get("mention_base"),relative(o.get("count"),o.get("mention_base")),o.get("mention_percent"))).toList();
    }
}

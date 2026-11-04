package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.statistics.StatisticalMath;
import java.util.*;

/** Presentation protocol derived only from numeric values of a verified frozen preparation. */
public final class UnivariateSummary {
    private UnivariateSummary() {}
    private static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private static Double finite(double value){return Double.isFinite(value)?value:null;}
    public static Map<String,Object> calculate(List<Double> values,String unit){
        var stats=StatisticalMath.numeric(values);int n=values.size();
        var counts=new TreeMap<Double,Integer>();values.forEach(v->counts.merge(v==0?0d:v,1,Integer::sum));
        int maximum=counts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var modes=maximum<=1?List.<Double>of():counts.entrySet().stream().filter(e->e.getValue()==maximum).map(Map.Entry::getKey).toList();
        var measures=new ArrayList<Map<String,Object>>();
        for(String key:List.of("min","max","mean","median","sample_variance","sample_standard_deviation","q1","q3","iqr")){
            String label=switch(key){case "min"->"Mínimo";case "max"->"Máximo";case "mean"->"Media";case "median"->"Mediana (Q2 / P50)";case "sample_variance"->"Varianza muestral";case "sample_standard_deviation"->"Desviación estándar muestral";case "q1"->"Primer cuartil (Q1 / P25)";case "q3"->"Tercer cuartil (Q3 / P75)";default->"Rango intercuartílico";};
            measures.add(map("key",key,"label",label,"value",stats.get(key),"unit",key.equals("sample_variance")?unit+"²":unit));
        }
        measures.add(map("key","range","label","Rango","value",n==0?null:finite(((Number)stats.get("max")).doubleValue()-((Number)stats.get("min")).doubleValue()),"unit",unit));
        var sorted=values.stream().sorted().toList();Double p90=null;
        if(n>0){double index=(n-1)*.9;int lower=(int)Math.floor(index),upper=(int)Math.ceil(index);p90=finite(sorted.get(lower)+(sorted.get(upper)-sorted.get(lower))*(index-lower));}
        measures.add(map("key","p90","label","Percentil 90 (P90)","value",p90,"unit",unit));
        Double cv=null;double mean=stats.get("mean") instanceof Number number?number.doubleValue():0;
        boolean ratioUnit=Set.of("segundos","minutos","horas","días","dias","meses","años","años completos","meses completos","días completos","dias completos").contains(unit.strip().toLowerCase(Locale.ROOT));
        if(ratioUnit&&n>=2&&mean>0&&stats.get("sample_standard_deviation") instanceof Number sd)cv=finite(sd.doubleValue()/mean*100);
        measures.add(map("key","cv","label","Coeficiente de variación muestral","value",cv,"unit","%"));
        var frequencies=new ArrayList<Map<String,Object>>();int cumulative=0;
        for(var e:counts.entrySet()){cumulative+=e.getValue();frequencies.add(map("value",e.getKey(),"count",e.getValue(),"relative",e.getValue()/(double)n,"percent",100d*e.getValue()/n,"cumulative",cumulative,"cumulative_percent",100d*cumulative/n));}
        var intervals=new ArrayList<Map<String,Object>>();String intervalMethod="Sin valores numéricos.";
        if(n>0){var histogram=ReportAdvancedProjection.histogram(values);intervalMethod=histogram.get("method").toString();int accumulated=0;
            for(Object raw:(List<?>)histogram.get("bins")){var bin=(Map<?,?>)raw;int count=((Number)bin.get("count")).intValue();accumulated+=count;double lower=((Number)bin.get("lower")).doubleValue(),upper=((Number)bin.get("upper")).doubleValue();intervals.add(map("lower",lower,"upper",upper,"closed_upper",bin.get("closed_upper"),"midpoint",finite(lower+(upper-lower)/2),"count",count,"relative",count/(double)n,"percent",100d*count/n,"cumulative",accumulated,"cumulative_percent",100d*accumulated/n));}
        }
        return map("version",1,"n",n,"unit",unit,"measures",measures,"modes",modes,"mode_frequency",maximum,
            "mode_status",n==0?"SIN_DATOS":maximum<=1?"SIN_REPETICIONES":"CALCULADO","frequencies",frequencies,"intervals",intervals,"interval_method",intervalMethod,
            "method","Base: valores numéricos con respuesta; faltantes y no aplica excluidos. Varianza y desviación muestrales: divisor n−1. Cuartiles y P90: interpolación tipo 7. Moda: todos los valores de frecuencia máxima si se repiten; sin repeticiones no se declara moda. CV = 100·s/media, solo con n≥2, media positiva y unidades reconocidas de duración/edad con cero significativo (segundos, minutos, horas, días, meses, años; también completos). Para año calendario u otras unidades no se declara CV. Sin clasificación automática de calidad, representatividad ni valores atípicos.");
    }
}

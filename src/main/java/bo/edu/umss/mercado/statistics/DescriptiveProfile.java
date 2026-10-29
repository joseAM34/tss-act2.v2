package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.reporting.ReportAdvancedProjection;
import java.text.Normalizer;
import java.util.*;

/** Versioned presentation projection; never changes historical analysis protocols or snapshots. */
public final class DescriptiveProfile {
    public static final String PROTOCOL="PERFIL_DESCRIPTIVO_V1";
    private DescriptiveProfile(){}
    private static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private static Double percent(long count,long base){return base==0?null:100d*count/base;}
    @SuppressWarnings("unchecked")
    public static Map<String,Object> enrich(Map<String,Object> result,Map<String,Object> logical){
        var matrix=(List<Map<String,Object>>)logical.get("matrix");var enriched=new LinkedHashMap<>(result);var variables=new ArrayList<Map<String,Object>>();
        for(var original:(List<Map<String,Object>>)result.get("variables")){
            var v=new LinkedHashMap<>(original);String id=(String)v.get("question_id");
            if("MAGNITUD_ENTERA".equals(v.get("treatment"))){
                var numbers=matrix.stream().filter(r->"CON_VALOR".equals(((Map<?,?>)r.get("states")).get(id))).map(r->((Map<?,?>)r.get("values")).get(id)).map(n->{if(!(n instanceof Number number)||!Double.isFinite(number.doubleValue()))throw new IllegalStateException("Magnitud no finita en el conjunto.");return number.doubleValue();}).sorted().toList();
                v.put("quality",numeric(numbers,Objects.toString(v.get("unit"),"")));
            }else if("NOMINAL".equals(v.get("treatment"))){
                var distribution=(List<Map<String,Object>>)v.get("distribution");int max=distribution.stream().mapToInt(o->((Number)o.get("count")).intValue()).max().orElse(0);
                var modes=distribution.stream().filter(o->max>0&&((Number)o.get("count")).intValue()==max).map(o->o.get("label").toString()).toList();
                boolean tied=modes.size()==distribution.size()&&distribution.size()>1;
                var categorical=distribution.stream().map(o->{long count=((Number)o.get("count")).longValue(),base=((Number)o.get("respondent_base")).longValue(),mentions=((Number)o.get("mention_base")).longValue();return map("label",o.get("label"),"count",count,"base",base,"relative",base==0?null:count/(double)base,"percentage",percent(count,base),"mention_base",mentions,"mention_relative",mentions==0?null:count/(double)mentions);}).toList();
                v.put("quality",map("protocol",PROTOCOL,"kind","NOMINAL","categorical",categorical,"measures",List.of(map("label",v.get("type").equals("checkbox")?"Opciones más seleccionadas":"Moda categórica","value",max==0?"Sin respuestas":tied?"Sin moda: todas las categorías tienen igual frecuencia":String.join("; ",modes)),map("label","Frecuencia máxima por opción","value",max)),"method","Frecuencias y porcentajes conservan sus bases de envíos y menciones. Sin acumulación sobre categorías nominales ni puntajes de acuerdo. No sabe es una categoría propia. En selección múltiple, las frecuencias relativas por envío pueden sumar más de 1."));
            }
            variables.add(v);
        }
        enriched.put("variables",variables);
        if(result.get("strata") instanceof List<?> strata)enriched.put("strata",strata.stream().map(s->{var stratum=(Map<String,Object>)s;var selected=new LinkedHashMap<>(logical);selected.put("matrix",matrix.stream().filter(r->Objects.equals(r.get("origin"),stratum.get("origin"))).toList());return enrich(stratum,selected);}).toList());
        return enriched;
    }
    public static Map<String,Object> numeric(List<Double> values,String unit){
        var stats=StatisticalMath.numeric(values);var ordered=values.stream().sorted().toList();int n=ordered.size();
        var frequencies=new TreeMap<Double,Integer>();ordered.forEach(x->frequencies.merge(x,1,Integer::sum));
        var table=new ArrayList<Map<String,Object>>();int cumulative=0,max=frequencies.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for(var entry:frequencies.entrySet()){cumulative+=entry.getValue();table.add(map("value",entry.getKey(),"count",entry.getValue(),"relative",entry.getValue()/(double)n,"percentage",percent(entry.getValue(),n),"cumulative",cumulative,"cumulative_percent",percent(cumulative,n),"base",n));}
        var modes=frequencies.entrySet().stream().filter(e->e.getValue()==max).map(Map.Entry::getKey).toList();
        Double mean=(Double)stats.get("mean"),sd=(Double)stats.get("sample_standard_deviation");
        boolean ratio=ratioUnit(unit);Double cv=ratio&&sd!=null&&mean!=null&&mean>0&&ordered.getFirst()>=0?100d*sd/mean:null;
        var measures=new ArrayList<Map<String,Object>>();
        measures.add(map("label","Valores numéricos (n)","value",n));
        for(String key:List.of("min","max","mean","median","sample_variance","sample_standard_deviation","q1","q3","iqr"))measures.add(map("label",switch(key){case "min"->"Mínimo";case "max"->"Máximo";case "mean"->"Media";case "median"->"Mediana / Q2 / P50";case "sample_variance"->"Varianza muestral (unidad al cuadrado)";case "sample_standard_deviation"->"Desviación estándar muestral";case "q1"->"Q1 / P25";case "q3"->"Q3 / P75";default->"Rango intercuartílico";},"value",stats.get(key)));
        measures.add(map("label","Rango","value",n==0?null:ordered.getLast()-ordered.getFirst()));
        measures.add(map("label","Moda","value",n==0?"Sin datos":modes.size()==frequencies.size()&&frequencies.size()>1?"Sin moda: todos los valores tienen igual frecuencia":String.join("; ",modes.stream().map(x->java.math.BigDecimal.valueOf(x).stripTrailingZeros().toPlainString()).toList())));
        measures.add(map("label","Coeficiente de variación (%)","value",cv));
        measures.add(map("label","Percentil 90 (tipo 7)","value",n==0?null:quantile(ordered,.9)));
        Double lower=n==0?null:(Double)stats.get("q1")-1.5*(Double)stats.get("iqr"),upper=n==0?null:(Double)stats.get("q3")+1.5*(Double)stats.get("iqr");
        var outliers=frequencies.entrySet().stream().filter(e->lower!=null&&(e.getKey()<lower||e.getKey()>upper)).map(e->map("value",e.getKey(),"count",e.getValue())).toList();
        measures.add(map("label","Observaciones fuera de Q1 − 1,5·RIC y Q3 + 1,5·RIC","value",outliers.stream().mapToInt(o->(Integer)o.get("count")).sum()));
        var bins=new ArrayList<Map<String,Object>>();
        if(n>0){@SuppressWarnings("unchecked") var raw=(List<Map<String,Object>>)ReportAdvancedProjection.histogram(ordered).get("bins");int acc=0;for(var b:raw){int count=(Integer)b.get("count");acc+=count;var row=new LinkedHashMap<>(b);row.putAll(map("midpoint",((Double)b.get("lower")+(Double)b.get("upper"))/2,"relative",count/(double)n,"percentage",percent(count,n),"cumulative",acc,"cumulative_percent",percent(acc,n),"base",n));bins.add(row);}}
        return map("protocol",PROTOCOL,"kind","NUMERIC","n",n,"measures",measures,"frequencies",table,"intervals",bins,"outliers",outliers,"cv_reason",cv!=null?"Calculado sobre una magnitud no negativa con unidad de razón reconocida, media positiva y n ≥ 2.":"No calculable: requiere n ≥ 2, valores no negativos, media positiva y una unidad de razón reconocida.",
                "method","Base: valores numéricos con respuesta de esta pregunta; sin respuesta y no aplica se excluyen. Varianza muestral: n−1. Cuartiles y P90: tipo 7. Intervalos Sturges, máximo 20; [inferior, superior), último cerrado. Atípicos: regla 1,5·RIC, no errores ni exclusiones automáticas. Caja existente: cinco valores con bigotes mínimo/máximo. Sin validación automática de representatividad ni calidad del servicio.");
    }
    private static double quantile(List<Double> sorted,double p){double index=(sorted.size()-1)*p;int lo=(int)Math.floor(index),hi=(int)Math.ceil(index);return sorted.get(lo)+(sorted.get(hi)-sorted.get(lo))*(index-lo);}
    private static boolean ratioUnit(String unit){String normalized=Normalizer.normalize(unit.strip().toLowerCase(Locale.ROOT),Normalizer.Form.NFD).replaceAll("\\p{M}","");return Set.of("ano","anos","anos completos","mes","meses","meses completos","dia","dias","hora","horas","minuto","minutos","min","segundo","segundos","s","kg","kilogramos","gramos","g","metros","m","centimetros","cm","bolivianos","bs","usd").contains(normalized);}
}

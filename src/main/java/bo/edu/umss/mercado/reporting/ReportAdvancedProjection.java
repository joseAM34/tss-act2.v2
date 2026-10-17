package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.statistics.StatisticalMath;
import java.time.*;
import java.util.*;

/** Advanced graphics computed exclusively from the verified frozen preparation. */
public final class ReportAdvancedProjection {
    private ReportAdvancedProjection() {}
    private static Map<String,Object> map(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> rows(Map<String,Object> logical){return (List<Map<String,Object>>)logical.get("matrix");}
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> dictionary(Map<String,Object> logical){return (List<Map<String,Object>>)logical.get("dictionary");}
    @SuppressWarnings("unchecked") private static Object value(Map<String,Object> row,String id){return "CON_VALOR".equals(((Map<?,?>)row.get("states")).get(id))?((Map<String,Object>)row.get("values")).get(id):null;}
    private static List<Double> numbers(Map<String,Object> logical,String id){return rows(logical).stream().map(r->value(r,id)).filter(Number.class::isInstance).map(n->((Number)n).doubleValue()).sorted().toList();}
    private static Map<String,Object> field(Map<String,Object> logical,String id){return dictionary(logical).stream().filter(q->id.equals(q.get("question_id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Pregunta ajena al conjunto."));}
    private static List<String> panel(int first){return java.util.stream.IntStream.range(first,first+5).mapToObj(i->"criterio_%02d".formatted(i)).toList();}
    @SuppressWarnings("unchecked")
    private static boolean radarCompatible(Map<String,Object> logical,List<String> ids){
        if(!ids.equals(panel(1))&&!ids.equals(panel(6)))return false;
        return ids.stream().allMatch(id->dictionary(logical).stream().anyMatch(q->id.equals(q.get("question_id"))&&"NOMINAL".equals(q.get("treatment"))&&Set.of("radio","select").contains(q.get("type"))&&
                ((List<Map<String,Object>>)q.get("options")).stream().map(o->o.get("value")).toList().equals(List.of("TOTALMENTE_ACUERDO","PARCIALMENTE_ACUERDO","TOTALMENTE_DESACUERDO","NO_SABE"))));
    }
    public static Map<String,Object> options(Map<String,Object> logical){
        return map("numeric",dictionary(logical).stream().filter(q->"MAGNITUD_ENTERA".equals(q.get("treatment"))).map(q->map("id",q.get("question_id"),"label",q.get("label"),"unit",q.get("unit"))).toList(),
                "radar",List.of(1,6).stream().filter(first->radarCompatible(logical,panel(first))).map(first->map("questions",panel(first),"title","Acuerdo de empleadores: criterios "+first+"–"+(first+4))).toList());
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> enrich(Map<String,Object> result,Map<String,Object> logical,String cutoff){
        var enriched=new LinkedHashMap<>(result);var variables=new ArrayList<Map<String,Object>>();
        for(var original:(List<Map<String,Object>>)result.get("variables")){
            var v=new LinkedHashMap<>(original);String id=(String)v.get("question_id");var advanced=new LinkedHashMap<String,Object>();
            if(v.get("numeric")!=null){var values=numbers(logical,id);if(!values.isEmpty())advanced.put("histogram",histogram(values));
                if(values.size()>=2){var stats=StatisticalMath.numeric(values);advanced.put("box",map("values",List.of(stats.get("min"),stats.get("q1"),stats.get("median"),stats.get("q3"),stats.get("max")),"n",values.size(),"method","Cuartiles tipo 7; bigotes mínimo/máximo; sin clasificación de atípicos."));}}
            if(!rows(logical).isEmpty()&&rows(logical).stream().allMatch(r->r.get("submitted_at") instanceof String)){
                var counts=new TreeMap<YearMonth,Integer>();for(var row:rows(logical)){var month=YearMonth.from(Instant.parse((String)row.get("submitted_at")).atZone(ZoneId.of("America/La_Paz")));counts.putIfAbsent(month,0);if(value(row,id)!=null)counts.merge(month,1,Integer::sum);}
                YearMonth start=logical.get("from_date")==null?counts.firstKey():YearMonth.from(LocalDate.parse(logical.get("from_date").toString()));
                YearMonth end=YearMonth.from(Instant.parse(cutoff).atZone(ZoneId.of("America/La_Paz")));
                if(logical.get("until_date")!=null){var selected=YearMonth.from(LocalDate.parse(logical.get("until_date").toString()));if(selected.isBefore(end))end=selected;}
                var months=new ArrayList<String>();var values=new ArrayList<Integer>();for(var month=start;!month.isAfter(end);month=month.plusMonths(1)){months.add(month.toString());values.add(counts.getOrDefault(month,0));}
                advanced.put("time",map("months",months,"counts",values,"method","Envíos con valor por mes de recepción, America/La_Paz. Extremos limitados por filtros/corte; no son meses completos ni una predicción."));
            }
            v.put("advanced",advanced);variables.add(v);
        }enriched.put("variables",variables);return enriched;
    }
    public static Map<String,Object> histogram(List<Double> values){
        if(values.isEmpty())throw new IllegalArgumentException("El histograma requiere valores.");
        double min=Collections.min(values),max=Collections.max(values);int k=min==max?1:Math.min(20,(int)Math.ceil(1+Math.log(values.size())/Math.log(2)));double width=(max-min)/k;
        var bins=new ArrayList<Map<String,Object>>();int[] counts=new int[k];for(double n:values)counts[min==max?0:Math.min(k-1,(int)((n-min)/width))]++;
        for(int i=0;i<k;i++)bins.add(map("lower",min+i*width,"upper",i==k-1?max:min+(i+1)*width,"closed_upper",i==k-1,"count",counts[i]));
        return map("bins",bins,"n",values.size(),"method","Sturges: techo(1+log2(n)), máximo 20 intervalos iguales. Intervalos [inferior, superior); último cerrado. Valor constante: un intervalo puntual.");
    }
    public static List<Map<String,Object>> charts(ReportCustomization.Configuration configuration,Map<String,Object> logical){
        if(configuration.advanced()==null)return List.of();var charts=new ArrayList<Map<String,Object>>();var keys=new HashSet<String>();
        for(var setting:configuration.advanced()){
            if(setting.questions()==null||setting.questions().size()<2||setting.questions().size()>5||new HashSet<>(setting.questions()).size()!=setting.questions().size())throw new IllegalArgumentException("Selecciona preguntas distintas para el gráfico avanzado.");
            String key=setting.type()+":"+String.join("|",setting.questions());if(!keys.add(key))throw new IllegalArgumentException("Gráfico avanzado repetido.");
            Map<String,Object> chart;
            if("scatter".equals(setting.type())){
                if(setting.questions().size()!=2)throw new IllegalArgumentException("La dispersión requiere dos magnitudes.");
                var x=field(logical,setting.questions().get(0));var y=field(logical,setting.questions().get(1));
                if(!"MAGNITUD_ENTERA".equals(x.get("treatment"))||!"MAGNITUD_ENTERA".equals(y.get("treatment")))throw new IllegalArgumentException("La dispersión requiere dos magnitudes numéricas.");
                var frequencies=new TreeMap<String,List<Number>>();int n=0;for(var row:rows(logical)){var a=value(row,setting.questions().get(0));var b=value(row,setting.questions().get(1));if(a instanceof Number ax&&b instanceof Number by){n++;String coordinate=ax.doubleValue()+"|"+by.doubleValue();var entry=frequencies.computeIfAbsent(coordinate,k->new ArrayList<>(List.of(ax.doubleValue(),by.doubleValue(),0)));entry.set(2,entry.get(2).intValue()+1);}}
                chart=map("type","scatter","x_label",x.get("label"),"y_label",y.get("label"),"x_unit",x.get("unit"),"y_unit",y.get("unit"),"points",new ArrayList<>(frequencies.values()),"n",n,"excluded",rows(logical).size()-n,"available",n>0,"method","Pares con valor del mismo envío; coordenadas iguales agrupadas con frecuencia. Sin identificadores, regresión ni inferencia de independencia.");
            }else if("radar".equals(setting.type())){
                if(!radarCompatible(logical,setting.questions()))throw new IllegalArgumentException("El radar requiere uno de los paneles originales de cinco criterios de empleadores y su escala exacta.");
                var criteria=new ArrayList<Map<String,Object>>();boolean complete=true;
                for(String id:setting.questions()){int agreement=0,base=0,unknown=0,missing=0,notApplicable=0;for(var row:rows(logical)){Object answer=value(row,id);if(answer==null){if("NO_APLICA".equals(((Map<?,?>)row.get("states")).get(id)))notApplicable++;else missing++;continue;}if("NO_SABE".equals(answer)){unknown++;continue;}base++;if(Set.of("TOTALMENTE_ACUERDO","PARCIALMENTE_ACUERDO").contains(answer))agreement++;}
                    complete&=base>0;criteria.add(map("label",field(logical,id).get("label"),"numerator",agreement,"base",base,"no_sabe",unknown,"missing",missing,"not_applicable",notApplicable,"percentage",base==0?null:agreement*100.0/base));}
                chart=map("type","radar","criteria",criteria,"available",complete,"method","Acuerdo parcial/total sobre respuestas sustantivas, base propia por criterio; No sabe separado. Ejes 0–100 %. Sin índice global ni relleno de series incompletas.");
            }else throw new IllegalArgumentException("Tipo de gráfico avanzado no admitido.");
            chart.putAll(map("id",key,"title",setting.title(),"comment",setting.comment()));if(setting.included())charts.add(chart);
        }return charts;
    }
}

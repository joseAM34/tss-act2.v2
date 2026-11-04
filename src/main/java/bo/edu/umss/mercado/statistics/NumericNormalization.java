package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.reporting.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Explicit, reviewed full-answer mappings. Never applies thematic token normalization. */
public final class NumericNormalization {
    private NumericNormalization() {}
    public static String key(String text) { return Normalizer.normalize(text,Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," "); }
    public static Map<String,Object> definition(boolean enabled,String unit,String rules) {
        if(!Set.of("años","meses").contains(unit))throw new IllegalArgumentException("Selecciona años o meses.");
        if(rules.length()>262144)throw new IllegalArgumentException("Demasiadas equivalencias numéricas.");
        var mappings=new LinkedHashMap<String,Double>();
        for(String line:rules.split("\\R")) {
            if(line.isBlank())continue;
            int eq=line.indexOf('=');if(eq<1)throw new IllegalArgumentException("Usa valor=respuesta completa|otra respuesta, una línea por valor.");
            double value;
            try {value=new BigDecimal(line.substring(0,eq).strip().replace(',','.')).doubleValue();}catch(NumberFormatException ex){throw new IllegalArgumentException("Valor numérico inválido: "+line.substring(0,eq));}
            if(!Double.isFinite(value)||value<0||value>1e9)throw new IllegalArgumentException("Las duraciones deben estar entre 0 y 1000000000.");
            for(String alias:line.substring(eq+1).split("\\|",-1)) {
                String normalized=key(alias);if(normalized.isBlank()||normalized.length()>2000)throw new IllegalArgumentException("Respuesta vacía o demasiado larga.");
                if(normalized.matches("(?s).*[\\p{Cntrl}].*")||normalized.matches("(?s).*\\d+(?:[.,]\\d+)?\\s*(?:a|al|hasta|[-–])\\s*\\d+.*")||normalized.matches("(?s).*(?:aproxim|más de|menos de|o más|desde \\d{4}).*"))throw new IllegalArgumentException("Conserva los rangos y cantidades imprecisas como pendientes: "+alias);
                var explicit=java.util.regex.Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*(años?|anos?|mes(?:es)?)$").matcher(normalized);
                if(explicit.matches()){
                    double source=new BigDecimal(explicit.group(1).replace(',','.')).doubleValue();boolean months=explicit.group(2).startsWith("mes");double converted=months?(unit.equals("años")?source/12:source):(unit.equals("meses")?source*12:source);
                    if(Math.abs(value-converted)>1e-10*Math.max(1,Math.abs(converted)))throw new IllegalArgumentException("La cantidad o unidad no coincide con la respuesta: "+alias);
                }
                Double old=mappings.putIfAbsent(normalized,value);if(old!=null&&Double.compare(old,value)!=0)throw new IllegalArgumentException("Una respuesta tiene dos valores distintos: "+alias);
            }
        }
        if(mappings.size()>2000)throw new IllegalArgumentException("Máximo 2000 respuestas equivalentes.");
        return Map.of("version",1,"enabled",enabled,"unit",unit,"mappings",mappings,"rules",rules.strip());
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> project(Map<String,Object> definition,List<String> texts,String label) {
        if(!Boolean.TRUE.equals(definition.get("enabled")))return Map.of();
        var mappings=(Map<String,Object>)definition.get("mappings");var values=new ArrayList<Double>();
        for(String text:texts){Object v=mappings.get(key(text));if(v instanceof Number n)values.add(n.doubleValue());}
        if(values.isEmpty())return Map.of();
        String unit=(String)definition.get("unit");var stats=StatisticalMath.numeric(values);var advanced=new LinkedHashMap<String,Object>();advanced.put("histogram",ReportAdvancedProjection.histogram(values));
        if(values.size()>1)advanced.put("box",Map.of("values",List.of(stats.get("min"),stats.get("q1"),stats.get("median"),stats.get("q3"),stats.get("max")),"n",values.size(),"method","Cuartiles tipo 7; bigotes mínimo/máximo; sin clasificación de atípicos."));
        return Map.of("label",label,"unit",unit,"numeric",stats,"univariate",UnivariateSummary.calculate(values,unit),"advanced",advanced,"converted",values.size(),"pending",texts.size()-values.size(),"method","Valores derivados de equivalencias completas revisadas; respuestas sin equivalencia excluidas, nunca convertidas a cero.");
    }
    public static String importJson(String json,String unit) {
        if(json.length()>262144)throw new IllegalArgumentException("La propuesta es demasiado larga.");
        var document=CanonicalJson.read(json,Map.class);
        if(document==null||!unit.equals(document.get("unidad_destino"))||!(document.get("propuestas") instanceof List<?> proposals))throw new IllegalArgumentException("La propuesta debe indicar la unidad de destino seleccionada.");
        var lines=new ArrayList<String>();
        for(Object raw:proposals){if(!(raw instanceof Map<?,?> row))throw new IllegalArgumentException("Propuesta inválida.");if(!"CONVERTIBLE".equals(row.get("estado")))continue;
            if(!(row.get("valor") instanceof Number value)||!(row.get("original") instanceof String original)||!unit.equals(row.get("unidad"))||original.matches("(?s).*[\\r\\n|=].*"))throw new IllegalArgumentException("Propuesta convertible inválida.");
            lines.add(value+"="+original);
        }
        String rules=String.join("\n",lines);definition(true,unit,rules);return rules;
    }
}

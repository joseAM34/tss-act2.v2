package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.reporting.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;

/** Explicit, reviewed full-answer mappings. Never applies thematic token normalization. */
public final class NumericNormalization {
    public static final Set<String> UNITS=Set.of("años","meses","días","horas","minutos","segundos");
    private NumericNormalization() {}
    private static String canonicalUnit(String unit){return switch(unit){case "año","años","ano","anos"->"años";case "mes","meses"->"meses";case "día","días","dia","dias"->"días";case "hora","horas"->"horas";case "minuto","minutos"->"minutos";case "segundo","segundos"->"segundos";default->unit;};}
    public static double convert(double value,String source,String destination){
        source=canonicalUnit(source);destination=canonicalUnit(destination);
        if(!UNITS.contains(source)||!UNITS.contains(destination))throw new IllegalArgumentException("Unidad de tiempo no compatible.");
        boolean calendar=Set.of("años","meses").contains(source);if(calendar!=Set.of("años","meses").contains(destination))throw new IllegalArgumentException("La conversión entre días y meses/años queda pendiente; no se supone la duración de un mes o año.");
        var factors=Map.of("años",12d,"meses",1d,"días",86400d,"horas",3600d,"minutos",60d,"segundos",1d);return value*factors.get(source)/factors.get(destination);
    }
    public static Map<String,Object> contextual(boolean enabled,String unit,String rules){
        var definition=new LinkedHashMap<>(definition(enabled,unit,rules));
        var mappings=(Map<?,?>)definition.get("mappings");
        for(Object raw:mappings.keySet()){
            var found=java.util.regex.Pattern.compile("(?U)\\b(años?|anos?|mes(?:es)?|días?|dias?|horas?|minutos?|segundos?)\\b").matcher(raw.toString());
            while(found.find())convert(1,found.group(1),unit);
        }
        for(var entry:mappings.entrySet())if(entry.getKey().toString().matches("[0-9]+(?:[.,][0-9]+)?")){
            double original=new BigDecimal(entry.getKey().toString().replace(',','.')).doubleValue();
            if(Math.abs(original-((Number)entry.getValue()).doubleValue())>1e-10*Math.max(1,original))throw new IllegalArgumentException("Sin unidad escrita, la cantidad debe conservar el valor en "+unit+": "+entry.getKey());
        }
        definition.put("version",2);definition.put("unitless_policy","UNIDAD_PRINCIPAL");return definition;
    }
    public static String key(String text) { return Normalizer.normalize(text,Normalizer.Form.NFC).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+"," "); }
    public static Map<String,Object> generic(boolean enabled,String unit,String rules,String context,String proportions){
        if(context==null||context.isBlank()||context.length()>4000||proportions==null||proportions.length()>8000||context.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t')||proportions.chars().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\r'&&c!='\t'))throw new IllegalArgumentException("Indica un contexto válido (hasta 4000 caracteres) y proporciones de hasta 8000 caracteres.");
        if(unit==null||unit.isBlank()||unit.length()>120||unit.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("La referencia numérica debe tener entre 1 y 120 caracteres.");
        var result=new LinkedHashMap<>(parseDefinition(enabled,unit,rules,false));result.put("version",3);result.put("context",context.strip());result.put("proportions",proportions.strip());result.put("unitless_policy","INTERPRETAR_CONTEXTO_REVISADO");return result;
    }
    public static Map<String,Object> definition(boolean enabled,String unit,String rules) {return parseDefinition(enabled,unit,rules,true);}
    private static Map<String,Object> parseDefinition(boolean enabled,String unit,String rules,boolean temporal) {
        if(temporal&&!UNITS.contains(unit))throw new IllegalArgumentException("Selecciona una unidad de tiempo válida.");
        if(rules.length()>262144)throw new IllegalArgumentException("Demasiadas equivalencias numéricas.");
        var mappings=new LinkedHashMap<String,Double>();
        for(String line:rules.split("\\R")) {
            if(line.isBlank())continue;
            int eq=line.indexOf('=');if(eq<1)throw new IllegalArgumentException("Usa valor=respuesta completa|otra respuesta, una línea por valor.");
            double value;
            try {value=new BigDecimal(line.substring(0,eq).strip().replace(',','.')).doubleValue();}catch(NumberFormatException ex){throw new IllegalArgumentException("Valor numérico inválido: "+line.substring(0,eq));}
            if(!Double.isFinite(value)||(temporal?value<0:Math.abs(value)>1e9)||value>1e9)throw new IllegalArgumentException("La cantidad está fuera de los límites numéricos admitidos.");
            for(String alias:line.substring(eq+1).split("\\|",-1)) {
                String normalized=key(alias);if(normalized.isBlank()||normalized.length()>2000)throw new IllegalArgumentException("Respuesta vacía o demasiado larga.");
                if(normalized.matches("(?s).*[\\p{Cntrl}].*")||normalized.matches("(?s).*\\d+(?:[.,]\\d+)?\\s*(?:a|al|hasta|[-–])\\s*\\d+.*")||normalized.matches("(?s).*(?:aproxim|más de|menos de|o más|desde \\d{4}).*"))throw new IllegalArgumentException("Conserva los rangos y cantidades imprecisas como pendientes: "+alias);
                var explicit=java.util.regex.Pattern.compile("^(\\d+(?:[.,]\\d+)?)\\s*(años?|anos?|mes(?:es)?|días?|dias?|horas?|minutos?|segundos?)$").matcher(normalized);
                if(temporal&&explicit.matches()){
                    double source=new BigDecimal(explicit.group(1).replace(',','.')).doubleValue();double converted=convert(source,explicit.group(2),unit);
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
        var summary=UnivariateSummary.calculate(values,unit);
        if(Objects.equals(definition.get("version"),3)){
            var measures=new ArrayList<Map<String,Object>>();for(var raw:(List<Map<String,Object>>)summary.get("measures")){var measure=new LinkedHashMap<>(raw);if("cv".equals(measure.get("key")))measure.put("value",null);measures.add(measure);}summary.put("measures",measures);summary.put("method",summary.get("method")+" El contexto libre no establece automáticamente una escala de razón; no se declara CV para esta normalización.");
        }
        return Map.of("label",label,"unit",unit,"numeric",stats,"univariate",summary,"advanced",advanced,"converted",values.size(),"pending",texts.size()-values.size(),"method","Valores derivados de equivalencias completas revisadas; respuestas sin equivalencia excluidas, nunca convertidas a cero.");
    }
    public static String importLines(String text,String unit,boolean generic){
        if(text.length()>262144)throw new IllegalArgumentException("La propuesta es demasiado larga.");
        String cleaned=text.strip().replaceFirst("^```[a-zA-Z]*\\s*","").replaceFirst("\\s*```$","");
        cleaned=cleaned.lines().filter(line->!line.strip().startsWith("#")).collect(java.util.stream.Collectors.joining("\n")).strip();
        if(cleaned.isBlank())throw new IllegalArgumentException("No hay equivalencias para cargar. Las respuestas sin cantidad siguen pendientes; el diccionario se conserva.");
        if(generic)parseDefinition(true,unit,cleaned,false);else definition(true,unit,cleaned);return cleaned;
    }
    @SuppressWarnings("unchecked")
    public static String mergeRules(String existing,String incoming,String unit,boolean generic){
        var parsed=parseDefinition(true,unit,existing+"\n"+incoming,!generic);
        var grouped=new TreeMap<Double,List<String>>();for(var entry:((Map<String,Double>)parsed.get("mappings")).entrySet())grouped.computeIfAbsent(entry.getValue(),k->new ArrayList<>()).add(entry.getKey());
        return grouped.entrySet().stream().map(e->BigDecimal.valueOf(e.getKey()).stripTrailingZeros().toPlainString()+"="+String.join("|",e.getValue())).collect(java.util.stream.Collectors.joining("\n"));
    }
    public static String importProposal(String incoming,String existing,String unit,boolean generic){
        String rules=incoming.strip().startsWith("{")?importJson(incoming,unit,generic):importLines(incoming,unit,generic);
        if(rules.isBlank())throw new IllegalArgumentException("La propuesta no contiene cantidades convertibles. El diccionario se conserva.");
        return mergeRules(existing,rules,unit,generic);
    }
    public static String importJson(String json,String unit){return importJson(json,unit,false);}
    public static String importJson(String json,String unit,boolean generic) {
        if(json.length()>262144)throw new IllegalArgumentException("La propuesta es demasiado larga.");
        var document=CanonicalJson.read(json,Map.class);
        if(document==null||!unit.equals(document.get("unidad_destino"))||!(document.get("propuestas") instanceof List<?> proposals))throw new IllegalArgumentException("La propuesta debe indicar la unidad de destino seleccionada.");
        var lines=new ArrayList<String>();
        for(Object raw:proposals){if(!(raw instanceof Map<?,?> row))throw new IllegalArgumentException("Propuesta inválida.");
            if("EJEMPLO".equals(row.get("id")))throw new IllegalArgumentException("La propuesta contiene el ejemplo de formato. Pide el JSON de las respuestas reales, sin añadir el ejemplo.");
            if(!Set.of("CONVERTIBLE","PENDIENTE","SIN_CANTIDAD").contains(Objects.toString(row.get("estado"),"")))throw new IllegalArgumentException("Estado inválido. Usa CONVERTIBLE, PENDIENTE o SIN_CANTIDAD en el JSON.");
            if(!"CONVERTIBLE".equals(row.get("estado")))continue;
            if(!(row.get("valor") instanceof Number value)||!(row.get("original") instanceof String original)||!unit.equals(row.get("unidad"))||original.matches("(?s).*[\\r\\n|=].*"))throw new IllegalArgumentException("Propuesta convertible inválida.");
            lines.add(value+"="+original);
        }
        String rules=String.join("\n",lines);if(generic)parseDefinition(true,unit,rules,false);else definition(true,unit,rules);return rules;
    }
}

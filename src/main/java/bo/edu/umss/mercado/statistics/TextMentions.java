package bo.edu.umss.mercado.statistics;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Conteo descriptivo reproducible de menciones, sin clasificación semántica implícita. */
public final class TextMentions {
    private TextMentions() {}
    public record Theme(String label,List<String> aliases,List<Pattern> patterns) {}
    public static String normalize(String value){return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+#]+"," ").strip().replaceAll("\\s+"," ");}
    public static String canonicalize(String rules){
        if(rules==null||rules.length()>262144)throw new IllegalArgumentException("El diccionario supera 262144 caracteres.");
        var groups=new ArrayList<LinkedHashSet<String>>();var comments=new LinkedHashSet<String>();
        for(String line:rules.split("\\R")){if(line.isBlank())continue;if(line.stripLeading().startsWith("#")){comments.add(line.strip());continue;}String[] parts=line.split("=",2);if(parts.length!=2||parts[0].isBlank())throw new IllegalArgumentException("Usa nombre=variante|sinónimo.");var group=new LinkedHashSet<String>();group.add(normalize(parts[0]));for(String alias:parts[1].split("\\|",-1)){String value=normalize(alias);if(value.isBlank()||value.length()>100)throw new IllegalArgumentException("Variante vacía o demasiado larga.");group.add(value);}if(group.getFirst().length()>100)throw new IllegalArgumentException("Nombre demasiado largo.");
            boolean joined;do{joined=false;for(var existing:new ArrayList<>(groups))if(!Collections.disjoint(group,existing)){group.addAll(existing);groups.remove(existing);joined=true;}}while(joined);groups.add(group);
        }
        var lines=new ArrayList<String>();for(var group:groups){String preferred=group.getFirst();String label=preferred.length()>3&&!preferred.matches("^(actualmente|considero) .*")?preferred:group.stream().max(Comparator.comparingInt((String value)->value.matches("^(actualmente|considero) .*")?-1:0).thenComparingInt(value->value.split(" ").length).thenComparingInt(String::length).thenComparing(Comparator.naturalOrder())).orElseThrow();lines.add(label+"="+String.join("|",group));}lines.sort(String::compareTo);lines.addAll(comments);String result=String.join("\n",lines);parse(result);return result;
    }
    public static List<Theme> parse(String rules){
        if(rules==null||rules.length()>262144)throw new IllegalArgumentException("Usa hasta 262144 caracteres para el diccionario.");
        var themes=new ArrayList<Theme>();var labels=new HashSet<String>();
        for(String line:rules.split("\\R")){if(line.isBlank()||line.stripLeading().startsWith("#"))continue;String[] parts=line.split("=",2);
            if(parts.length!=2||parts[0].isBlank()||parts[0].strip().length()>100)throw new IllegalArgumentException("Escribe cada tema como Nombre=palabra|sinónimo.");
            var aliases=Arrays.stream(parts[1].split("\\|",-1)).map(String::strip).toList();
            if(aliases.isEmpty()||aliases.size()>200||aliases.stream().anyMatch(a->a.length()>100||normalize(a).isEmpty()))throw new IllegalArgumentException("Cada tema admite hasta doscientas variantes no vacías de hasta cien caracteres.");
            if(!labels.add(normalize(parts[0])))throw new IllegalArgumentException("No repitas el nombre de un tema.");
            themes.add(new Theme(parts[0].strip(),aliases,aliases.stream().map(TextMentions::normalize).distinct().map(a->Pattern.compile("(?<![\\p{L}\\p{N}])"+Pattern.quote(a)+"(?![\\p{L}\\p{N}])")).toList()));
            if(themes.size()>1000)throw new IllegalArgumentException("Usa hasta mil temas.");
        }return themes;
    }
    public static Map<String,Object> count(List<String> answers,String rules){
        var themes=parse(rules);int[] counts=new int[themes.size()];int matched=0;
        for(String answer:answers){var found=highlights(answer,themes,rules).stream().map(m->m.get("theme")).collect(java.util.stream.Collectors.toSet());for(int i=0;i<themes.size();i++)if(found.contains(themes.get(i).label()))counts[i]++;if(!found.isEmpty())matched++;}
        var distribution=new ArrayList<Map<String,Object>>();for(int i=0;i<themes.size();i++){var t=themes.get(i);distribution.add(Map.of("label",t.label(),"aliases",t.aliases(),"count",counts[i],"base",answers.size()));}
        return Map.of("rules",rules,"distribution",distribution,"base",answers.size(),"matched",matched,"unmatched",answers.size()-matched,"method","MENCION_POR_RESPUESTA_V2");
    }
    public static List<Map<String,Object>> highlights(String text,String rules){
        return highlights(text,parse(rules),rules);
    }
    private static List<Map<String,Object>> highlights(String text,List<Theme> themes,String rules){
        var matcher=Pattern.compile("[\\p{L}\\p{N}]+(?:\\+\\+|#)?").matcher(text);var words=new ArrayList<String>();var starts=new ArrayList<Integer>();var ends=new ArrayList<Integer>();
        while(matcher.find()){words.add(normalize(matcher.group()));starts.add(matcher.start());ends.add(matcher.end());}
        var result=new ArrayList<Map<String,Object>>();
        for(var theme:themes)for(String alias:theme.aliases()){String normalized=normalize(alias);var tokens=List.of(normalized.split(" "));for(int i=0;i+tokens.size()<=words.size();i++)if(words.subList(i,i+tokens.size()).equals(tokens))result.add(Map.of("start",starts.get(i),"end",ends.get(i+tokens.size()-1),"theme",theme.label(),"alias",normalized));}
        result.sort(Comparator.<Map<String,Object>>comparingInt(r->(Integer)r.get("end")-(Integer)r.get("start")).reversed().thenComparingInt(r->(Integer)r.get("start")));
        var selected=new ArrayList<Map<String,Object>>();for(var candidate:result)if(selected.stream().noneMatch(r->(Integer)candidate.get("start")<(Integer)r.get("end")&&(Integer)candidate.get("end")>(Integer)r.get("start")))selected.add(candidate);
        selected.sort(Comparator.comparingInt(r->(Integer)r.get("start")));return selected;
    }
}

package bo.edu.umss.mercado.importing;

import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.*;

/** Propuestas sin escritura ni aceptación automática; códigos y etiquetas ambiguos quedan pendientes. */
public final class ImportSuggestions {
    private ImportSuggestions() {}
    public record Proposal(CsvImportService.Mapping mapping, int matched, int pending, List<String> notices) {}
    public static String normalize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").strip();
    }
    private static String optionKey(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT).strip().replaceAll("\\s+"," ");
    }
    public static Proposal propose(SurveyDefinition definition,List<List<String>> table) {
        var headers=table.getFirst(); var candidates=new ArrayList<List<Integer>>();
        for(var q:definition.questions()) candidates.add(matches(headers,List.of(q.id(),q.label()),true));
        candidates.add(matches(headers,List.of("consentimiento","consent","consentimiento académico","aceptación de uso académico")));
        candidates.add(matches(headers,List.of("fecha_externa","fecha externa")));
        candidates.add(matches(headers,List.of("id_externo","identificador externo")));
        var uses=new HashMap<Integer,Integer>();
        candidates.stream().filter(c->c.size()==1).forEach(c->uses.merge(c.getFirst(),1,Integer::sum));
        var selected=candidates.stream().map(c->c.size()==1 && uses.getOrDefault(c.getFirst(),0)==1?c.getFirst():-1).toList();
        var columns=new ArrayList<CsvImportService.Column>();var notices=new ArrayList<String>();int matched=0;
        for(int i=0;i<definition.questions().size();i++) {
            var q=definition.questions().get(i);int column=selected.get(i);
            columns.add(new CsvImportService.Column(q.id(),column));
            if(column>=0)matched++;else notices.add("Elige la columna de «"+q.label()+"»: no hay una coincidencia única por código o enunciado.");
        }
        int n=definition.questions().size(), consent=selected.get(n), date=selected.get(n+1), external=selected.get(n+2);
        String consentValue="SI";
        if(consent>=0) {
            int c=consent;
            var positive=table.subList(1,table.size()).stream().map(r->r.get(c).strip()).filter(v->Set.of("si","acepto","true","verdadero","yes").contains(normalize(v))).distinct().toList();
            if(positive.size()==1)consentValue=positive.getFirst();
            else notices.add("Revisa el texto exacto de aceptación: la columna de consentimiento no tiene un único valor afirmativo reconocido.");
        } else notices.add("Selecciona la columna que recoge el consentimiento académico en la fuente.");
        if(date>=0) {
            int d=date;
            boolean valid=table.subList(1,table.size()).stream().allMatch(r->{try {if(!r.get(d).isBlank())OffsetDateTime.parse(r.get(d).strip());return true;}catch(java.time.format.DateTimeParseException ex){return false;}});
            if(!valid){date=-1;notices.add("La fecha externa tiene un formato ambiguo. Se conserva sin interpretar; puedes asignarla si contiene fechas ISO 8601 con zona.");}
        }
        boolean mixedMultiple=false;
        for(int i=0;i<definition.questions().size();i++) {
            var q=definition.questions().get(i); int c=selected.get(i);
            if(c>=0 && q.type().equals("checkbox")) for(var row:table.subList(1,table.size())) {
                String raw=row.get(c);
                if(raw.stripTrailing().endsWith(";") || raw.indexOf('\u00a0')>=0 || raw.indexOf('\u202f')>=0
                        || raw.contains(",") && raw.contains("(")) mixedMultiple=true;
                if(!raw.stripLeading().startsWith("[")) continue;
                try { options(q,raw,"AUTO","JSON"); mixedMultiple=true; } catch(IllegalArgumentException ignored) { }
            }
        }
        if(mixedMultiple) notices.add("Se propone la lectura mixta de selecciones múltiples: listas JSON, terminador de Forms y separadores inequívocos. Las comas dentro de paréntesis se conservan. Revisa la vista previa.");
        return new Proposal(new CsvImportService.Mapping(columns,consent,consentValue,date,external,"AUTO",mixedMultiple?"MIXTO_V1":"AUTO",false,"",""),matched,n-matched,List.copyOf(notices));
    }
    private static List<Integer> matches(List<String> headers,List<String> aliases) {
        return matches(headers,aliases,false);
    }
    private static List<Integer> matches(List<String> headers,List<String> aliases,boolean allowBracketWrapper) {
        var normalized=aliases.stream().map(v->allowBracketWrapper?questionHeader(v):normalize(v)).filter(s->!s.isEmpty()).toList();
        var result=new ArrayList<Integer>();
        for(int i=0;i<headers.size();i++) {
            String header=allowBracketWrapper?questionHeader(headers.get(i)):normalize(headers.get(i));
            if(normalized.contains(header))result.add(i);
        }
        return result;
    }
    private static String questionHeader(String header) {
        header=sourceSpaces(header).strip().replaceFirst("^\\d+[).]\\s+","");
        if(header.matches("\\[[^\\[\\]]+\\]")) header=header.substring(1,header.length()-1);
        return normalize(header);
    }

    public static List<String> options(Question question,String raw,String mode,String separator) {
        if(separator.equals("MIXTO_V1")) return mixedOptions(question,raw,mode);
        if(question.type().equals("checkbox") && separator.equals("JSON")) {
            return jsonValues(raw).stream().map(v->option(question,v,mode)).toList();
        }
        if(!question.type().equals("checkbox") || !separator.equals("AUTO")) {
            var values=question.type().equals("checkbox") && !separator.isEmpty()?Arrays.asList(raw.split(java.util.regex.Pattern.quote(separator),-1)):List.of(raw);
            return values.stream().map(v->option(question,v,mode)).toList();
        }
        // Una etiqueta completa que contiene coma/punto y coma no se divide.
        try {return List.of(option(question,raw,mode));}catch(IllegalArgumentException ignored) {}
        var interpretations=new LinkedHashSet<List<String>>();
        for(String candidate:List.of("|",";",",")) {
            if(!raw.contains(candidate))continue;
            try {
                var values=Arrays.stream(raw.split(java.util.regex.Pattern.quote(candidate),-1)).map(v->option(question,v,mode)).toList();
                if(new HashSet<>(values).size()==values.size())interpretations.add(values);
            }catch(IllegalArgumentException ignored) {}
        }
        if(interpretations.size()!=1)throw new IllegalArgumentException("No se pudo interpretar una selección múltiple de forma única. Elige su separador o corrige el valor en la fuente.");
        return interpretations.getFirst();
    }
    private static List<String> jsonValues(String raw) {
        List<?> values;
        try { values=bo.edu.umss.mercado.data.CanonicalJson.read(raw,List.class); }
        catch(RuntimeException ex) { throw new IllegalArgumentException("La selección múltiple requiere una lista JSON de textos; revisa la celda original."); }
        if(values==null || values.stream().anyMatch(v->!(v instanceof String))) throw new IllegalArgumentException("La selección múltiple JSON debe contener textos de opciones, sin números ni valores nulos.");
        return values.stream().map(String.class::cast).toList();
    }
    private static String sourceSpaces(String raw) {return raw.replace('\u00a0',' ').replace('\u202f',' ');}
    private static List<String> mixedOptions(Question question,String raw,String mode) {
        if(!question.type().equals("checkbox"))return List.of(option(question,raw,mode,true));
        raw=sourceSpaces(raw).strip();
        if(raw.startsWith("["))return jsonValues(raw).stream().map(v->option(question,v,mode,true)).toList();
        // Se respeta primero una etiqueta completa, incluso si contiene un punto y coma literal.
        try { return List.of(option(question,raw,mode,true)); } catch(IllegalArgumentException ignored) { }
        // Forms añade un terminador; no se eliminan vacíos interiores ni varios terminadores.
        if(raw.endsWith(";"))raw=raw.substring(0,raw.length()-1).stripTrailing();
        try { return List.of(option(question,raw,mode,true)); } catch(IllegalArgumentException ignored) { }
        var interpretations=new LinkedHashSet<List<String>>();
        for(String separator:List.of("|",";",",")) {
            if(!raw.contains(separator))continue;
            try {
                var pieces=separator.equals(",")?topLevelComma(raw):Arrays.asList(raw.split(java.util.regex.Pattern.quote(separator),-1));
                var values=pieces.stream().map(v->option(question,v,mode,true)).toList();
                if(new HashSet<>(values).size()==values.size())interpretations.add(values);
            } catch(IllegalArgumentException ignored) { }
        }
        if(interpretations.isEmpty()) {
            var unknown=unrecognizedOptions(question,raw,mode,"MIXTO_V1");
            if(!unknown.isEmpty())throw new IllegalArgumentException("Opciones no incluidas en esta versión: «"+String.join("», «",unknown)+"». Revisa el catálogo; el archivo original se conserva.");
        }
        if(interpretations.size()!=1)throw new IllegalArgumentException("No se pudo interpretar una selección múltiple de forma única. Revisa la lista JSON, su terminador o los separadores de la fuente.");
        return interpretations.getFirst();
    }
    /** Unknown labels from an unambiguous list with at least one recognized option; no recoding. */
    public static List<String> unrecognizedOptions(Question question,String raw,String mode,String separator) {
        if(!question.type().equals("checkbox") || !separator.equals("MIXTO_V1") || mode.equals("CODIGOS") || raw==null || raw.isBlank())return List.of();
        raw=sourceSpaces(raw).strip();
        try {option(question,raw,mode,true);return List.of();}catch(IllegalArgumentException ignored){}
        if(!optionMatches(question,raw,mode,true).isEmpty())return List.of();
        var lists=new ArrayList<List<String>>();
        if(raw.startsWith("[")) {try {lists.add(jsonValues(raw));}catch(IllegalArgumentException ex){return List.of();}}
        else {
            if(raw.endsWith(";"))raw=raw.substring(0,raw.length()-1).stripTrailing();
            for(String delimiter:List.of("|",";",","))if(raw.contains(delimiter))try {
                lists.add(delimiter.equals(",")?topLevelComma(raw):Arrays.asList(raw.split(java.util.regex.Pattern.quote(delimiter),-1)));
            }catch(IllegalArgumentException ignored){}
        }
        int best=0;var choices=new LinkedHashSet<List<String>>();
        for(var pieces:lists) {
            if(pieces.stream().anyMatch(v->v==null || v.isBlank() || v.length()>1000))continue;
            int known=0;boolean ambiguous=false;var unknown=new LinkedHashSet<String>();
            for(String piece:pieces)try {option(question,piece,mode,true);known++;}catch(IllegalArgumentException ex){
                if(!optionMatches(question,sourceSpaces(piece).strip(),mode,true).isEmpty()){ambiguous=true;break;}
                unknown.add(sourceSpaces(piece).strip());
            }
            if(!ambiguous && unknown.isEmpty())return List.of();
            if(!ambiguous && known>0 && !unknown.isEmpty()) {
                if(known>best){best=known;choices.clear();}
                if(known==best)choices.add(List.copyOf(unknown));
            }
        }
        return choices.size()==1?choices.iterator().next():List.of();
    }
    private static List<String> topLevelComma(String raw) {
        var result=new ArrayList<String>(); int depth=0,start=0;
        for(int i=0;i<raw.length();i++) {
            char c=raw.charAt(i);
            if(c=='(')depth++;
            else if(c==')') {if(--depth<0)throw new IllegalArgumentException("Paréntesis de opción incompletos.");}
            else if(c==',' && depth==0) {result.add(raw.substring(start,i));start=i+1;}
        }
        if(depth!=0)throw new IllegalArgumentException("Paréntesis de opción incompletos.");
        result.add(raw.substring(start));return result;
    }
    private static String option(Question q,String raw,String mode) {
        return option(q,raw,mode,false);
    }
    private static String option(Question q,String raw,String mode,boolean sourceFormat) {
        String value=(sourceFormat?sourceSpaces(raw):raw).strip();
        if(mode.equals("CODIGOS")) {
            if(q.options().stream().noneMatch(o->o.value().equals(value)))throw new IllegalArgumentException("Código inexistente; revisa la representación de opciones o corrige el valor en la fuente.");
            return value;
        }
        if(mode.equals("AUTO") && optionKey(value).isEmpty())throw new IllegalArgumentException("Opción vacía; revisa la fuente.");
        var matches=optionMatches(q,value,mode,sourceFormat);
        if(matches.size()!=1)throw new IllegalArgumentException("Opción inexistente o ambigua; elige códigos/etiquetas exactos o corrige la fuente.");
        return matches.getFirst();
    }
    private static List<String> optionMatches(Question q,String value,String mode,boolean sourceFormat) {
        return optionMatches(q.options(),value,mode,sourceFormat);
    }
    static List<String> matchingSourceOptions(List<Question.Option> options,String raw,String mode) {
        return optionMatches(options,sourceSpaces(raw).strip(),mode,true);
    }
    private static List<String> optionMatches(List<Question.Option> options,String value,String mode,boolean sourceFormat) {
        return options.stream().filter(o->mode.equals("AUTO")
                ? optionKey(sourceFormat?sourceSpaces(o.value()):o.value()).equals(optionKey(value)) || optionKey(sourceFormat?sourceSpaces(o.label()):o.label()).equals(optionKey(value))
                : (sourceFormat?sourceSpaces(o.label()):o.label()).equals(value)).map(Question.Option::value).distinct().toList();
    }
}

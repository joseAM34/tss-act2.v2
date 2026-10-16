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
        for(var q:definition.questions()) candidates.add(matches(headers,List.of(q.id(),q.label())));
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
        return new Proposal(new CsvImportService.Mapping(columns,consent,consentValue,date,external,"AUTO","AUTO",false,"",""),matched,n-matched,List.copyOf(notices));
    }
    private static List<Integer> matches(List<String> headers,List<String> aliases) {
        var normalized=aliases.stream().map(ImportSuggestions::normalize).filter(s->!s.isEmpty()).toList();
        var result=new ArrayList<Integer>();
        for(int i=0;i<headers.size();i++)if(normalized.contains(normalize(headers.get(i))))result.add(i);
        return result;
    }

    public static List<String> options(Question question,String raw,String mode,String separator) {
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
    private static String option(Question q,String raw,String mode) {
        String value=raw.strip();
        if(mode.equals("CODIGOS")) {
            if(q.options().stream().noneMatch(o->o.value().equals(value)))throw new IllegalArgumentException("Código inexistente; revisa la representación de opciones o corrige el valor en la fuente.");
            return value;
        }
        if(mode.equals("AUTO") && optionKey(value).isEmpty())throw new IllegalArgumentException("Opción vacía; revisa la fuente.");
        var matches=q.options().stream().filter(o->mode.equals("AUTO")
                ? optionKey(o.value()).equals(optionKey(value)) || optionKey(o.label()).equals(optionKey(value))
                : o.label().equals(value)).map(Question.Option::value).distinct().toList();
        if(matches.size()!=1)throw new IllegalArgumentException("Opción inexistente o ambigua; elige códigos/etiquetas exactos o corrige la fuente.");
        return matches.getFirst();
    }
}

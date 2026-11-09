package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.statistics.TextMentions;
import java.util.*;
import org.springframework.stereotype.Service;

/** Derived counts; original answers are included only when explicitly selected. */
@Service
public class ReportTextProjection {
    private final CaptureStore captures;
    public ReportTextProjection(CaptureStore captures){this.captures=captures;}
    /** Temporary numeric view; frozen source and selections are never modified. */
    @SuppressWarnings("unchecked")
    public Map<String,Object> numericLogical(Map<String,Object> logical,Map<String,Map<String,Object>> selections){
        var output=new LinkedHashMap<>(logical);
        var fields=new ArrayList<Map<String,Object>>();
        var definitions=new HashMap<String,Map<String,Object>>();
        for(var original:(List<Map<String,Object>>)logical.get("dictionary")){
            var q=new LinkedHashMap<>(original);fields.add(q);var selection=selections.get(q.get("question_id"));
            if(selection==null||!(selection.get("numeric") instanceof Map<?,?> numeric))continue;
            var definition=(Map<String,Object>)numeric.get("definition");
            if(!bo.edu.umss.mercado.data.CanonicalJson.hash(bo.edu.umss.mercado.data.CanonicalJson.json(definition)).equals(numeric.get("hash")))throw new IllegalStateException("Equivalencias congeladas alteradas.");
            if(!Boolean.TRUE.equals(definition.get("enabled"))||sensitive(q,false))continue;
            definitions.put((String)q.get("question_id"),definition);q.put("treatment","MAGNITUD_ENTERA");q.put("unit",definition.get("unit"));
        }
        if(definitions.isEmpty())return logical;
        var matrix=(List<Map<String,Object>>)logical.get("matrix");
        var inputs=new HashMap<String,CaptureStore.Input>();for(var input:captures.inputs(matrix.stream().map(r->(String)r.get("response_id")).distinct().toList()))inputs.put(input.responseId(),input);
        var members=new HashMap<String,Map<String,Object>>();for(var member:(List<Map<String,Object>>)logical.get("members"))members.put((String)member.get("response_id"),member);
        var converted=new ArrayList<Map<String,Object>>();
        for(var row:matrix){var copy=new LinkedHashMap<>(row);var values=new LinkedHashMap<>((Map<String,Object>)row.get("values"));copy.put("values",values);converted.add(copy);
            for(var q:fields){String id=(String)q.get("question_id");var definition=definitions.get(id);if(definition==null)continue;values.remove(id);
                if(!"CON_VALOR".equals(((Map<?,?>)row.get("states")).get(id)))continue;
                String response=(String)row.get("response_id");var input=Objects.requireNonNull(inputs.get(response),"Captura no encontrada.");
                if(!Objects.equals(members.get(response).get("capture_hash"),input.captureHash()))throw new IllegalStateException("Captura congelada alterada.");
                String text=String.join(" · ",input.answers().getOrDefault((String)q.get("original_id"),List.of()));
                Object number=((Map<?,?>)definition.get("mappings")).get(bo.edu.umss.mercado.statistics.NumericNormalization.key(text));
                if(number instanceof Number)values.put(id,number);
            }
        }
        output.put("dictionary",fields);output.put("matrix",converted);return output;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> enrich(Map<String,Object> result,Map<String,Object> logical,String rules,int revision){
        return enrich(result,logical,rules,revision,Set.of());
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> enrich(Map<String,Object> result,Map<String,Object> logical,String rules,int revision,Set<String> originalQuestions){
        return enrich(result,logical,rules,revision,originalQuestions,Set.of());
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> enrich(Map<String,Object> result,Map<String,Object> logical,String rules,int revision,Set<String> originalQuestions,Set<String> sensitiveQuestions){
        return enrich(result,logical,rules,revision,originalQuestions,sensitiveQuestions,Map.of(),false);
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> enrich(Map<String,Object> result,Map<String,Object> logical,String rules,int revision,Set<String> originalQuestions,Set<String> sensitiveQuestions,Map<String,Map<String,Object>> selections,boolean legacyPolicy){
        var output=new LinkedHashMap<>(result);
        var dictionary=(List<Map<String,Object>>)logical.get("dictionary");
        var members=(List<Map<String,Object>>)logical.get("members");
        var matrix=(List<Map<String,Object>>)logical.get("matrix");
        Map<String,CaptureStore.Input> inputs=null;
        var membersById=new HashMap<String,Map<String,Object>>();members.forEach(m->membersById.put((String)m.get("response_id"),m));
        var textKeys=new HashSet<String>();
        for(var q:dictionary)if(Set.of("text","textarea","email","tel").contains(q.getOrDefault("original_type",""))&&(!sensitive(q,legacyPolicy)||sensitiveQuestions.contains(q.get("question_id"))))textKeys.add((String)q.get("question_id"));
        var variables=new ArrayList<Map<String,Object>>();
        for(var original:(List<Map<String,Object>>)result.get("variables")){
            var variable=new LinkedHashMap<>(original);variables.add(variable);
            var q=dictionary.stream().filter(d->Objects.equals(d.get("question_id"),original.get("question_id"))).findFirst().orElseThrow();
            if(!Set.of("text","textarea","email","tel").contains(q.getOrDefault("original_type","")))continue;
            String questionId=(String)q.get("question_id");
            boolean sensitive=sensitive(q,legacyPolicy);
            variable.put("text_available",true);variable.put("sensitive_text",sensitive);
            if(sensitive&&!sensitiveQuestions.contains(questionId))continue;
            if(inputs==null){
                var ids=matrix.stream().filter(r->{var states=(Map<?,?>)r.get("states");return textKeys.stream().anyMatch(k->"CON_VALOR".equals(states.get(k)));}).map(r->(String)r.get("response_id")).distinct().toList();
                inputs=new HashMap<>();for(var input:captures.inputs(ids))inputs.put(input.responseId(),input);
            }
            var texts=new ArrayList<String>();String key=(String)q.get("question_id"),source=(String)q.get("original_id");
            for(var row:matrix){if(!"CON_VALOR".equals(((Map<?,?>)row.get("states")).get(key)))continue;
                String id=(String)row.get("response_id");
                var input=Objects.requireNonNull(inputs.get(id),"Captura no encontrada.");
                var member=Objects.requireNonNull(membersById.get(id),"Miembro no encontrado.");
                if(!Objects.equals(member.get("capture_hash"),input.captureHash()))throw new IllegalStateException("La fuente de texto no coincide con su captura.");
                texts.add(String.join(" · ",input.answers().getOrDefault(source,List.of())));
            }
            var selection=selections.get(questionId);String applied=selection==null?rules:(String)selection.get("rules");
            if(selection!=null&&!bo.edu.umss.mercado.data.CanonicalJson.hash(applied).equals(selection.get("hash")))throw new IllegalStateException("El diccionario del reporte no coincide con su huella.");
            var counts=new LinkedHashMap<>(TextMentions.count(texts,applied));counts.put("dictionary_revision",selection==null?revision:selection.get("revision"));if(selection!=null)counts.put("dictionary_scope",selection.get("scope"));
            variable.put("text_counts",counts);
            if(selection!=null&&selection.get("numeric") instanceof Map<?,?> numericSelection){
                var definition=(Map<String,Object>)numericSelection.get("definition");
                if(!bo.edu.umss.mercado.data.CanonicalJson.hash(bo.edu.umss.mercado.data.CanonicalJson.json(definition)).equals(numericSelection.get("hash")))throw new IllegalStateException("Las equivalencias numéricas del reporte no coinciden con su huella.");
                var derived=bo.edu.umss.mercado.statistics.NumericNormalization.project(definition,texts,q.get("label").toString());
                if(!derived.isEmpty()){var advanced=new LinkedHashMap<>((Map<String,Object>)variable.getOrDefault("advanced",Map.of()));advanced.putAll((Map<String,Object>)derived.get("advanced"));variable.putAll(derived);variable.put("advanced",advanced);variable.put("numeric_normalization",numericSelection);}
            }
            if(originalQuestions.contains(key))variable.put("text_content",List.copyOf(texts));
        }
        output.put("variables",variables);return output;
    }
    private static boolean sensitive(Map<String,Object> q,boolean legacyPolicy){return legacyPolicy?Set.of("email","tel").contains(q.get("original_type"))||q.get("label").toString().toLowerCase(Locale.ROOT).matches(".*(nombre|correo|email|tel[eé]fono|contacto|direcci[oó]n).*"):bo.edu.umss.mercado.statistics.TextFieldPolicy.sensitive(q.get("original_type"),q.get("label"));}
}

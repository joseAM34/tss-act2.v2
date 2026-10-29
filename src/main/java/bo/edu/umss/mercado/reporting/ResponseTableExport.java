package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.data.CaptureStore;
import bo.edu.umss.mercado.statistics.WorkspaceResultsService;
import bo.edu.umss.mercado.survey.Question;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Administrative originals export, separate from the ordinary analytical matrices. */
@Service
public class ResponseTableExport {
    private final CaptureStore captures;
    public ResponseTableExport(CaptureStore captures){this.captures=captures;}

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    public List<List<Object>> rows(WorkspaceResultsService.Snapshot snapshot,boolean sensitiveAcknowledged){
        if(!sensitiveAcknowledged)throw new IllegalArgumentException("Confirma que la descarga incluye respuestas originales y puede contener datos personales.");
        var dictionary=(List<Map<String,Object>>)snapshot.logical().get("dictionary");
        var matrix=(List<Map<String,Object>>)snapshot.logical().get("matrix");
        var members=(List<Map<String,Object>>)snapshot.logical().get("members");
        var byId=new LinkedHashMap<String,Map<String,Object>>();
        members.forEach(m->{if(byId.put((String)m.get("response_id"),m)!=null)throw corrupt();});
        var inputs=new HashMap<String,CaptureStore.Input>();
        captures.inputs(new ArrayList<>(byId.keySet())).forEach(input->inputs.put(input.responseId(),input));
        var headers=new ArrayList<Object>();headers.add("Recepción (America/La_Paz)");
        var labels=dictionary.stream().map(q->q.get("label").toString()+(dictionary.stream().filter(other->other.get("label").equals(q.get("label"))).count()>1?" ["+q.get("question_id")+"; "+String.join(", ",(List<String>)q.get("source_versions"))+"]":"")).toList();
        headers.addAll(labels);labels.forEach(label->headers.add("Estado: "+label));
        headers.addAll(List.of("Referencia del envío","Versión del instrumento","Origen revisado del corte","Huella de captura","Referencia del corte","Corte UTC"));
        var usedHeaders=new HashSet<String>();
        for(int i=0;i<headers.size();i++){String base=headers.get(i).toString(),name=base;int suffix=2;while(!usedHeaders.add(name))name=base+" ["+(suffix++)+"]";headers.set(i,name);}
        var output=new ArrayList<List<Object>>();output.add(headers);
        var seen=new HashSet<String>();
        for(var row:matrix){
            String id=(String)row.get("response_id");var input=inputs.get(id);var member=byId.get(id);
            if(!seen.add(id)||input==null||member==null||!input.hasSnapshot()||!snapshot.slug().equals(input.slug())||!Objects.equals(input.captureHash(),member.get("capture_hash"))||!Objects.equals(input.version(),row.get("instrument_version"))||!Objects.equals(input.submittedAt(),member.get("submitted_at")))throw corrupt();
            var states=(Map<String,String>)row.get("states");
            var line=new ArrayList<Object>();line.add(Instant.parse(input.submittedAt()).atZone(ZoneId.of("America/La_Paz")).toOffsetDateTime().toString());
            for(var q:dictionary){
                String key=(String)q.get("question_id"),original=(String)q.get("original_id");
                if(!states.containsKey(key)){line.add("");continue;}
                String state=states.get(key);
                if(!Objects.equals(state,input.states().get(original)))throw corrupt();
                var definition=input.definition().questions().stream().filter(question->question.id().equals(original)).findFirst().orElseThrow(ResponseTableExport::corrupt);
                var values=input.answers().getOrDefault(original,List.of());
                if(!Set.of("CON_VALOR","SIN_RESPUESTA","NO_APLICA").contains(state)||state.equals("CON_VALOR")==values.isEmpty())throw corrupt();
                line.add(String.join(" | ",values.stream().map(value->definition.options().stream().filter(option->option.value().equals(value)).map(Question.Option::label).findFirst().orElse(value)).toList()));
            }
            dictionary.forEach(q->line.add(states.getOrDefault(q.get("question_id"),"NO_PERTENECE_VERSION")));
            line.addAll(List.of(id,input.version(),row.get("origin"),input.captureHash(),snapshot.id(),snapshot.cutoff()));output.add(line);
        }
        if(seen.size()!=members.size())throw corrupt();
        return output;
    }
    private static IllegalStateException corrupt(){return new IllegalStateException("La exportación no coincide con las capturas verificadas del corte.");}
}

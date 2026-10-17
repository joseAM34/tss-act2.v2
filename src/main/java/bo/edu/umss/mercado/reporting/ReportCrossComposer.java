package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Builds exploratory comparisons from an analysis' exact frozen preparation. */
@Service
public class ReportCrossComposer {
    public record Pair(String analysisId,String row,String column,List<String> representations) {
        public Pair(String analysisId,String row,String column){this(analysisId,row,column,null);}
        public Pair { representations=representations==null?null:List.copyOf(representations); }
    }
    public record Save(Pair pair,String expectedHash) {}
    private final GenericAnalysisService analyses;
    private final GenericCrossService crosses;
    private final JdbcTemplate jdbc;
    public ReportCrossComposer(GenericAnalysisService analyses,GenericCrossService crosses,JdbcTemplate jdbc){this.analyses=analyses;this.crosses=crosses;this.jdbc=jdbc;}
    @SuppressWarnings("unchecked")
    public List<Map<String,Object>> choices(String analysisId){
        var logical=crosses.source((String)analyses.get(analysisId).get("preparation_id"));
        var definition=CanonicalJson.read(CanonicalJson.json(logical.get("definition")),Map.class);
        var groups=new HashMap<Object,Object>();
        for(var question:(List<Map<String,Object>>)definition.get("questions"))groups.put(question.get("id"),question.get("section"));
        return ((List<Map<String,Object>>)logical.get("dictionary")).stream().map(q->{
            boolean eligible="NOMINAL".equals(q.get("treatment"))&&Set.of("radio","select").contains(q.get("type"));
            return Map.<String,Object>of("id",q.get("question_id"),"label",q.get("label"),"eligible",eligible,
                "group",Objects.toString(groups.get(q.get("question_id")),"Preguntas del conjunto"), "options",((List<?>)q.get("options")).size(),
                "reason",eligible?"Compatible con otra pregunta de selección única, hasta 2500 celdas.":"Este cruce requiere categorías nominales de selección única.");
        }).toList();
    }
    @Transactional(readOnly=true)
    public Map<String,Object> preview(Pair pair){
        if(pair==null)throw new IllegalArgumentException("Selecciona dos preguntas.");
        var analysis=analyses.get(pair.analysisId());var logical=crosses.source((String)analysis.get("preparation_id"));
        var spec=new GenericCrossService.Spec(pair.row(),pair.column(),.05,false,"","",false);
        var result=crosses.calculate(logical,spec);var snapshot=new LinkedHashMap<String,Object>();
        snapshot.put("id","preview");snapshot.put("preparation_id",analysis.get("preparation_id"));snapshot.put("source_hash",analysis.get("source_hash"));
        snapshot.put("spec",spec);snapshot.put("spec_hash",CanonicalJson.hash(spec));snapshot.put("result",result);snapshot.put("result_hash",CanonicalJson.hash(result));
        if(pair.representations()!=null){
            var styles=pair.representations();
            if(styles.isEmpty()||styles.size()>4||new HashSet<>(styles).size()!=styles.size()||!Set.of("table","heat","grouped","stacked").containsAll(styles))throw new IllegalArgumentException("Selecciona representaciones compatibles para el cruce.");
            snapshot.put("report_tables",styles.contains("table"));snapshot.put("report_graphs",styles.stream().filter(s->!s.equals("table")).toList());
        }
        return snapshot;
    }
    @Transactional
    public Map<String,Object> save(Save request,String actor){
        if(request==null||request.pair()==null)throw new IllegalArgumentException("Selecciona dos preguntas.");
        if(actor==null||actor.isBlank()||actor.length()>100)throw new IllegalArgumentException("Administrador no válido.");
        var analysis=analyses.get(request.pair().analysisId());String prepared=(String)analysis.get("preparation_id");
        jdbc.queryForList("SELECT id FROM survey_preparation_run WHERE id=? FOR UPDATE",prepared);
        var snapshot=preview(request.pair());
        if(!CanonicalJson.hash(snapshot).equals(request.expectedHash()))throw new ResponseStatusException(HttpStatus.CONFLICT,"El cruce cambió. Revisa su vista previa antes de añadirlo.");
        var existing=jdbc.queryForList("SELECT id FROM survey_general_cross WHERE preparation_id=? AND spec_hash=? AND result_hash=? ORDER BY created_at,id LIMIT 1",String.class,prepared,snapshot.get("spec_hash"),snapshot.get("result_hash"));
        String id=existing.isEmpty()?crosses.create(prepared,(GenericCrossService.Spec)snapshot.get("spec"),actor):existing.getFirst();
        return crosses.get(id);
    }
}

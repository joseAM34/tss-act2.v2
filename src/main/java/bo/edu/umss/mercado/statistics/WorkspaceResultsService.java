package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.*;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import bo.edu.umss.mercado.reporting.ReportAdvancedProjection;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Totales de una captura consistente; ningún gráfico consulta ni modifica sus cifras. */
@Service
public class WorkspaceResultsService {
    public record Snapshot(String id,String surveyId,String slug,String cutoff,List<String> versionIds,String from,String until,boolean allVersions,Map<String,Object> logical,Map<String,Object> result) {}
    public record Cut(String id,String name,String createdAt,Snapshot snapshot) {}
    public record CutState(int analyses,int crosses,int reports,String hash) {}
    private final JdbcTemplate jdbc;
    private final SurveyPublicationService publications;
    private final GenericPreparationService preparation;
    private final GenericAnalysisService analyses;
    private final CaptureStore captures;
    private final TextDictionaryService dictionaries;
    public WorkspaceResultsService(JdbcTemplate jdbc,SurveyPublicationService publications,GenericPreparationService preparation,GenericAnalysisService analyses,CaptureStore captures,TextDictionaryService dictionaries){this.jdbc=jdbc;this.publications=publications;this.preparation=preparation;this.analyses=analyses;this.captures=captures;this.dictionaries=dictionaries;}
    private static Map<String,Object> map(Object... pairs){var m=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return m;}
    private static final List<Question.Option> COVERAGE=List.of(new Question.Option("CON_VALOR","Con respuesta"),new Question.Option("SIN_RESPUESTA","Sin respuesta"),new Question.Option("NO_APLICA","No aplica"));
    private static final ZoneId ZONE=ZoneId.of("America/La_Paz");

    /** Consulta individual administrativa, separada de las matrices y exportaciones ordinarias. */
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    public Map<String,Object> questionAnswers(Snapshot snapshot,String questionId,String actor){
        return questionAnswers(snapshot,questionId,actor,dictionaries.get(snapshot,questionId).rules());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    public Map<String,Object> questionAnswers(Snapshot snapshot,String questionId,String actor,String keywordRules){
        return questionAnswers(snapshot,questionId,actor,keywordRules,false);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Map<String,Object> questionAnswers(Snapshot snapshot,String questionId,String actor,boolean sensitiveAcknowledged){
        return questionAnswers(snapshot,questionId,actor,dictionaries.get(snapshot,questionId).rules(),sensitiveAcknowledged);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    public Map<String,Object> questionAnswers(Snapshot snapshot,String questionId,String actor,String keywordRules,boolean sensitiveAcknowledged){
        actor(actor);
        var dictionary=(List<Map<String,Object>>)snapshot.logical().get("dictionary");
        var question=dictionary.stream().filter(q->questionId.equals(q.get("question_id"))).findFirst().orElseThrow(()->new IllegalArgumentException("Pregunta no incluida en esta vista."));
        var matrix=(List<Map<String,Object>>)snapshot.logical().get("matrix");
        var members=(List<Map<String,Object>>)snapshot.logical().get("members");var hashes=new HashMap<String,String>();members.forEach(m->hashes.put((String)m.get("response_id"),(String)m.get("capture_hash")));
        var rows=new ArrayList<Map<String,Object>>();var frequencies=new LinkedHashMap<String,Integer>();
        String original=(String)question.get("original_id");
        String label=question.get("label").toString().toLowerCase(Locale.ROOT);
        boolean sensitive=TextFieldPolicy.sensitive(question.get("original_type"),label),locked=sensitive&&!sensitiveAcknowledged;
        boolean freeText=Set.of("text","textarea","email","tel").contains(question.get("original_type")),groupable=freeText&&!locked;
        for(var row:matrix){if(!((Map<?,?>)row.get("states")).containsKey(questionId))continue;
            String id=(String)row.get("response_id");var input=captures.input(id);if(!Objects.equals(hashes.get(id),input.captureHash()))throw corrupt();
            var values=input.answers().getOrDefault(original,List.of());String state=input.states().get(original);
            var definition=input.definition().questions().stream().filter(q->q.id().equals(original)).findFirst().orElseThrow(WorkspaceResultsService::corrupt);
            String value=String.join(" · ",values.stream().map(v->definition.options().stream().filter(o->o.value().equals(v)).map(Question.Option::label).findFirst().orElse(v)).toList());
            rows.add(map("id",id,"submittedAt",input.submittedAt(),"version",input.version(),"state",state,"value",value));
            if(groupable&&"CON_VALOR".equals(state))frequencies.merge(value,1,Integer::sum);
        }
        var distribution=frequencies.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed()).map(e->map("label",e.getKey(),"count",e.getValue())).toList();
        var texts=rows.stream().filter(r->"CON_VALOR".equals(r.get("state"))).map(r->r.get("value").toString()).toList();
        return map("label",question.get("label"),"rows",locked?List.of():rows,"sensitive",sensitive,"sensitiveAcknowledged",sensitiveAcknowledged,"dictionaryRevision",dictionaries.get(snapshot,questionId).revision(),"dictionaryScope",dictionaries.scope(snapshot.logical(),questionId),"freeText",freeText,"groupable",groupable,"distribution",distribution,"base",texts.size(),"keywords",groupable?TextMentions.count(texts,keywordRules):Map.of());
    }

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot load(String slug,List<String> ids,String from,String until,String actor){return load(slug,ids,from,until,actor,false);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @SuppressWarnings("unchecked")
    public Snapshot load(String slug,List<String> ids,String from,String until,String actor,boolean allVersions){
        actor(actor);LocalDate start=from.isBlank()?null:LocalDate.parse(from),end=until.isBlank()?null:LocalDate.parse(until);
        if(start!=null&&end!=null&&start.isAfter(end))throw new IllegalArgumentException("La fecha inicial no puede superar la final.");
        if(ids==null||ids.isEmpty()||ids.size()>100||new HashSet<>(ids).size()!=ids.size())throw new IllegalArgumentException("Selecciona versiones publicadas de esta encuesta.");
        var versions=ids.stream().map(publications::get).toList();var anchor=versions.getFirst();
        if(versions.stream().anyMatch(v->!slug.equals(v.slug())||!anchor.surveyId().equals(v.surveyId())))throw new IllegalArgumentException("Todas las versiones deben pertenecer a la misma encuesta.");
        OffsetDateTime cutoff=OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        var groups=new LinkedHashMap<String,Map<String,Object>>();var sourceKeys=new LinkedHashMap<String,Map<String,String>>();
        for(var version:versions){
            var keys=new LinkedHashMap<String,String>();var config=preparation.configuration(version.id());
            for(var q:version.content().definition().questions()){
                String unit=unit(q,config),treatment=!q.options().isEmpty()?"NOMINAL":q.type().equals("number")&&!unit.isBlank()?"MAGNITUD_ENTERA":"COBERTURA";
                String signature=CanonicalJson.hash(map("question",q,"unit",unit,"treatment",treatment,"conditions",dependencies(q,version.content().definition())));
                var group=groups.computeIfAbsent(signature,key->map("original_id",q.id(),"question_id",q.id(),"label",q.label(),"type",treatment.equals("COBERTURA")?"coverage":q.type(),"original_type",q.type(),"treatment",treatment,"unit",unit,
                        "options",treatment.equals("COBERTURA")?COVERAGE:q.options(),"source_versions",new ArrayList<String>(),"required",q.required(),"depends_on",q.dependsOn(),"depends_value",q.dependsValue(),"min",q.min(),"max",q.max(),"privacy","SIN_TEXTOS_ORIGINALES"));
                ((List<String>)group.get("source_versions")).add(version.code());keys.put(q.id(),signature);
            }sourceKeys.put(version.id(),keys);
        }
        for(var entry:groups.entrySet()){
            var group=entry.getValue();String original=(String)group.get("original_id");
            if(groups.values().stream().filter(g->original.equals(g.get("original_id"))).count()>1)group.put("question_id",original.substring(0,Math.min(40,original.length()))+"_"+entry.getKey().substring(0,16));
        }
        var matrix=new ArrayList<Map<String,Object>>();var members=new ArrayList<Map<String,Object>>();
        for(var version:versions){
            var origins=new HashMap<String,Map<String,Object>>();jdbc.queryForList("SELECT o.* FROM wrangling_origin_classification o JOIN survey_response r ON r.id=o.response_id WHERE r.publication_version_id=? AND o.created_at<=? ORDER BY o.revision_order DESC",version.id(),cutoff).forEach(row->origins.putIfAbsent((String)row.get("response_id"),row));
            for(var verified:captures.verifiedVersionInputs(version.id(),cutoff,start,end)){
                var input=verified.input();String responseId=input.responseId();
                if(!input.hasSnapshot()||!input.definition().equals(version.content().definition())||!input.studyId().equals(version.content().studyId()))throw corrupt();
                LocalDate received=Instant.parse(input.submittedAt()).atZone(ZONE).toLocalDate();
                if(start!=null&&received.isBefore(start)||end!=null&&received.isAfter(end))continue;
                var classified=origins.containsKey(responseId)?List.of(origins.get(responseId)):List.<Map<String,Object>>of();
                String origin=classified.isEmpty()?"NO_DECLARADO":(String)classified.getFirst().get("origin");
                var values=new LinkedHashMap<String,Object>();var states=new LinkedHashMap<String,String>();
                for(var q:version.content().definition().questions()){
                    String state=input.states().get(q.id());var answers=input.answers().getOrDefault(q.id(),List.of());
                    if(!Set.of("CON_VALOR","SIN_RESPUESTA","NO_APLICA").contains(Objects.toString(state,""))||state.equals("CON_VALOR")==answers.isEmpty()||state.equals("NO_APLICA")==q.applicable(input.answers()))throw corrupt();
                    var group=groups.get(sourceKeys.get(version.id()).get(q.id()));String key=(String)group.get("question_id");Object value=null;
                    if(state.equals("CON_VALOR")){
                        if(group.get("treatment").equals("COBERTURA"))value=true;
                        else if(group.get("treatment").equals("MAGNITUD_ENTERA")){if(answers.size()!=1)throw corrupt();try{value=Integer.parseInt(answers.getFirst());}catch(NumberFormatException ex){throw corrupt();}}
                        else{if(!q.options().stream().map(Question.Option::value).toList().containsAll(answers)||new HashSet<>(answers).size()!=answers.size())throw corrupt();value=q.type().equals("checkbox")?answers.stream().sorted().toList():answers.size()==1?answers.getFirst():null;if(value==null)throw corrupt();}
                    }states.put(key,state);values.put(key,value);
                }
                matrix.add(map("response_id",responseId,"version_id",version.id(),"instrument_version",version.code(),"submitted_at",input.submittedAt(),"origin",origin,"values",values,"states",states));
                members.add(map("response_id",responseId,"version_id",version.id(),"submitted_at",input.submittedAt(),"capture_hash",input.captureHash(),"capture_provenance",input.provenance(),"origin",origin,"origin_revision",classified.isEmpty()?null:map("id",classified.getFirst().get("id"),"hash",CanonicalJson.hash(map("origin",origin,"reason",classified.getFirst().get("reason"),"actor",classified.getFirst().get("actor")))),"import_reference",verified.importReference()));
            }
        }
        var anchorConfig=preparation.configuration(anchor.id());
        var fields=anchor.content().definition().questions().stream().map(q->{String unit=unit(q,anchorConfig);return new GenericPreparationService.Field(q.id(),!q.options().isEmpty()?"NOMINAL":q.type().equals("number")&&!unit.isBlank()?"MAGNITUD_ENTERA":"EXCLUIR",unit);}).toList();
        var config=new GenericPreparationService.Config(anchor.id(),anchor.hash(),fields);String configId=UUID.randomUUID().toString();
        var study=jdbc.queryForMap("SELECT id,name,reference_year,status FROM survey_study WHERE id=?",anchor.content().studyId());
        var sourceVersions=versions.stream().map(v->map("id",v.id(),"code",v.code(),"publication_hash",v.hash(),"definition",v.content().definition(),"instrument_status",jdbc.queryForObject("SELECT status FROM instrument_snapshot WHERE survey_slug=? AND instrument_version=?",String.class,v.slug(),v.code()))).toList();
        var logical=map("rules_version",GenericPreparationService.TOTAL_RULES,"study_id",anchor.content().studyId(),"study",study,"instrument_status","BORRADOR","survey_id",anchor.surveyId(),"version_id",anchor.id(),"survey_slug",slug,
                "instrument_version",String.join(" + ",versions.stream().map(SurveyPublicationService.Version::code).toList()),"publication_hash",anchor.hash(),"definition",anchor.content().definition(),"configuration",config,"config_id",configId,"config_revision",0,"config_hash",CanonicalJson.hash(config),
                "origin","TODOS","mode","BORRADOR","from_date",start==null?null:start.toString(),"until_date",end==null?null:end.toString(),"timezone",ZONE.getId(),"source_versions",sourceVersions,"members",members,"matrix",matrix,"dictionary",new ArrayList<>(groups.values()),"selections",List.of(),"coverage",List.of(),"excluded_questions",List.of(),"unit_of_analysis","UN_ENVIO","privacy","Los campos de texto y contacto aportan únicamente cobertura; sus valores originales se consultan en Ver envíos.");
        logical.put("definition_hash",versions.size()==1?CanonicalJson.hash(anchor.content().definition()):CanonicalJson.hash(sourceVersions));
        logical.put("instrument_status",versions.size()==1?sourceVersions.getFirst().get("instrument_status"):"POR_VERSION");
        logical=CanonicalJson.read(CanonicalJson.json(logical),Map.class);
        var result=ReportAdvancedProjection.enrich(analyses.calculate(logical),logical,cutoff.toInstant().toString());
        return new Snapshot(UUID.randomUUID().toString(),anchor.surveyId(),slug,cutoff.toInstant().toString(),List.copyOf(ids),from,until,allVersions,logical,result);
    }
    private List<Question> dependencies(Question question,SurveyDefinition definition){var result=new ArrayList<Question>();var next=question;var visited=new HashSet<String>();while(next.dependsOn()!=null&&visited.add(next.dependsOn())){String id=next.dependsOn();next=definition.questions().stream().filter(q->q.id().equals(id)).findFirst().orElseThrow(WorkspaceResultsService::corrupt);result.add(next);}return result;}
    private String unit(Question q,GenericPreparationService.Configuration config){
        if(!q.type().equals("number"))return "";
        var declared=config.content().fields().stream().filter(f->q.id().equals(f.questionId())&&f.treatment().equals("MAGNITUD_ENTERA")).findFirst();if(declared.isPresent())return declared.get().unit();
        if(new SurveyCatalog().all().stream().flatMap(s->s.questions().stream()).noneMatch(q::equals))return "";
        return Map.of("edad","años completos","anio_egreso","año calendario","meses_vida_profesional","meses completos","meses_desempleado","meses completos").getOrDefault(q.id(),"");
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public String materialize(Snapshot snapshot,String actor){
        actor(actor);publications.lockForCapture(snapshot.versionIds().getFirst());
        var existing=jdbc.queryForList("SELECT id FROM survey_preparation_run WHERE id=?",String.class,snapshot.id());
        if(!existing.isEmpty()){var saved=preparation.bundle(snapshot.id());if(!((Map<?,?>)saved.get("logical")).get("members").equals(snapshot.logical().get("members")))throw corrupt();return analysis(snapshot.id(),actor);}
        var logical=new LinkedHashMap<>(snapshot.logical());String configId=(String)logical.get("config_id");
        int revision=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0)+1 FROM survey_preparation_config WHERE version_id=?",Integer.class,logical.get("version_id"));
        jdbc.update("INSERT INTO survey_preparation_config (id,version_id,revision,actor,created_at,config_json,config_hash) VALUES (?,?,?,?,?,?,?)",configId,logical.get("version_id"),revision,actor,OffsetDateTime.now(ZoneOffset.UTC),CanonicalJson.json(logical.get("configuration")),logical.get("config_hash"));logical.put("config_revision",revision);
        var bundle=map("run_id",snapshot.id(),"created_at",OffsetDateTime.now(ZoneOffset.UTC).toInstant().toString(),"cutoff_utc",snapshot.cutoff(),"actor",actor,"logical_hash",CanonicalJson.hash(logical),"logical",logical);
        preparation.persist(bundle);return analysis(snapshot.id(),actor);
    }
    private String analysis(String prepared,String actor){var ids=jdbc.queryForList("SELECT id FROM survey_general_analysis WHERE preparation_id=? ORDER BY created_at,id LIMIT 1",String.class,prepared);return ids.isEmpty()?analyses.create(prepared,actor):ids.getFirst();}
    @Transactional
    public String save(Snapshot snapshot,String name,String actor){
        actor(actor);name=name==null||name.isBlank()?"Corte "+snapshot.cutoff().substring(0,10):name.strip();if(name.length()>200)throw new IllegalArgumentException("El nombre admite hasta 200 caracteres.");
        materialize(snapshot,actor);var existing=jdbc.queryForList("SELECT id FROM survey_result_cut WHERE preparation_id=?",String.class,snapshot.id());if(!existing.isEmpty())return existing.getFirst();
        String id=UUID.randomUUID().toString();jdbc.update("INSERT INTO survey_result_cut (id,survey_id,name,preparation_id,snapshot_json,snapshot_hash,actor,created_at) VALUES (?,?,?,?,?,?,?,?)",id,snapshot.surveyId(),name,snapshot.id(),CanonicalJson.json(snapshot),CanonicalJson.hash(snapshot),actor,OffsetDateTime.now(ZoneOffset.UTC));return id;
    }
    @Transactional(readOnly=true)
    @SuppressWarnings("unchecked")
    public Cut cut(String slug,String id){var rows=jdbc.queryForList("SELECT c.* FROM survey_result_cut c JOIN survey s ON s.id=c.survey_id WHERE c.id=? AND s.slug=?",id,slug);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Corte no encontrado en esta encuesta.");var row=rows.getFirst();var snapshot=CanonicalJson.read((String)row.get("snapshot_json"),Snapshot.class);if(!CanonicalJson.hash(snapshot).equals(row.get("snapshot_hash"))||!snapshot.surveyId().equals(row.get("survey_id"))||!snapshot.id().equals(row.get("preparation_id")))throw corrupt();
        var prepared=preparation.bundle(snapshot.id());var source=new LinkedHashMap<>((Map<String,Object>)prepared.get("logical"));source.put("config_revision",snapshot.logical().get("config_revision"));
        if(!CanonicalJson.hash(source).equals(CanonicalJson.hash(snapshot.logical()))||!snapshot.cutoff().equals(prepared.get("cutoff_utc"))||!CanonicalJson.hash(ReportAdvancedProjection.enrich(analyses.calculate(snapshot.logical()),snapshot.logical(),snapshot.cutoff())).equals(CanonicalJson.hash(snapshot.result())))throw corrupt();
        return new Cut(id,(String)row.get("name"),row.get("created_at").toString(),snapshot);}
    public List<Map<String,Object>> cuts(String slug){return jdbc.query("SELECT c.id,c.name,c.created_at,c.snapshot_json FROM survey_result_cut c JOIN survey s ON s.id=c.survey_id WHERE s.slug=? ORDER BY c.created_at DESC,c.id DESC",(rs,n)->map("id",rs.getString(1),"name",rs.getString(2),"created_at",rs.getObject(3,OffsetDateTime.class).toInstant().toString(),"versions",CanonicalJson.read(rs.getString(4),Snapshot.class).logical().get("instrument_version")),slug);}
    public CutState cutState(String slug,String id){var cut=cut(slug,id);String p=cut.snapshot().id();var a=jdbc.queryForList("SELECT id,result_hash FROM survey_general_analysis WHERE preparation_id=? ORDER BY id",p);var c=jdbc.queryForList("SELECT id,result_hash FROM survey_general_cross WHERE preparation_id=? ORDER BY id",p);var r=jdbc.queryForList("SELECT id,content_hash FROM generated_report WHERE analysis_id IN (SELECT id FROM survey_general_analysis WHERE preparation_id=?) ORDER BY id",p);return new CutState(a.size(),c.size(),r.size(),CanonicalJson.hash(map("snapshot",cut.snapshot(),"analyses",a,"crosses",c,"reports",r)));}
    @Transactional
    public void deleteCut(String slug,String id,String expectedHash,String actor){
        actor(actor);var cut=cut(slug,id);publications.lockForCapture(cut.snapshot().versionIds().getFirst());String p=cut.snapshot().id();
        jdbc.queryForList("SELECT id FROM survey_preparation_run WHERE id=? FOR UPDATE",p);jdbc.queryForList("SELECT id FROM survey_general_analysis WHERE preparation_id=? ORDER BY id FOR UPDATE",p);jdbc.queryForList("SELECT id FROM survey_general_cross WHERE preparation_id=? ORDER BY id FOR UPDATE",p);jdbc.queryForList("SELECT id FROM generated_report WHERE analysis_id IN (SELECT id FROM survey_general_analysis WHERE preparation_id=?) ORDER BY id FOR UPDATE",p);
        var state=cutState(slug,id);if(!state.hash().equals(expectedHash))throw new ResponseStatusException(HttpStatus.CONFLICT,"El corte o sus productos cambiaron. Revisa la confirmación actualizada.");
        String reports="SELECT id FROM generated_report WHERE analysis_id IN (SELECT id FROM survey_general_analysis WHERE preparation_id=?)";
        jdbc.update("DELETE FROM generated_report_cross WHERE report_id IN ("+reports+")",p);jdbc.update("DELETE FROM generated_report WHERE analysis_id IN (SELECT id FROM survey_general_analysis WHERE preparation_id=?)",p);jdbc.update("DELETE FROM survey_general_cross WHERE preparation_id=?",p);jdbc.update("DELETE FROM survey_general_analysis WHERE preparation_id=?",p);jdbc.update("DELETE FROM survey_result_cut WHERE id=?",id);jdbc.update("DELETE FROM survey_preparation_run WHERE id=?",p);
        var event=map("entity","CORTE_DATOS","cut_id",id,"counts",state,"responses_preserved",true);
        jdbc.update("INSERT INTO survey_management_event VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),cut.snapshot().surveyId(),slug,"ELIMINAR",actor,OffsetDateTime.now(ZoneOffset.UTC),CanonicalJson.json(event),state.hash());
    }
    private static void actor(String actor){if(actor==null||actor.isBlank()||actor.length()>100)throw new IllegalArgumentException("Administrador no válido.");}
    private static IllegalStateException corrupt(){return new IllegalStateException("La captura o el corte no coincide con sus fuentes verificadas.");}
}

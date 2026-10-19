package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.GenericCrossService;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Reusable presentation recipes: no source rows, source identifiers or report comments. */
@Service
public class ReportPresentationTemplates {
    public record Recipe(String row,String column,String title,List<String> representations) {}
    public record Definition(String instrumentHash,ReportCustomization.Configuration configuration,List<Recipe> crosses) {}
    public record Summary(String id,String name) {}
    public record Stored(String id,String name,String hash,Definition definition) {}
    private final JdbcTemplate jdbc;
    private final ReportService reports;
    private final GenericCrossService crosses;
    private final ReportCrossComposer composer;
    private final bo.edu.umss.mercado.statistics.GenericAnalysisService analyses;
    public ReportPresentationTemplates(JdbcTemplate jdbc,ReportService reports,GenericCrossService crosses,ReportCrossComposer composer,bo.edu.umss.mercado.statistics.GenericAnalysisService analyses){this.jdbc=jdbc;this.reports=reports;this.crosses=crosses;this.composer=composer;this.analyses=analyses;}
    public List<Summary> recent(){return jdbc.query("SELECT id,name FROM report_presentation_template ORDER BY created_at DESC,id LIMIT 100",(r,n)->new Summary(r.getString(1),r.getString(2)));}
    public Stored stored(String id){var rows=jdbc.queryForList("SELECT name,definition_json,definition_hash FROM report_presentation_template WHERE id=?",id);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plantilla no encontrada.");var row=rows.getFirst();var definition=CanonicalJson.read((String)row.get("definition_json"),Definition.class);if(!CanonicalJson.hash(definition).equals(row.get("definition_hash")))throw new IllegalStateException("La plantilla no coincide con su huella.");String name=(String)row.get("name");return new Stored(id,name,CanonicalJson.hash(Map.of("name",name,"definition",definition)),definition);}
    private static String name(String name){if(name==null||name.isBlank()||name.strip().length()>200)throw new IllegalArgumentException("Escribe un nombre de hasta 200 caracteres.");return name.strip();}
    private void event(String id,String action,String actor,Object snapshot){if(actor==null||actor.isBlank()||actor.length()>100)throw new IllegalArgumentException("Administrador requerido.");String json=CanonicalJson.json(snapshot);jdbc.update("INSERT INTO report_template_management_event(id,template_id,action,actor,created_at,snapshot_json,snapshot_hash) VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,action,actor,OffsetDateTime.now(),json,CanonicalJson.hash(snapshot));}
    private Stored lock(String id,String hash){jdbc.queryForList("SELECT id FROM report_presentation_template WHERE id=? FOR UPDATE",id);var stored=stored(id);if(!stored.hash().equals(hash))throw new ResponseStatusException(HttpStatus.CONFLICT,"La plantilla cambió. Recarga antes de continuar.");return stored;}
    @Transactional public void delete(String id,String hash,boolean confirmed,String actor){if(!confirmed)throw new IllegalArgumentException("Confirma la eliminación de la plantilla.");var previous=lock(id,hash);event(id,"ELIMINAR",actor,previous);jdbc.update("DELETE FROM report_presentation_template WHERE id=?",id);}
    @Transactional public String duplicate(String id,String hash,String name,String actor){var previous=lock(id,hash);String copy=UUID.randomUUID().toString();var d=previous.definition();var definition=new Definition(d.instrumentHash(),clean(d.configuration(),"",List.of(),List.of()),d.crosses());jdbc.update("INSERT INTO report_presentation_template(id,name,actor,created_at,definition_json,definition_hash) VALUES(?,?,?,?,?,?)",copy,name(name),actor,OffsetDateTime.now(),CanonicalJson.json(definition),CanonicalJson.hash(definition));event(copy,"DUPLICAR",actor,Map.of("source",id,"template",stored(copy)));return copy;}
    @Transactional public String save(ReportCustomization.Configuration config,String previewHash,String id,String expectedHash,String name,String actor){
        var content=reports.preview(config,actor);if(!CanonicalJson.hash(content).equals(previewHash))throw new ResponseStatusException(HttpStatus.CONFLICT,"Revisa la vista previa actual antes de guardar la plantilla.");
        var recipes=new ArrayList<Recipe>();for(String crossId:config.crossIds()){var source=crosses.get(crossId);var spec=CanonicalJson.read(CanonicalJson.json(source.get("spec")),GenericCrossService.Spec.class);var setting=config.crossSettings()==null?null:config.crossSettings().stream().filter(c->c.id().equals(crossId)).findFirst().orElse(null);recipes.add(new Recipe(spec.rowQuestion(),spec.columnQuestion(),setting==null?"Comparación":setting.title(),setting==null?null:setting.representations()));}
        var definition=new Definition(Objects.toString(((Map<?,?>)content.get("context")).get("definition_hash")),clean(config,"",List.of(),List.of()),recipes);String title=name(name==null||name.isBlank()?config.title():name);
        if(id==null||id.isBlank()){id=UUID.randomUUID().toString();jdbc.update("INSERT INTO report_presentation_template(id,name,actor,created_at,definition_json,definition_hash) VALUES(?,?,?,?,?,?)",id,title,actor,OffsetDateTime.now(),CanonicalJson.json(definition),CanonicalJson.hash(definition));event(id,"CREAR",actor,stored(id));}
        else{var previous=lock(id,expectedHash);if(!previous.definition().instrumentHash().equals(definition.instrumentHash()))throw new IllegalArgumentException("La fuente no corresponde al instrumento de esta plantilla.");jdbc.update("UPDATE report_presentation_template SET name=?,actor=?,definition_json=?,definition_hash=? WHERE id=?",title,actor,CanonicalJson.json(definition),CanonicalJson.hash(definition),id);event(id,"EDITAR",actor,Map.of("before",previous,"after",stored(id)));}
        return id;
    }
    @Transactional
    public String generate(ReportCustomization.Configuration config,String hash,boolean save,String name,String actor){
        if(save && name!=null && name.strip().length()>200)throw new IllegalArgumentException("El nombre de plantilla admite hasta 200 caracteres.");
        String report=reports.generateCustomized(config,hash,actor);
        if(save)save(config,hash,"","",name,actor);
        return report;
    }
    @Transactional
    public ReportCustomization.Configuration apply(String id,String analysisId,String actor){
        var rows=jdbc.queryForList("SELECT definition_json,definition_hash FROM report_presentation_template WHERE id=?",id);
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plantilla no encontrada.");
        var row=rows.getFirst();var definition=CanonicalJson.read((String)row.get("definition_json"),Definition.class);
        if(!CanonicalJson.hash(definition).equals(row.get("definition_hash")))throw new IllegalArgumentException("La plantilla no supera la verificación de integridad.");
        var c=definition.configuration();
        // Verify the new frozen instrument before creating any exploratory comparison.
        var preview=reports.preview(ReportCustomization.defaults(analyses.get(analysisId),List.of()),actor);
        if(!definition.instrumentHash().equals(((Map<?,?>)preview.get("context")).get("definition_hash")))throw new IllegalArgumentException("La plantilla requiere la misma definición del instrumento. Selecciona una compatible.");
        var ids=new ArrayList<String>();var settings=new ArrayList<ReportCustomization.Cross>();
        for(var recipe:definition.crosses()){
            var pair=new ReportCrossComposer.Pair(analysisId,recipe.row(),recipe.column(),recipe.representations());
            var saved=composer.save(new ReportCrossComposer.Save(pair,CanonicalJson.hash(composer.preview(pair))),actor);
            String crossId=(String)saved.get("id");ids.add(crossId);settings.add(new ReportCustomization.Cross(crossId,recipe.title(),"",recipe.representations()));
        }
        var defaults=ReportCustomization.defaults(analyses.get(analysisId),List.of());var targetIds=defaults.questions().stream().map(ReportCustomization.Question::id).toList();
        if(c.questions().stream().anyMatch(q->q.included()&&!targetIds.contains(q.id())))throw new IllegalArgumentException("La fuente no contiene todas las preguntas incluidas en la plantilla.");
        var questions=defaults.questions().stream().map(q->c.questions().stream().filter(saved->saved.id().equals(q.id())).findFirst().orElse(new ReportCustomization.Question(q.id(),false,q.title(),"",List.of()))).toList();
        var adapted=new ReportCustomization.Configuration(analysisId,c.title(),c.introduction(),c.context(),c.tables(),c.charts(),"",questions,ids,settings,c.advanced());
        var applied=clean(adapted,analysisId,ids,settings);reports.preview(applied,actor);return applied;
    }
    private static ReportCustomization.Configuration clean(ReportCustomization.Configuration c,String analysis,List<String> ids,List<ReportCustomization.Cross> settings){
        return new ReportCustomization.Configuration(analysis,c.title(),c.introduction(),c.context(),c.tables(),c.charts(),"",c.questions().stream().map(q->new ReportCustomization.Question(q.id(),q.included(),q.title(),"",q.sensitiveAcknowledged()?List.of("table"):q.representations())).toList(),ids,settings,c.advanced()==null?null:c.advanced().stream().map(a->new ReportCustomization.Advanced(a.type(),a.questions(),a.title(),"",a.included())).toList());
    }
}

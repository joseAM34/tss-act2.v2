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
    private final JdbcTemplate jdbc;
    private final ReportService reports;
    private final GenericCrossService crosses;
    private final ReportCrossComposer composer;
    private final bo.edu.umss.mercado.statistics.GenericAnalysisService analyses;
    public ReportPresentationTemplates(JdbcTemplate jdbc,ReportService reports,GenericCrossService crosses,ReportCrossComposer composer,bo.edu.umss.mercado.statistics.GenericAnalysisService analyses){this.jdbc=jdbc;this.reports=reports;this.crosses=crosses;this.composer=composer;this.analyses=analyses;}
    public List<Summary> recent(){return jdbc.query("SELECT id,name FROM report_presentation_template ORDER BY created_at DESC,id LIMIT 100",(r,n)->new Summary(r.getString(1),r.getString(2)));}
    @Transactional
    public String generate(ReportCustomization.Configuration config,String hash,boolean save,String name,String actor){
        if(save && name!=null && name.strip().length()>200)throw new IllegalArgumentException("El nombre de plantilla admite hasta 200 caracteres.");
        String report=reports.generateCustomized(config,hash,actor);
        if(save){
            var content=reports.preview(config,actor);
            var recipes=new ArrayList<Recipe>();
            for(String id:config.crossIds()){
                var source=crosses.get(id);
                var spec=CanonicalJson.read(CanonicalJson.json(source.get("spec")),GenericCrossService.Spec.class);
                var setting=config.crossSettings()==null?null:config.crossSettings().stream().filter(c->c.id().equals(id)).findFirst().orElse(null);
                recipes.add(new Recipe(spec.rowQuestion(),spec.columnQuestion(),setting==null?"Comparación":setting.title(),setting==null?null:setting.representations()));
            }
            var definition=new Definition(Objects.toString(((Map<?,?>)content.get("context")).get("definition_hash")),clean(config,"",List.of(),List.of()),recipes);
            String title=name==null||name.isBlank()?config.title():name.strip();
            jdbc.update("INSERT INTO report_presentation_template(id,name,actor,created_at,definition_json,definition_hash) VALUES (?,?,?,?,?,?)",UUID.randomUUID().toString(),title,actor,OffsetDateTime.now(),CanonicalJson.json(definition),CanonicalJson.hash(definition));
        }
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
        var applied=clean(c,analysisId,ids,settings);reports.preview(applied,actor);return applied;
    }
    private static ReportCustomization.Configuration clean(ReportCustomization.Configuration c,String analysis,List<String> ids,List<ReportCustomization.Cross> settings){
        return new ReportCustomization.Configuration(analysis,c.title(),c.introduction(),c.context(),c.tables(),c.charts(),"",c.questions().stream().map(q->new ReportCustomization.Question(q.id(),q.included(),q.title(),"",q.representations())).toList(),ids,settings,c.advanced()==null?null:c.advanced().stream().map(a->new ReportCustomization.Advanced(a.type(),a.questions(),a.title(),"",a.included())).toList());
    }
}

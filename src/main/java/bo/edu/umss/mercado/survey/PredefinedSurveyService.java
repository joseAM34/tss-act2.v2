package bo.edu.umss.mercado.survey;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.data.InstrumentRegistry;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Optional builders, never an application startup seed or a special survey type. */
@Service
public class PredefinedSurveyService {
    public record Installed(String key,String versionId,boolean created) {}
    private final OfficialSurveyTemplates templates;
    private final DraftSurveyService drafts;
    private final SurveyPublicationService publications;
    private final JdbcTemplate jdbc;
    public PredefinedSurveyService(OfficialSurveyTemplates templates,DraftSurveyService drafts,SurveyPublicationService publications,JdbcTemplate jdbc){
        this.templates=templates;this.drafts=drafts;this.publications=publications;this.jdbc=jdbc;
    }
    public List<OfficialSurveyTemplates.Template> templates(){return templates.all();}
    @Transactional
    public List<Installed> install(List<String> keys,String actor){
        if(keys==null || keys.isEmpty() || keys.size()>templates.all().size() || new HashSet<>(keys).size()!=keys.size())throw new IllegalArgumentException("Selecciona las encuestas que quieres establecer.");
        // Serialize duplicate clicks while allowing edited originals to remain independent.
        jdbc.queryForList("SELECT id FROM survey_study WHERE id=? FOR UPDATE",InstrumentRegistry.STUDY_ID);
        var result=new ArrayList<Installed>();
        for(String key:keys){
            var template=templates.get(key);
            var grouped=new LinkedHashMap<String,List<Question>>();
            template.questions().forEach(q->grouped.computeIfAbsent(q.section(),ignored->new ArrayList<>()).add(q));
            var source=new SurveyDefinition(template.key(),template.title(),template.description(),template.version(),grouped.values().stream().flatMap(List::stream).toList());
            String existing=null;
            for(var row:jdbc.queryForList("SELECT v.id,v.status FROM survey_version v JOIN survey s ON s.id=v.survey_id WHERE v.source_slug=? AND v.source_version=? AND s.archived=FALSE AND v.id=COALESCE(s.draft_version_id,s.active_version_id,(SELECT last_version.id FROM survey_version last_version WHERE last_version.survey_id=s.id ORDER BY last_version.created_at DESC,last_version.id DESC LIMIT 1)) ORDER BY v.created_at DESC,v.id",key,template.version())){
                String id=(String)row.get("id");
                SurveyDefinition definition="BORRADOR".equals(row.get("status"))?drafts.get(id).definition():publications.get(id).content().definition();
                if(definition.title().equals(source.title()) && definition.description().equals(source.description()) && CanonicalJson.hash(definition.questions()).equals(CanonicalJson.hash(source.questions()))){existing=id;break;}
            }
            result.add(new Installed(key,existing==null?drafts.fromTemplate(source,actor):existing,existing==null));
        }
        return List.copyOf(result);
    }
}

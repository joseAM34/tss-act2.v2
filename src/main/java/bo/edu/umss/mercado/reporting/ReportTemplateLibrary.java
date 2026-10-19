package bo.edu.umss.mercado.reporting;
import bo.edu.umss.mercado.statistics.WorkspaceResultsService;
import bo.edu.umss.mercado.survey.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service public class ReportTemplateLibrary {
    private final ReportPresentationTemplates templates;private final ReportService reports;private final JdbcTemplate jdbc;private final SurveyPublicationService publications;private final SurveyWorkspaceService workspaces;private final WorkspaceResultsService results;
    public ReportTemplateLibrary(ReportPresentationTemplates templates,ReportService reports,JdbcTemplate jdbc,SurveyPublicationService publications,SurveyWorkspaceService workspaces,WorkspaceResultsService results){this.templates=templates;this.reports=reports;this.jdbc=jdbc;this.publications=publications;this.workspaces=workspaces;this.results=results;}
    public record Entry(String id,String name,String hash,boolean legacy){}
    public List<Entry> entries(){var entries=new ArrayList<Entry>();for(var s:templates.recent()){var t=templates.stored(s.id());entries.add(new Entry(t.id(),t.name(),t.hash(),false));}var seen=new HashSet<String>();for(var r:reports.revisions())if(seen.add(r.templateId())&&jdbc.queryForObject("SELECT COUNT(*) FROM hidden_legacy_report_template WHERE template_id=?",Integer.class,r.templateId())==0){var latest=reports.latest(r.templateId());entries.add(new Entry("legacy-"+latest.templateId(),latest.definition().title(),latest.hash(),true));}return entries;}
    public ReportService.Revision legacy(String id){if(!id.startsWith("legacy-"))throw new IllegalArgumentException("Referencia de plantilla anterior no válida.");String root=id.substring(7);if(jdbc.queryForObject("SELECT COUNT(*) FROM hidden_legacy_report_template WHERE template_id=?",Integer.class,root)>0)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Plantilla eliminada de la biblioteca.");return reports.latest(root);}
    public Entry entry(String id){if(id.startsWith("legacy-")){var r=legacy(id);return new Entry(id,r.definition().title(),r.hash(),true);}var t=templates.stored(id);return new Entry(t.id(),t.name(),t.hash(),false);}
    @org.springframework.transaction.annotation.Transactional
    public void hideLegacy(String id,String expectedHash,boolean confirmed,String actor){if(!confirmed)throw new IllegalArgumentException("Confirma la eliminación de la plantilla.");if(actor==null||actor.isBlank()||actor.length()>100)throw new IllegalArgumentException("Administrador requerido.");var previous=legacy(id);if(!previous.hash().equals(expectedHash))throw new ResponseStatusException(HttpStatus.CONFLICT,"La plantilla cambió. Recarga la biblioteca.");jdbc.update("INSERT INTO hidden_legacy_report_template(template_id,actor,created_at) VALUES(?,?,?)",previous.templateId(),actor,java.time.OffsetDateTime.now());jdbc.update("INSERT INTO report_template_management_event VALUES(?,?,?,?,?,?,?)",UUID.randomUUID().toString(),id,"ELIMINAR",actor,java.time.OffsetDateTime.now(),bo.edu.umss.mercado.data.CanonicalJson.json(previous),bo.edu.umss.mercado.data.CanonicalJson.hash(previous));}
    public List<Map<String,Object>> cuts(){var list=new ArrayList<Map<String,Object>>();for(var survey:workspaces.all(true))if(!survey.legacy())for(var c:results.cuts(survey.slug()))list.add(Map.of("id",c.get("id"),"slug",survey.slug(),"label",survey.title()+" · "+c.get("name")+" · "+c.get("created_at")));return list;}
    public Map<String,Object> picker(String id,String mode,String version){var model=new LinkedHashMap<String,Object>();model.put("templateEntry",id==null||id.isBlank()?null:entry(id));model.put("templateMode",mode);model.put("templateId",id==null?"":id);model.put("versions",publications.recent());model.put("cuts",cuts());model.put("workspaceVersionId",version);return model;}
}

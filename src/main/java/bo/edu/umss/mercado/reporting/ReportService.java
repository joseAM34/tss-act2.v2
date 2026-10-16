package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.GenericAnalysisService;
import bo.edu.umss.mercado.statistics.GenericCrossService;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Plantillas declarativas y documentos congelados; ningún texto administrativo ejecuta código. */
@Service
public class ReportService {
    public static final List<String> SECTIONS = List.of("CONTEXTO", "DESCRIPTIVOS", "CRUCES", "COMENTARIOS");
    public record Template(String title, String introduction, List<String> sections, boolean charts) {
        public Template { sections = sections == null ? null : List.copyOf(sections); }
    }
    public record Revision(String id, String templateId, int revision, String actor, String createdAt, String hash, Template definition) {}
    public record Summary(String id, String title, String createdAt) {}
    private final JdbcTemplate jdbc;
    private final GenericAnalysisService analyses;
    private final GenericCrossService crosses;
    private final GenericPreparationService preparation;
    public ReportService(JdbcTemplate jdbc, GenericAnalysisService analyses, GenericCrossService crosses, GenericPreparationService preparation) {
        this.jdbc = jdbc; this.analyses = analyses; this.crosses = crosses; this.preparation = preparation;
    }
    @Transactional
    public String createTemplate(Template definition, String actor) {
        validate(definition); actor = text(actor,100,true,"Administrador");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO report_template (id, created_by, created_at) VALUES (?, ?, ?)",id,actor,OffsetDateTime.now(ZoneOffset.UTC));
        revise(id,0,definition,actor); return id;
    }
    @Transactional
    public String revise(String templateId, int expectedRevision, Template definition, String actor) {
        validate(definition); actor = text(actor,100,true,"Administrador");
        var roots = jdbc.queryForList("SELECT id FROM report_template WHERE id = ? FOR UPDATE",templateId);
        if (roots.isEmpty()) throw missing("Plantilla no encontrada.");
        int latest = jdbc.queryForObject("SELECT COALESCE(MAX(revision), 0) FROM report_template_revision WHERE template_id = ?",Integer.class,templateId);
        if (latest != expectedRevision) throw new ResponseStatusException(HttpStatus.CONFLICT,"Otra revisión fue guardada. Abre de nuevo la plantilla.");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO report_template_revision (id, template_id, revision, actor, created_at, definition_json, definition_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,templateId,Math.addExact(latest,1),actor,OffsetDateTime.now(ZoneOffset.UTC),CanonicalJson.json(definition),CanonicalJson.hash(definition));
        return id;
    }
    public Revision revision(String id) {
        var rows = jdbc.queryForList("SELECT * FROM report_template_revision WHERE id = ?",id);
        if (rows.isEmpty()) throw missing("Revisión de plantilla no encontrada.");
        var row = rows.getFirst(); var definition = CanonicalJson.read((String)row.get("definition_json"),Template.class);
        validate(definition); if (!CanonicalJson.hash(definition).equals(row.get("definition_hash"))) throw corrupt();
        return new Revision(id,(String)row.get("template_id"),((Number)row.get("revision")).intValue(),(String)row.get("actor"),row.get("created_at").toString(),(String)row.get("definition_hash"),definition);
    }
    public Revision latest(String templateId) {
        var ids = jdbc.queryForList("SELECT id FROM report_template_revision WHERE template_id = ? ORDER BY revision DESC LIMIT 1",String.class,templateId);
        if (ids.isEmpty()) throw missing("Plantilla no encontrada."); return revision(ids.getFirst());
    }
    public List<Revision> revisions() {
        return jdbc.queryForList("SELECT id FROM report_template_revision ORDER BY created_at DESC, id DESC LIMIT 100",String.class).stream().map(this::revision).toList();
    }
    @Transactional
    @SuppressWarnings("unchecked")
    public String generate(String revisionId, String analysisId, List<String> crossIds, String comment, String actor) {
        var template = revision(revisionId); var analysis = analyses.get(analysisId);
        comment = text(comment,8000,false,"Comentario"); actor = text(actor,100,true,"Administrador");
        if (crossIds == null || crossIds.size() > 10 || new HashSet<>(crossIds).size() != crossIds.size()) throw new IllegalArgumentException("Selecciona hasta diez cruces distintos.");
        if (!crossIds.isEmpty() && !template.definition().sections().contains("CRUCES")) throw new IllegalArgumentException("La plantilla no incluye la sección Cruces. Usa otra revisión o quita los cruces seleccionados.");
        if (!comment.isBlank() && !template.definition().sections().contains("COMENTARIOS")) throw new IllegalArgumentException("La plantilla no incluye Comentarios. Usa otra revisión o deja el comentario vacío.");
        var snapshots = new ArrayList<Map<String,Object>>();
        for (String crossId : crossIds) {
            var cross = crosses.get(crossId);
            if (!analysis.get("preparation_id").equals(cross.get("preparation_id")) || !analysis.get("source_hash").equals(cross.get("source_hash"))) throw new IllegalArgumentException("Todos los cruces deben proceder del mismo conjunto exacto que el análisis.");
            snapshots.add(map("id",crossId,"result_hash",cross.get("result_hash"),"source_hash",cross.get("source_hash"),"spec_hash",cross.get("spec_hash"),"spec",cross.get("spec"),"result",cross.get("result")));
        }
        var prepared = preparation.bundle((String)analysis.get("preparation_id")); var logical = (Map<String,Object>) prepared.get("logical");
        var content = map("format","REPORTE_HTML_V1","template_revision_id",revisionId,"template_id",template.templateId(),"template_revision",template.revision(),"template_hash",template.hash(),"template",template.definition(),
                "analysis",map("id",analysisId,"result_hash",analysis.get("result_hash"),"source_hash",analysis.get("source_hash"),"result",analysis.get("result")),
                "preparation_id",analysis.get("preparation_id"),"crosses",snapshots,"comment",comment,
                "context",map("study",logical.get("study"),"instrument_status",logical.get("instrument_status"),"definition_hash",logical.get("definition_hash"),
                    "publication_hash",logical.get("publication_hash"),"config_id",logical.get("config_id"),"config_hash",logical.get("config_hash"),"config_revision",logical.get("config_revision"),
                    "from_date",logical.get("from_date"),"until_date",logical.get("until_date"),"cutoff_utc",prepared.get("cutoff_utc"),"timezone",logical.get("timezone"),"unit_of_analysis",logical.get("unit_of_analysis")));
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO generated_report (id, template_revision_id, analysis_id, actor, created_at, content_json, content_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,revisionId,analysisId,actor,OffsetDateTime.now(ZoneOffset.UTC),CanonicalJson.json(content),CanonicalJson.hash(content));
        for (int i = 0; i < crossIds.size(); i++) jdbc.update("INSERT INTO generated_report_cross (report_id, cross_id, position) VALUES (?, ?, ?)",id,crossIds.get(i),i);
        return id;
    }
    @SuppressWarnings("unchecked")
    public Map<String,Object> get(String id) {
        var rows = jdbc.queryForList("SELECT * FROM generated_report WHERE id = ?",id);
        if (rows.isEmpty()) throw missing("Reporte no encontrado."); var row = rows.getFirst();
        Map<String,Object> content = CanonicalJson.read((String)row.get("content_json"),Map.class);
        if (!CanonicalJson.hash(content).equals(row.get("content_hash")) || !"REPORTE_HTML_V1".equals(content.get("format")) || !row.get("template_revision_id").equals(content.get("template_revision_id"))) throw corrupt();
        var template = revision((String)row.get("template_revision_id"));
        if (!template.hash().equals(content.get("template_hash")) || !CanonicalJson.hash(template.definition()).equals(CanonicalJson.hash(content.get("template")))) throw corrupt();
        var snapshot = (Map<String,Object>)content.get("analysis"); var analysis = analyses.get((String)row.get("analysis_id"));
        if (!row.get("analysis_id").equals(snapshot.get("id")) || !analysis.get("preparation_id").equals(content.get("preparation_id"))
                || !analysis.get("result_hash").equals(snapshot.get("result_hash")) || !analysis.get("source_hash").equals(snapshot.get("source_hash"))
                || !CanonicalJson.hash(snapshot.get("result")).equals(snapshot.get("result_hash"))) throw corrupt();
        var crossSnapshots = (List<Map<String,Object>>)content.get("crosses");
        var crossIds = jdbc.queryForList("SELECT cross_id FROM generated_report_cross WHERE report_id = ? ORDER BY position",String.class,id);
        if (!crossIds.equals(crossSnapshots.stream().map(s -> s.get("id")).toList())) throw corrupt();
        for (var s : crossSnapshots) {
            var cross = crosses.get((String)s.get("id"));
            if (!analysis.get("preparation_id").equals(cross.get("preparation_id")) || !cross.get("result_hash").equals(s.get("result_hash")) || !cross.get("source_hash").equals(s.get("source_hash"))
                    || !cross.get("spec_hash").equals(s.get("spec_hash")) || !CanonicalJson.hash(s.get("result")).equals(s.get("result_hash")) || !CanonicalJson.hash(s.get("spec")).equals(s.get("spec_hash"))) throw corrupt();
        }
        return map("id",id,"created_at",row.get("created_at").toString(),"actor",row.get("actor"),"logical_hash",row.get("content_hash"),"content",content);
    }
    public List<Summary> recent() {
        return jdbc.query("SELECT id, content_json, created_at FROM generated_report ORDER BY created_at DESC, id DESC LIMIT 100",(rs,n) -> {
            @SuppressWarnings("unchecked") var content = CanonicalJson.read(rs.getString(2),Map.class);
            return new Summary(rs.getString(1),((Map<?,?>)content.get("template")).get("title").toString(),rs.getObject(3,OffsetDateTime.class).toInstant().toString());
        });
    }
    private void validate(Template d) {
        if (d == null) throw new IllegalArgumentException("Plantilla vacía.");
        text(d.title(),200,true,"Título"); text(d.introduction(),4000,false,"Introducción");
        if (d.sections() == null || d.sections().isEmpty() || d.sections().size() > 4 || !SECTIONS.containsAll(d.sections()) || new HashSet<>(d.sections()).size() != d.sections().size()) throw new IllegalArgumentException("Elige secciones admitidas, sin repetir y en el orden deseado.");
    }
    private String text(String value,int max,boolean required,String label) {
        if (value == null || value.length() > max || required && value.isBlank() || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) throw new IllegalArgumentException(label + " no válido.");
        return value;
    }
    private ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
    private IllegalStateException corrupt() { return new IllegalStateException("El reporte, plantilla o fuentes no coinciden con sus huellas."); }
    private static Map<String,Object> map(Object... pairs) { var m = new LinkedHashMap<String,Object>(); for (int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return m; }
}

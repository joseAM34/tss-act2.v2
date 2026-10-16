package bo.edu.umss.mercado.importing;

import bo.edu.umss.mercado.survey.SurveyPublicationService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class XlsxImportService {
    public record Workbook(String id,String versionId,String fileName,String hash,String platform,List<XlsxFile.Sheet> sheets) {}
    private final JdbcTemplate jdbc; private final SurveyPublicationService publications; private final CsvImportService imports;
    public XlsxImportService(JdbcTemplate jdbc,SurveyPublicationService publications,CsvImportService imports) { this.jdbc=jdbc; this.publications=publications; this.imports=imports; }
    @Transactional
    public String upload(String versionId,String name,byte[] bytes,String platform,String actor) {
        XlsxFile.open(bytes);
        if(name==null || name.isBlank() || name.length()>200 || actor==null || actor.isBlank() || actor.length()>100 || !Set.of("GENERICO","GOOGLE_FORMS","MICROSOFT_FORMS").contains(platform)) throw new IllegalArgumentException("Nombre, plataforma o administrador inválido.");
        var version=publications.lockForCapture(versionId);
        if(!version.status().equals("PUBLICADA")) throw new ResponseStatusException(HttpStatus.GONE,"La versión está cerrada.");
        String hash=CsvFile.hash(bytes);
        var existing=jdbc.queryForList("SELECT id,platform FROM xlsx_import_workbook WHERE version_id=? AND source_hash=?",versionId,hash);
        if(!existing.isEmpty()) {
            if(!platform.equals(existing.getFirst().get("platform"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"El libro ya tiene otra plataforma declarada.");
            return (String)existing.getFirst().get("id");
        }
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO xlsx_import_workbook(id,version_id,file_name,source_base64,source_hash,platform,actor,created_at) VALUES(?,?,?,?,?,?,?,?)",id,versionId,name,Base64.getEncoder().encodeToString(bytes),hash,platform,actor,OffsetDateTime.now(ZoneOffset.UTC)); return id;
    }
    public Workbook get(String id) {
        var row=row(id); byte[] bytes=original(id);
        return new Workbook(id,(String)row.get("version_id"),(String)row.get("file_name"),(String)row.get("source_hash"),(String)row.get("platform"),XlsxFile.open(bytes).sheets());
    }
    public byte[] original(String id) {
        var row=row(id); byte[] bytes;
        try { bytes=Base64.getDecoder().decode((String)row.get("source_base64")); } catch(IllegalArgumentException e) { throw new IllegalStateException("Original Excel alterado."); }
        if(!CsvFile.hash(bytes).equals(row.get("source_hash"))) throw new IllegalStateException("Original Excel alterado."); return bytes;
    }
    @Transactional
    public String select(String id,int sheet,String actor) {
        var w=get(id); return imports.upload(w.versionId(),w.fileName(),original(id),"XLSX:"+sheet,w.platform(),actor);
    }
    public List<Workbook> recent() { return jdbc.queryForList("SELECT id FROM xlsx_import_workbook ORDER BY created_at DESC LIMIT 100",String.class).stream().map(this::get).toList(); }
    public List<Workbook> recentForVersion(String versionId) { return jdbc.queryForList("SELECT id FROM xlsx_import_workbook WHERE version_id = ? ORDER BY created_at DESC, id DESC LIMIT 100",String.class,versionId).stream().map(this::get).toList(); }
    private Map<String,Object> row(String id) {
        var rows=jdbc.queryForList("SELECT * FROM xlsx_import_workbook WHERE id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Libro no encontrado."); return rows.getFirst();
    }
}

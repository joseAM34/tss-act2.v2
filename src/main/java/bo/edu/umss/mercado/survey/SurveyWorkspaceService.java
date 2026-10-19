package bo.edu.umss.mercado.survey;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Lecturas por encuesta y versión. No reconstruye capturas ni cambia publicaciones. */
@Service
@Transactional(readOnly = true)
public class SurveyWorkspaceService {
    private final JdbcTemplate jdbc;
    private final SurveyCatalog catalog;
    public SurveyWorkspaceService(JdbcTemplate jdbc, SurveyCatalog catalog) { this.jdbc = jdbc; this.catalog = catalog; }
    public record Card(String slug, String title, String description, int versions, boolean draft, long responses, boolean legacy, boolean archived) {}
    public record Version(String id, String code, String title, String description, String status, boolean active, long responses) {
        public boolean draft() { return status.equals("BORRADOR"); }
        public boolean published() { return status.equals("PUBLICADA"); }
        public String label() { return switch (status) {
            case "BORRADOR" -> "Borrador"; case "PUBLICADA" -> "En recepción"; case "CERRADA" -> "Cerrada";
            case "CATALOGO" -> "Formulario actual"; default -> "Histórica";
        }; }
    }
    public record Workspace(String slug, boolean legacy, String title, List<Version> versions, Version selected) {
        public String path() { return "/admin/espacio/" + slug; }
        public String link(String section) { return path()+"/"+section+"?version="+java.net.URLEncoder.encode(selected.code(),java.nio.charset.StandardCharsets.UTF_8); }
        public String draftId() { return versions.stream().filter(Version::draft).map(Version::id).findFirst().orElse(""); }
    }
    public record Counts(long total, long real, long synthetic, long unverified) {}
    public record Link(String title, String date, String path) {}

    public List<Card> all() {
        return all(false);
    }
    public List<Card> all(boolean includeArchived) {
        var cards = new ArrayList<Card>();
        for (var s : catalog.all()) {
            // Conserva acceso al historial original sin imponer formularios vacíos en instalaciones nuevas.
            if (total(s.slug()) == 0) continue;
            var w = open(s.slug(), "");
            cards.add(new Card(s.slug(), s.title(), s.description(), w.versions().size(), false,
                    total(s.slug()), true, false));
        }
        for (var row : jdbc.queryForList("SELECT slug, archived FROM survey WHERE archived = FALSE OR ? = TRUE ORDER BY created_at DESC, id", includeArchived)) {
            String slug = (String) row.get("slug");
            var w = open(slug, "");
            cards.add(new Card(slug, w.title(), w.selected().description(),
                    (int) w.versions().stream().filter(v -> !v.draft()).count(), !w.draftId().isEmpty(), total(slug), false, (Boolean) row.get("archived")));
        }
        return cards;
    }
    private long total(String slug) { return jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE survey_slug = ?", Long.class, slug); }

    public Workspace open(String slug, String code) {
        var original = catalog.all().stream().filter(s -> s.slug().equals(slug)).findFirst();
        var versions = new ArrayList<Version>();
        boolean legacy = original.isPresent();
        if (legacy) {
            var current = original.get();
            versions.add(new Version("", current.version(), current.title(), current.description(), "CATALOGO", true, count(slug,current.version())));
            for (String historical : jdbc.queryForList("SELECT instrument_version FROM survey_response WHERE survey_slug = ? UNION SELECT instrument_version FROM instrument_snapshot WHERE survey_slug = ? ORDER BY instrument_version", String.class,slug,slug)) {
                if (!historical.equals(current.version())) versions.add(new Version("",historical,current.title(),"Definición histórica; no se sustituye por el catálogo actual.","HISTORICA",false,count(slug,historical)));
            }
        } else {
            versions.addAll(jdbc.query("""
                    SELECT v.*, s.active_version_id,
                        (SELECT COUNT(*) FROM survey_response r WHERE r.publication_version_id = v.id) AS response_count
                    FROM survey_version v JOIN survey s ON s.id = v.survey_id
                    WHERE s.slug = ? ORDER BY CASE WHEN v.id = s.active_version_id THEN 0 WHEN v.status <> 'BORRADOR' THEN 1 ELSE 2 END,
                        v.publication_number DESC, v.created_at DESC, v.id
                    """, (rs,n) -> new Version(rs.getString("id"),rs.getString("version_code"),rs.getString("title"),rs.getString("description"),
                    rs.getString("status"),rs.getString("id").equals(rs.getString("active_version_id")),rs.getLong("response_count")),slug));
        }
        if (versions.isEmpty()) throw missing();
        Version selected = code == null || code.isBlank() ? versions.getFirst() : versions.stream().filter(v -> v.code().equals(code)).findFirst().orElseThrow(SurveyWorkspaceService::missing);
        return new Workspace(slug,legacy,selected.title(),List.copyOf(versions),selected);
    }
    private long count(String slug,String code) { return jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE survey_slug = ? AND instrument_version = ?",Long.class,slug,code); }

    public Counts counts(Workspace w) {
        var rows = jdbc.queryForList("""
                SELECT COALESCE((SELECT c.origin FROM wrangling_origin_classification c
                    WHERE c.response_id = r.id ORDER BY c.revision_order DESC LIMIT 1), 'NO_DECLARADO') AS origin, COUNT(*) AS n
                FROM survey_response r WHERE r.survey_slug = ? AND r.instrument_version = ? GROUP BY origin
                """,w.slug(),w.selected().code());
        long real=0,synthetic=0,unknown=0;
        for(var row:rows) {
            long n=((Number)row.get("n")).longValue();
            switch(row.get("origin").toString()) {case "REAL" -> real=n;case "SINTETICO" -> synthetic=n;default -> unknown+=n;}
        }
        return new Counts(real+synthetic+unknown,real,synthetic,unknown);
    }

    /** Filtra en SQL antes de limitar; los trabajos de otras versiones no ocultan los de ésta. */
    public List<Link> preparations(Workspace w) {
        if(w.legacy() || w.selected().draft()) return List.of();
        return jdbc.query("SELECT id, created_at FROM survey_preparation_run WHERE version_id = ? ORDER BY created_at DESC, id DESC LIMIT 100",
                (rs,n) -> new Link("Conjunto preparado", date(rs.getObject(2,OffsetDateTime.class)),"/admin/preparacion/conjuntos/"+rs.getString(1)),w.selected().id());
    }
    public List<Link> analyses(Workspace w) {
        if(w.legacy() || w.selected().draft()) return List.of();
        return jdbc.query("SELECT a.id, a.created_at FROM survey_general_analysis a JOIN survey_preparation_run p ON p.id = a.preparation_id WHERE p.version_id = ? ORDER BY a.created_at DESC, a.id DESC LIMIT 100",
                (rs,n) -> new Link("Análisis guardado",date(rs.getObject(2,OffsetDateTime.class)),"/admin/analisis-general/"+rs.getString(1)),w.selected().id());
    }
    public List<Link> reports(Workspace w) {
        if(w.legacy() || w.selected().draft()) return List.of();
        return jdbc.query("SELECT r.id, r.created_at FROM generated_report r JOIN survey_general_analysis a ON a.id = r.analysis_id JOIN survey_preparation_run p ON p.id = a.preparation_id WHERE p.version_id = ? ORDER BY r.created_at DESC, r.id DESC LIMIT 100",
                (rs,n) -> new Link("Reporte guardado",date(rs.getObject(2,OffsetDateTime.class)),"/admin/reportes/"+rs.getString(1)),w.selected().id());
    }
    public List<Link> imports(Workspace w) {
        if(w.legacy() || w.selected().draft()) return List.of();
        return jdbc.query("SELECT id, file_name, created_at FROM csv_import_batch WHERE version_id = ? ORDER BY created_at DESC, id DESC LIMIT 100",
                (rs,n) -> new Link(rs.getString(2),date(rs.getObject(3,OffsetDateTime.class)),"/admin/importaciones/"+rs.getString(1)),w.selected().id());
    }
    public List<Link> workbooks(Workspace w) {
        if(w.legacy() || w.selected().draft()) return List.of();
        return jdbc.query("SELECT id, file_name, created_at FROM xlsx_import_workbook WHERE version_id = ? ORDER BY created_at DESC, id DESC LIMIT 100",
                (rs,n) -> new Link(rs.getString(2),date(rs.getObject(3,OffsetDateTime.class)),"/admin/importaciones/excel/"+rs.getString(1)),w.selected().id());
    }
    public Optional<Workspace> forVersion(String id) {
        var rows=jdbc.queryForList("SELECT s.slug, v.version_code FROM survey_version v JOIN survey s ON s.id = v.survey_id WHERE v.id = ?",id);
        return rows.isEmpty()?Optional.empty():Optional.of(open((String)rows.getFirst().get("slug"),(String)rows.getFirst().get("version_code")));
    }
    public Optional<Workspace> forResponse(String id) {
        var rows=jdbc.queryForList("SELECT survey_slug, instrument_version FROM survey_response WHERE id = ?",id);
        return rows.isEmpty()?Optional.empty():Optional.of(open((String)rows.getFirst().get("survey_slug"),(String)rows.getFirst().get("instrument_version")));
    }
    private static String date(OffsetDateTime value) { return value.atZoneSameInstant(ZoneId.of("America/La_Paz")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Encuesta o versión no encontrada en este espacio."); }
}

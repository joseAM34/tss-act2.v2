package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.wrangling.GenericPreparationService;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LiveResultsService {
    public record Selection(String origin,LocalDate from,LocalDate until,String row,String column) {
        public Selection {
            if(origin==null || !Set.of("REAL","SINTETICO","NO_DECLARADO").contains(origin))throw new IllegalArgumentException("Selecciona un origen válido.");
            if(from!=null && until!=null && from.isAfter(until))throw new IllegalArgumentException("La fecha inicial no puede superar la final.");
            row=row==null?"":row;column=column==null?"":column;
            if(row.isBlank()!=column.isBlank())throw new IllegalArgumentException("Selecciona las dos preguntas del cruce o deja ambas vacías.");
        }
    }
    public record Frame(String sourceHash,String resultHash,String crossHash,String hash,String cutoff,Map<String,Object> result,Map<String,Object> cross) {
        public String cutoffLabel() {return Instant.parse(cutoff).atZone(ZoneId.of("America/La_Paz")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));}
    }
    public record Saved(String preparationId,String analysisId,String crossId) {}
    private final GenericPreparationService preparation;
    private final GenericAnalysisService analyses;
    private final GenericCrossService crosses;
    private final SurveyPublicationService publications;
    private final JdbcTemplate jdbc;
    public LiveResultsService(GenericPreparationService preparation,GenericAnalysisService analyses,GenericCrossService crosses,SurveyPublicationService publications,JdbcTemplate jdbc) {
        this.preparation=preparation;this.analyses=analyses;this.crosses=crosses;this.publications=publications;this.jdbc=jdbc;
    }
    public Frame current(String versionId,Selection selection,String actor) {return frame(preparation.current(versionId,selection.origin(),selection.from(),selection.until(),actor),selection);}
    private GenericCrossService.Spec spec(Selection s) {return new GenericCrossService.Spec(s.row(),s.column(),.05,false,"","",false);}
    @SuppressWarnings("unchecked")
    private Frame frame(Map<String,Object> bundle,Selection selection) {
        Map<String,Object> logical=CanonicalJson.read(CanonicalJson.json(bundle.get("logical")),Map.class);
        var result=analyses.calculate(logical);
        Map<String,Object> cross=selection.row().isBlank()?null:crosses.calculate(logical,spec(selection));
        String source=(String)bundle.get("logical_hash"), resultHash=CanonicalJson.hash(result),crossHash=CanonicalJson.hash(cross);
        String hash=CanonicalJson.hash(Map.of("source",source,"result",resultHash,"cross",crossHash));
        return new Frame(source,resultHash,crossHash,hash,(String)bundle.get("cutoff_utc"),result,cross);
    }
    /** El bloqueo serializa guardados; la lectura interna usa su propio snapshot repetible. */
    @Transactional
    public Saved save(String versionId,Selection selection,String expectedHash,String expectedCutoff,String actor) {
        publications.lockForCapture(versionId);
        var bundle=preparation.atCutoff(versionId,selection.origin(),selection.from(),selection.until(),expectedCutoff,actor);
        var frame=frame(bundle,selection);
        if(!frame.hash().equals(expectedHash))throw new ResponseStatusException(HttpStatus.CONFLICT,"Los datos o la configuración cambiaron. Actualiza la vista y revisa el nuevo corte antes de guardarlo.");
        var existing=jdbc.queryForList("SELECT id FROM survey_preparation_run WHERE version_id = ? AND logical_hash = ? ORDER BY created_at, id",String.class,versionId,frame.sourceHash());
        String prepared=existing.stream().filter(id->frame.cutoff().equals(preparation.bundle(id).get("cutoff_utc"))).findFirst().orElseGet(()->preparation.persist(bundle));
        preparation.bundle(prepared);
        var previous=jdbc.queryForList("SELECT id FROM survey_general_analysis WHERE preparation_id = ? AND result_hash = ? ORDER BY created_at, id LIMIT 1",String.class,prepared,frame.resultHash());
        String analysis=previous.isEmpty()?analyses.create(prepared,actor):previous.getFirst();
        analyses.get(analysis);
        String crossId="";
        if(frame.cross()!=null) {
            var saved=jdbc.queryForList("SELECT id FROM survey_general_cross WHERE preparation_id = ? AND spec_hash = ? AND result_hash = ? ORDER BY created_at, id LIMIT 1",String.class,prepared,CanonicalJson.hash(spec(selection)),frame.crossHash());
            crossId=saved.isEmpty()?crosses.create(prepared,spec(selection),actor):saved.getFirst();crosses.get(crossId);
        }
        return new Saved(prepared,analysis,crossId);
    }
}

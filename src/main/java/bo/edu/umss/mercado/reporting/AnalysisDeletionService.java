package bo.edu.umss.mercado.reporting;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.survey.SurveyWorkspaceService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AnalysisDeletionService {
    private final JdbcTemplate jdbc;
    public AnalysisDeletionService(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public record State(String id,String createdAt,int reports,String hash) {}

    @Transactional(readOnly=true)
    public State state(SurveyWorkspaceService.Workspace workspace,String id){
        var row=analysis(workspace,id);
        var reports=jdbc.queryForList("SELECT id,content_hash FROM generated_report WHERE analysis_id=? ORDER BY id",id);
        return new State(id,row.get("created_at").toString(),reports.size(),CanonicalJson.hash(Map.of("analysis",row,"reports",reports)));
    }
    private Map<String,Object> analysis(SurveyWorkspaceService.Workspace workspace,String id){
        if(workspace.legacy()||workspace.selected().draft())throw missing();
        var rows=jdbc.queryForList("SELECT a.id,a.preparation_id,a.created_at,a.result_hash,a.source_hash FROM survey_general_analysis a JOIN survey_preparation_run p ON p.id=a.preparation_id WHERE a.id=? AND p.version_id=?",id,workspace.selected().id());
        if(rows.isEmpty())throw missing();
        // Explicit keys/types keep the confirmation fingerprint stable across JDBC drivers.
        var r=rows.getFirst();
        return Map.of("id",r.get("id"),"preparation_id",r.get("preparation_id"),"created_at",r.get("created_at").toString(),"result_hash",r.get("result_hash"),"source_hash",r.get("source_hash"));
    }
    @Transactional
    public void delete(SurveyWorkspaceService.Workspace workspace,String id,String expectedHash,boolean confirmed,String actor){
        if(!confirmed)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirma la eliminación del análisis y sus reportes asociados.");
        if(actor==null||actor.isBlank()||actor.length()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Administrador no válido.");
        var original=analysis(workspace,id);
        jdbc.queryForList("SELECT id FROM survey_preparation_run WHERE id=? FOR UPDATE",original.get("preparation_id"));
        jdbc.queryForList("SELECT id FROM survey_general_analysis WHERE id=? FOR UPDATE",id);
        jdbc.queryForList("SELECT id FROM generated_report WHERE analysis_id=? ORDER BY id FOR UPDATE",id);
        var state=state(workspace,id);
        if(!state.hash().equals(expectedHash))throw new ResponseStatusException(HttpStatus.CONFLICT,"Los reportes asociados cambiaron. Vuelve a abrir la confirmación antes de eliminar.");
        jdbc.update("DELETE FROM generated_report_cross WHERE report_id IN (SELECT id FROM generated_report WHERE analysis_id=?)",id);
        jdbc.update("DELETE FROM generated_report WHERE analysis_id=?",id);
        jdbc.update("DELETE FROM survey_general_analysis WHERE id=?",id);
        var event=Map.of("entity","ANALISIS_REPORTE","analysis",original,"reports_deleted",state.reports(),"responses_preserved",true,"preparation_preserved",true);
        jdbc.update("INSERT INTO survey_management_event (id,survey_id,survey_slug,operation,actor,created_at,state_json,state_hash) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID().toString(),
                jdbc.queryForObject("SELECT survey_id FROM survey_version WHERE id=?",String.class,workspace.selected().id()),workspace.slug(),"ELIMINAR",actor,OffsetDateTime.now(ZoneOffset.UTC),CanonicalJson.json(event),CanonicalJson.hash(event));
    }
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Análisis no encontrado en esta encuesta y versión.");}
}

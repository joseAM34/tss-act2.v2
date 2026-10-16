package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.survey.SurveyWorkspaceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Reconstruye contexto desde referencias persistidas, sin una selección global en sesión. */
@ControllerAdvice(basePackages="bo.edu.umss.mercado.web")
public class SurveyWorkspaceAdvice {
    private final SurveyWorkspaceService workspaces;
    private final JdbcTemplate jdbc;
    public SurveyWorkspaceAdvice(SurveyWorkspaceService workspaces,JdbcTemplate jdbc) { this.workspaces=workspaces;this.jdbc=jdbc; }
    @ModelAttribute
    void context(HttpServletRequest request,HttpServletResponse response,Model model) {
        String path=request.getRequestURI().substring(request.getContextPath().length());
        if(!path.startsWith("/admin/")) return;
        response.setHeader("Cache-Control","no-store");
        if(path.startsWith("/admin/espacio")) return;
        Optional<SurveyWorkspaceService.Workspace> context=Optional.empty();
        String section="resumen";
        String[] parts=path.split("/");
        String id=parts.length>3?parts[3]:"";
        if(path.startsWith("/admin/encuestas/")) {
            section="versiones";
            context=workspaces.forVersion(id.equals("versiones") && parts.length>4?parts[4]:id);
        } else if(path.startsWith("/admin/respuestas/")) {
            section="respuestas";context=workspaces.forResponse(id);
        } else if(path.startsWith("/admin/importaciones")) {
            section="importar";
            if(id.equals("excel") && parts.length>4) context=byQuery("SELECT version_id FROM xlsx_import_workbook WHERE id = ?",parts[4]);
            else if(!id.isEmpty()) context=byQuery("SELECT version_id FROM csv_import_batch WHERE id = ?",id);
            else context=workspaces.forVersion(parameter(request,"versionId"));
        } else if(path.startsWith("/admin/preparacion")) {
            section="resultados";
            if(id.equals("conjuntos") && parts.length>4) context=byQuery("SELECT version_id FROM survey_preparation_run WHERE id = ?",parts[4]);
            else context=workspaces.forVersion(id.isEmpty()?parameter(request,"versionId"):id);
        } else if(path.startsWith("/admin/analisis-general")) {
            section="resultados";
            if(!id.isEmpty()) context=byQuery("SELECT p.version_id FROM survey_general_analysis a JOIN survey_preparation_run p ON p.id = a.preparation_id WHERE a.id = ?",id);
            else context=byQuery("SELECT version_id FROM survey_preparation_run WHERE id = ?",parameter(request,"preparationId"));
        } else if(path.startsWith("/admin/cruces-generales")) {
            section="resultados";
            if(!id.isEmpty()) context=byQuery("SELECT p.version_id FROM survey_general_cross c JOIN survey_preparation_run p ON p.id = c.preparation_id WHERE c.id = ?",id);
            else context=byQuery("SELECT version_id FROM survey_preparation_run WHERE id = ?",parameter(request,"preparationId"));
        } else if(path.startsWith("/admin/reportes")) {
            section="reportes";
            if(!id.isEmpty() && !id.equals("nuevo") && !id.equals("plantillas")) context=byQuery("SELECT p.version_id FROM generated_report r JOIN survey_general_analysis a ON a.id = r.analysis_id JOIN survey_preparation_run p ON p.id = a.preparation_id WHERE r.id = ?",id);
            else if(!parameter(request,"analysisId").isEmpty()) context=byQuery("SELECT p.version_id FROM survey_general_analysis a JOIN survey_preparation_run p ON p.id = a.preparation_id WHERE a.id = ?",parameter(request,"analysisId"));
            else context=workspaces.forVersion(parameter(request,"workspaceVersionId"));
        }
        if(context.isPresent()) { model.addAttribute("workspace",context.get());model.addAttribute("workspaceSection",section); }
    }
    private Optional<SurveyWorkspaceService.Workspace> byQuery(String sql,String id) {
        if(id.isEmpty()) return Optional.empty();
        var ids=jdbc.queryForList(sql,String.class,id);
        return ids.isEmpty()?Optional.empty():workspaces.forVersion(ids.getFirst());
    }
    private String parameter(HttpServletRequest request,String name) {
        String[] values=request.getParameterValues(name);return values!=null && values.length==1?values[0]:"";
    }
}

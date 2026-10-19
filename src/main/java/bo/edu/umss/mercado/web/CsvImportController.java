package bo.edu.umss.mercado.web;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.importing.CsvImportService;
import bo.edu.umss.mercado.importing.XlsxImportService;
import bo.edu.umss.mercado.importing.ImportSuggestions;
import bo.edu.umss.mercado.importing.ImportCatalogService;
import bo.edu.umss.mercado.survey.SurveyPublicationService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Controller @RequestMapping("/admin/importaciones")
public class CsvImportController {
    private final CsvImportService service;
    private final SurveyPublicationService publications;
    private final XlsxImportService excel;
    private final ImportCatalogService catalog;
    public CsvImportController(CsvImportService service,SurveyPublicationService publications,XlsxImportService excel,ImportCatalogService catalog) { this.service=service; this.publications=publications; this.excel=excel;this.catalog=catalog; }
    @GetMapping
    String scopedIndex(@RequestParam(defaultValue="") String versionId,Model model,HttpServletResponse response) {
        if(!versionId.isEmpty()) {
            var version=publications.get(versionId);
            response.setHeader("Cache-Control","no-store");
            model.addAttribute("selectedVersion",versionId);model.addAttribute("versions",version.status().equals("PUBLICADA")?List.of(version):List.of());
            model.addAttribute("batches",service.recentForVersion(versionId));model.addAttribute("workbooks",excel.recentForVersion(versionId));return "csv-imports";
        }
        return index(model,response);
    }
    private String index(Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); model.addAttribute("batches",service.recent());
        model.addAttribute("workbooks",excel.recent());
        model.addAttribute("versions",publications.recent().stream().filter(v -> v.status().equals("PUBLICADA")).toList()); return "csv-imports";
    }
    @PostMapping
    String upload(@RequestParam String versionId,@RequestParam(defaultValue="AUTO") String delimiter,@RequestParam(defaultValue="GENERICO") String platform,@RequestParam MultipartFile file,
                  Principal actor,Model model,HttpServletResponse response) throws IOException {
        try {
            String name=file.getOriginalFilename()==null?"archivo.csv":file.getOriginalFilename();
            if(name.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
                String bookId=excel.upload(versionId,name,file.getBytes(),platform,actor.getName());
                var book=excel.get(bookId);
                if(book.sheets().size()==1)return "redirect:/admin/importaciones/"+excel.select(bookId,book.sheets().getFirst().index(),actor.getName());
                return "redirect:/admin/importaciones/excel/"+bookId;
            }
            if(name.toLowerCase(Locale.ROOT).endsWith(".xls")) throw new IllegalArgumentException("Usa Excel .xlsx; no se admite el formato antiguo .xls.");
            return "redirect:/admin/importaciones/"+service.upload(versionId,name,file.getBytes(),delimiter,platform,actor.getName());
        }
        catch(IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error",ex.getMessage()); return scopedIndex(versionId,model,response); }
    }
    @GetMapping("/excel/{id}")
    String workbook(@PathVariable String id,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); var book=excel.get(id); model.addAttribute("book",book); model.addAttribute("version",publications.get(book.versionId())); return "xlsx-import-sheets";
    }
    @PostMapping("/excel/{id}/hoja")
    String sheet(@PathVariable String id,@RequestParam int sheet,Principal actor,Model model,HttpServletResponse response) {
        try { return "redirect:/admin/importaciones/"+excel.select(id,sheet,actor.getName()); }
        catch(IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error",ex.getMessage()); return workbook(id,model,response); }
    }
    @GetMapping("/{id}")
    String batch(@PathVariable String id,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); var batch=service.batch(id); var version=publications.get(batch.versionId()); var revisions=service.revisions(id);
        model.addAttribute("batch",batch); model.addAttribute("version",version); model.addAttribute("revisions",revisions); model.addAttribute("expectedRevision",revisions.isEmpty()?0:revisions.getFirst().revision());
        var proposal=ImportSuggestions.propose(version.content().definition(),batch.table());
        model.addAttribute("proposal",proposal);
        if(!model.containsAttribute("mapping"))model.addAttribute("mapping",revisions.isEmpty()?proposal.mapping():revisions.getFirst().mapping());
        var shown=(CsvImportService.Mapping)model.getAttribute("mapping");
        model.addAttribute("mappingPending",shown.columns().stream().filter(c->c.index()<0).count());
        model.addAttribute("consentReference",reference(shown.consentEvidence(),CONSENT_ACK));
        boolean historical=CsvImportService.historical(shown);
        model.addAttribute("historicalReceipt",historical || Boolean.TRUE.equals(model.getAttribute("suggestHistoricalReceipt")));
        model.addAttribute("historicalReference",historical?reference(shown.consentEvidence(),CsvImportService.HISTORICAL_ACK):"");
        model.addAttribute("compatibilityReference",reference(shown.compatibilityEvidence(),COMPATIBILITY_ACK));
        model.addAttribute("reusableVersions",publications.publishedForSurvey(version.surveyId()).stream().filter(v->!v.id().equals(batch.versionId())).toList());
        model.addAttribute("imported",service.imported(id)); return "csv-import-batch";
    }
    @GetMapping("/{id}/recepcion-historica")
    String reviewHistorical(@PathVariable String id,Model model,HttpServletResponse response) {
        // Suggest a mode without changing mappings or accepting the review on the administrator's behalf.
        model.addAttribute("suggestHistoricalReceipt",true);
        return batch(id,model,response);
    }
    @PostMapping("/{id}/correspondencias")
    String map(@PathVariable String id,@RequestParam MultiValueMap<String,String> values,Principal actor,Model model,HttpServletResponse response) {
        try {
            var batch=service.batch(id); var version=publications.get(batch.versionId()); var columns=new ArrayList<CsvImportService.Column>();
            for(var q:version.content().definition().questions()) columns.add(new CsvImportService.Column(q.id(),Integer.parseInt(one(values,"column."+q.id()))));
            String compatibility=optional(values,"compatibilityEvidence"),consent=optional(values,"consentEvidence");
            String reviewMode=optional(values,"reviewMode");
            if(!reviewMode.isEmpty() && !reviewMode.equals("CHECKBOX_V1"))throw new IllegalArgumentException("Modo de revisión no admitido.");
            if(reviewMode.equals("CHECKBOX_V1")) {
                compatibility=acknowledgement(flag(values,"compatible"),compatibility,COMPATIBILITY_ACK);
                consent=acknowledgement(flag(values,"consentReviewed"),consent,CONSENT_ACK);
            }else{
                if(flag(values,"compatible") && compatibility.isBlank())compatibility=COMPATIBILITY_ACK;
                if(flag(values,"consentReviewed") && consent.isBlank())consent=CONSENT_ACK;
            }
            int consentColumn=Integer.parseInt(one(values,"consentColumn")); String consentValue=one(values,"consentValue").strip();
            String sourceMode=optional(values,"sourceMode");
            if(!sourceMode.isEmpty() && !Set.of("CONSENTIMIENTO","HISTORICA").contains(sourceMode)) throw new IllegalArgumentException("Modo de recepción no admitido.");
            if(sourceMode.equals("HISTORICA")) {
                if(!flag(values,"historicalReviewed")) throw new IllegalArgumentException("Marca la recepción histórica documentada. No se declara aceptación del participante.");
                consentColumn=-2; consentValue=CsvImportService.HISTORICAL_VALUE;
                consent=acknowledgement(true,optional(values,"historicalEvidence"),CsvImportService.HISTORICAL_ACK);
            } else if(consentColumn==-2) throw new IllegalArgumentException("Selecciona explícitamente el modo de recepción histórica o una columna real de consentimiento.");
            var mapping=new CsvImportService.Mapping(columns,consentColumn,consentValue,Integer.parseInt(one(values,"dateColumn")),Integer.parseInt(one(values,"externalIdColumn")),
                    one(values,"optionMode"),one(values,"multipleSeparator"),flag(values,"compatible"),compatibility,consent);
            model.addAttribute("mapping",mapping);
            String revision=service.mapColumns(id,Integer.parseInt(one(values,"expectedRevision")),mapping,actor.getName()); return "redirect:/admin/importaciones/"+id+"/vista-previa?revisionId="+revision;
        } catch(IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error",ex.getMessage()); return batch(id,model,response); }
    }
    @GetMapping("/{id}/vista-previa")
    String preview(@PathVariable String id,@RequestParam String revisionId,Model model,HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store"); var batch=service.batch(id);var version=publications.get(batch.versionId());var preview=service.preview(id,revisionId);
        model.addAttribute("batch",batch);model.addAttribute("preview",preview);
        model.addAttribute("errorCount",preview.rows().stream().filter(r->!r.errors().isEmpty()).count());
        var labels=new LinkedHashMap<String,String>();version.content().definition().questions().forEach(q->labels.put(q.id(),q.label()));
        labels.put("consent","Consentimiento");labels.put("_fecha","Fecha externa");labels.put("_id","Identificador externo");labels.put("_form","Formulario");
        var mapping=service.revision(id,revisionId).mapping();var indexes=new HashMap<String,Integer>();mapping.columns().forEach(c->indexes.put(c.questionId(),c.index()));
        model.addAttribute("historicalReceipt",CsvImportService.historical(mapping));
        model.addAttribute("consentReviewPending",!CsvImportService.historical(mapping) && (mapping.consentColumn()<0 || mapping.consentEvidence().isBlank()));
        indexes.put("consent",mapping.consentColumn());indexes.put("_fecha",mapping.dateColumn());indexes.put("_id",mapping.externalIdColumn());
        var problems=new ArrayList<Problem>();
        for(var row:preview.rows())for(var error:row.errors().entrySet()) {
            int index=indexes.getOrDefault(error.getKey(),-1);
            problems.add(new Problem(row.number(),labels.getOrDefault(error.getKey(),error.getKey()),index<0?"Sin columna":row.cells().get(index),error.getValue()));
        }
        model.addAttribute("catalogProposals",problems.isEmpty() || !mapping.multipleSeparator().equals("MIXTO_V1")?List.of():catalog.proposals(id,revisionId));
        model.addAttribute("problems",problems);return "csv-import-preview";
    }
    @GetMapping("/{id}/vista-previa.json")
    void json(@PathVariable String id,@RequestParam String revisionId,HttpServletResponse response) throws IOException {
        var preview=service.preview(id,revisionId); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control","no-store"); response.getWriter().write(CanonicalJson.json(preview));
    }
    @PostMapping("/{id}/confirmar")
    String confirm(@PathVariable String id,@RequestParam String revisionId,@RequestParam String previewHash,@RequestParam(defaultValue="false") boolean overlapReviewed,
                   Principal actor,Model model,HttpServletResponse response) {
        try { service.confirm(id,revisionId,previewHash,overlapReviewed,actor.getName()); return "redirect:/admin/importaciones/"+id; }
        catch(IllegalArgumentException ex) { response.setStatus(400); model.addAttribute("error",ex.getMessage()); return preview(id,revisionId,model,response); }
    }
    @GetMapping("/{id}/original.txt")
    void original(@PathVariable String id,HttpServletResponse response) throws IOException {
        byte[] bytes=service.original(id); boolean xlsx=CsvImportService.sheet(service.batch(id).delimiter())>0;
        response.setContentType(xlsx?"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":"text/plain;charset=UTF-8"); response.setHeader("Cache-Control","no-store");
        response.setHeader("Content-Disposition","attachment; filename=\"original-"+id.replaceAll("[^A-Za-z0-9-]","_")+(xlsx?".xlsx":".txt")+"\""); response.getOutputStream().write(bytes);
    }
    private String one(MultiValueMap<String,String> values,String key) { var v=values.get(key); if(v==null || v.size()!=1) throw new IllegalArgumentException("Se requiere un valor único para "+key+"."); return v.getFirst(); }
    private String optional(MultiValueMap<String,String> values,String key) { return values.containsKey(key)?one(values,key):""; }
    private boolean flag(MultiValueMap<String,String> values,String key) {
        if(!values.containsKey(key))return false;
        String value=one(values,key);if(!value.equals("true") && !value.equals("false"))throw new IllegalArgumentException("Confirmación no válida.");return value.equals("true");
    }
    public static final String COMPATIBILITY_ACK="CHECKBOX_COMPATIBILIDAD_V1: Revisé preguntas, opciones, unidades, obligatoriedad y condiciones de la fuente frente a esta versión. No implica aprobación institucional.";
    public static final String CONSENT_ACK="CHECKBOX_CONSENTIMIENTO_V1: Revisé que la columna seleccionada registra aceptación del uso académico en la fuente. Esta revisión no sustituye el consentimiento de los participantes.";
    public record Problem(int row,String question,String value,String message) {}
    private String acknowledgement(boolean checked,String ref,String acknowledgement) {return !checked?"":acknowledgement+(ref.isBlank()?"":"\nReferencia: "+ref);}
    private String reference(String evidence,String acknowledgement) {return evidence.equals(acknowledgement)?"":evidence.startsWith(acknowledgement+"\nReferencia: ")?evidence.substring(acknowledgement.length()+13):evidence;}
    @ExceptionHandler(ResponseStatusException.class)
    String problem(ResponseStatusException ex,Model model,HttpServletResponse response) { response.setStatus(ex.getStatusCode().value()); response.setHeader("Cache-Control","no-store"); model.addAttribute("message",ex.getReason()); model.addAttribute("back","/admin/importaciones"); return "survey-editor-error"; }
}

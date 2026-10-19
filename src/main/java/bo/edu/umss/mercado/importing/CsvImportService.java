package bo.edu.umss.mercado.importing;

import bo.edu.umss.mercado.data.*;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CsvImportService {
    public static final String HISTORICAL_VALUE="RECEPCION_HISTORICA_V1";
    public static final String HISTORICAL_ACK="RECEPCION_HISTORICA_DOCUMENTADA_V1: Recibí este archivo histórico para revisión y análisis académico. La fuente no registra una aceptación individual; esta recepción administrativa no afirma consentimiento ni aprobación institucional.";
    public static boolean historical(Mapping mapping) {
        return mapping.consentColumn()==-2 && HISTORICAL_VALUE.equals(mapping.consentValue())
                && mapping.consentEvidence()!=null && (mapping.consentEvidence().equals(HISTORICAL_ACK)
                || mapping.consentEvidence().startsWith(HISTORICAL_ACK+"\nReferencia: "));
    }
    public record Column(String questionId,int index) {}
    public record Mapping(List<Column> columns,int consentColumn,String consentValue,int dateColumn,int externalIdColumn,
                          String optionMode,String multipleSeparator,boolean compatible,String compatibilityEvidence,String consentEvidence) {
        public Mapping { columns=List.copyOf(columns); }
    }
    public record Batch(String id,String versionId,String fileName,String sourceHash,String delimiter,String platform,List<List<String>> table) {}
    public record Revision(String id,int revision,String hash,Mapping mapping) {}
    public record Row(int number,List<String> cells,ValidationResult validation,Map<String,String> errors,String externalDate,String externalId) {}
    public record Candidate(String batchId,String fileName,String versionId,String reason,int matchingRows) {}
    public record Preview(String batchId,String revisionId,String hash,List<Row> rows,List<String> issues,int overlaps,List<Candidate> candidates,boolean valid) {}
    public record Summary(String id,String fileName,String versionId,String createdAt) {}
    private final JdbcTemplate jdbc;
    private final SurveyPublicationService publications;
    private final SurveyResponseRepository responses;
    private final CaptureStore captures;
    private final SurveyValidator validator;
    public CsvImportService(JdbcTemplate jdbc,SurveyPublicationService publications,SurveyResponseRepository responses,CaptureStore captures,SurveyValidator validator) {
        this.jdbc=jdbc; this.publications=publications; this.responses=responses; this.captures=captures; this.validator=validator;
    }
    @Transactional
    public String upload(String versionId,String filename,byte[] bytes,String delimiter,String platform,String actor) {
        if("AUTO".equals(delimiter))delimiter=CsvFile.detectDelimiter(bytes);
        table(bytes,delimiter); require(Set.of("GENERICO","GOOGLE_FORMS","MICROSOFT_FORMS"),platform,"Plataforma no admitida.");
        actor=text(actor,100,true); filename=text(filename,200,true);
        var version=publications.lockForCapture(versionId);
        if(!version.status().equals("PUBLICADA")) throw new ResponseStatusException(HttpStatus.GONE,"La versión está cerrada.");
        String hash=CsvFile.hash(bytes);
        var existing=jdbc.queryForList("SELECT id, delimiter, platform FROM csv_import_batch WHERE version_id = ? AND source_hash = ? AND source_sheet = ?",versionId,hash,sheet(delimiter));
        if(!existing.isEmpty()) {
            var row=existing.getFirst();
            if(!delimiter.equals(row.get("delimiter")) || !platform.equals(row.get("platform"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"El original ya está registrado con otro separador o plataforma declarada. Abre ese lote.");
            return (String)row.get("id");
        }
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO csv_import_batch (id, version_id, file_name, source_base64, source_hash, delimiter, platform, actor, created_at, source_sheet) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,versionId,filename,Base64.getEncoder().encodeToString(bytes),hash,delimiter,platform,actor,OffsetDateTime.now(ZoneOffset.UTC),sheet(delimiter)); return id;
    }
    public static int sheet(String delimiter) {
        if(delimiter!=null && delimiter.matches("XLSX:[1-9][0-9]?")) return Integer.parseInt(delimiter.substring(5));
        return 0;
    }
    public static List<List<String>> table(byte[] bytes,String delimiter) {
        return sheet(delimiter)>0?XlsxFile.open(bytes).table(sheet(delimiter)):CsvFile.parse(bytes,delimiter);
    }
    public Batch batch(String id) {
        var rows=jdbc.queryForList("SELECT * FROM csv_import_batch WHERE id = ?",id);
        if(rows.isEmpty()) throw missing("Lote no encontrado."); var row=rows.getFirst();
        byte[] bytes=bytes(row); if(((Number)row.get("source_sheet")).intValue()!=sheet((String)row.get("delimiter"))) throw corrupt(); return new Batch(id,(String)row.get("version_id"),(String)row.get("file_name"),(String)row.get("source_hash"),
                (String)row.get("delimiter"),(String)row.get("platform"),table(bytes,(String)row.get("delimiter")));
    }
    public byte[] original(String id) {
        var rows=jdbc.queryForList("SELECT * FROM csv_import_batch WHERE id = ?",id); if(rows.isEmpty()) throw missing("Lote no encontrado."); return bytes(rows.getFirst());
    }
    private byte[] bytes(Map<String,Object> row) {
        byte[] bytes;
        try { bytes=Base64.getDecoder().decode((String)row.get("source_base64")); } catch(IllegalArgumentException ex) { throw corrupt(); }
        if(!CsvFile.hash(bytes).equals(row.get("source_hash"))) throw corrupt(); return bytes;
    }
    public Revision revision(String batchId,String id) {
        var rows=jdbc.queryForList("SELECT * FROM csv_import_mapping WHERE id = ? AND batch_id = ?",id,batchId);
        if(rows.isEmpty()) throw missing("Revisión de correspondencias no encontrada."); var row=rows.getFirst();
        Mapping m=CanonicalJson.read((String)row.get("mapping_json"),Mapping.class);
        if(!CanonicalJson.hash(m).equals(row.get("mapping_hash"))) throw corrupt();
        return new Revision(id,((Number)row.get("revision")).intValue(),(String)row.get("mapping_hash"),m);
    }
    public List<Revision> revisions(String batchId) {
        return jdbc.queryForList("SELECT id FROM csv_import_mapping WHERE batch_id = ? ORDER BY revision DESC",String.class,batchId).stream().map(id -> revision(batchId,id)).toList();
    }
    @Transactional
    public String mapColumns(String batchId,int expectedRevision,Mapping mapping,String actor) {
        var batch=batch(batchId); var version=publications.get(batch.versionId());
        jdbc.queryForMap("SELECT id FROM csv_import_batch WHERE id = ? FOR UPDATE",batchId);
        int current=jdbc.queryForObject("SELECT COALESCE(MAX(revision),0) FROM csv_import_mapping WHERE batch_id = ?",Integer.class,batchId);
        if(current!=expectedRevision) throw new ResponseStatusException(HttpStatus.CONFLICT,"Las correspondencias cambiaron. Abre nuevamente el lote.");
        validate(mapping,batch,version); actor=text(actor,100,true);
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO csv_import_mapping (id, batch_id, revision, actor, created_at, mapping_json, mapping_hash) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,batchId,current+1,actor,OffsetDateTime.now(ZoneOffset.UTC),CanonicalJson.json(mapping),CanonicalJson.hash(mapping)); return id;
    }
    private void validate(Mapping m,Batch b,SurveyPublicationService.Version v) {
        var questions=v.content().definition().questions();
        if(m.columns().size()!=questions.size()) throw invalid("Configura todas las preguntas de esta versión.");
        var used=new HashSet<Integer>(); int n=b.table().getFirst().size();
        for(int i=0;i<questions.size();i++) {
            Column c=m.columns().get(i); if(!questions.get(i).id().equals(c.questionId())) throw invalid("La correspondencia no pertenece a esta versión.");
            index(c.index(),n); if(c.index()>=0 && !used.add(c.index())) throw invalid("Una columna no puede asignarse a dos campos.");
        }
        if(m.consentColumn()==-2 && !historical(m)) throw invalid("La recepción histórica requiere su declaración administrativa explícita; no equivale a consentimiento.");
        if(!historical(m)) index(m.consentColumn(),n);
        index(m.dateColumn(),n); index(m.externalIdColumn(),n);
        for(int c:List.of(m.consentColumn(),m.dateColumn(),m.externalIdColumn())) if(c>=0 && !used.add(c)) throw invalid("Una columna no puede asignarse a dos campos.");
        text(m.consentValue(),80,true); text(m.compatibilityEvidence(),2000,false); text(m.consentEvidence(),2000,false);
        require(Set.of("CODIGOS","ETIQUETAS","AUTO"),m.optionMode(),"Representación de opciones no admitida.");
        if(m.multipleSeparator()==null || !Set.of("","|",";",",","AUTO","JSON","MIXTO_V1").contains(m.multipleSeparator())) throw invalid("Separador de selección múltiple no admitido.");
    }
    public Preview preview(String batchId,String revisionId) {
        var b=batch(batchId); var revision=revision(batchId,revisionId); var m=revision.mapping(); var version=publications.get(b.versionId()); validate(m,b,version);
        var issues=new ArrayList<String>();
        if(!m.compatible() || m.compatibilityEvidence().isBlank()) issues.add("Falta revisión documentada de preguntas, opciones, unidades, obligatoriedad y condiciones del instrumento externo.");
        boolean historical=historical(m);
        if(!historical && (m.consentColumn()<0 || m.consentEvidence().isBlank())) issues.add("Falta columna y evidencia de consentimiento académico en la fuente. El lote solo puede conservarse para revisión.");
        if(m.columns().stream().anyMatch(c -> c.index()<0)) issues.add("Todas las preguntas, incluidas las opcionales, requieren columna de origen; no se infieren estados de campos no recolectados.");
        var rows=new ArrayList<Row>(); var seenIds=new HashSet<String>(); var rowHashes=new HashSet<String>();
        var questions=version.content().definition().questions();
        for(int i=1;i<b.table().size();i++) {
            var cells=b.table().get(i); rowHashes.add(CanonicalJson.hash(cells)); var fields=new LinkedMultiValueMap<String,String>(); var errors=new LinkedHashMap<String,String>();
            String consent=cell(cells,m.consentColumn()).strip();
            if(!historical) fields.set("consent",consent.equals(m.consentValue())?"SI":"");
            for(int q=0;q<questions.size();q++) {
                var question=questions.get(q); String raw=cell(cells,m.columns().get(q).index()); if(raw.isBlank()) continue;
                // Las revisiones anteriores conservan su conversión y huellas exactamente.
                if(m.optionMode().equals("AUTO") || Set.of("AUTO","JSON","MIXTO_V1").contains(m.multipleSeparator())) {
                    try {fields.put(question.id(),question.options().isEmpty()?List.of(question.type().equals("number")?raw.strip():raw):ImportSuggestions.options(question,raw,m.optionMode(),m.multipleSeparator()));}
                    catch(IllegalArgumentException ex){errors.put(question.id(),ex.getMessage());}
                } else {
                    var values=question.type().equals("checkbox") && !m.multipleSeparator().isEmpty() ? Arrays.asList(raw.split(java.util.regex.Pattern.quote(m.multipleSeparator()),-1)) : List.of(raw);
                    var converted=new ArrayList<String>();
                    for(String value:values) {
                        if(!question.options().isEmpty()) {
                            value=value.strip();
                            if(m.optionMode().equals("ETIQUETAS")) {
                                String label=value;var options=question.options().stream().filter(o->o.label().equals(label)).toList();
                                if(options.size()!=1){errors.put(question.id(),"Etiqueta inexistente o ambigua; revisa la fuente y representación.");continue;}value=options.getFirst().value();
                            }
                        }else if(question.type().equals("number"))value=value.strip();
                        converted.add(value);
                    }
                    fields.put(question.id(),converted);
                }
            }
            var validation=historical?validator.validateHistorical(version.content().definition(),fields):validator.validate(version.content().definition(),fields); validation.errors().forEach(errors::putIfAbsent);
            for(int q=0;q<questions.size();q++) if(validation.states().get(questions.get(q).id())==AnswerState.NO_APLICA && !cell(cells,m.columns().get(q).index()).isBlank()) errors.put(questions.get(q).id(),"La fuente trae un valor aunque la condición declarada indica No aplica; no se descarta automáticamente.");
            String date=cell(cells,m.dateColumn());
            if(!date.isBlank()) try { OffsetDateTime.parse(date.strip()); } catch(java.time.format.DateTimeParseException ex) { errors.put("_fecha","La fecha externa requiere ISO 8601 con zona, o deja esa columna sin interpretar."); }
            String externalId=cell(cells,m.externalIdColumn()).strip();
            if(externalId.length()>200) errors.put("_id","Identificador externo demasiado largo.");
            if(!externalId.isBlank() && !seenIds.add(externalId)) errors.put("_id","Identificador externo repetido en el lote; resuelve la ambigüedad antes de importar.");
            rows.add(new Row(i+1,cells,validation,Map.copyOf(errors),date,externalId));
        }
        var matching=new HashMap<String,Integer>();
        for(var row:jdbc.queryForList("SELECT batch_id, raw_row_hash FROM csv_import_row WHERE batch_id <> ?",batchId)) if(rowHashes.contains(row.get("raw_row_hash"))) matching.merge((String)row.get("batch_id"),1,Integer::sum);
        var candidates=new ArrayList<Candidate>();
        for(var row:jdbc.queryForList("SELECT id, file_name, version_id, source_hash FROM csv_import_batch WHERE id <> ? ORDER BY id",batchId)) {
            boolean same=b.sourceHash().equals(row.get("source_hash")); int count=matching.getOrDefault(row.get("id"),0);
            if(same || count>0) candidates.add(new Candidate((String)row.get("id"),(String)row.get("file_name"),(String)row.get("version_id"),same?"MISMO_ORIGINAL_EXACTO":"FILAS_ORIGINALES_COINCIDENTES",count));
        }
        boolean valid=issues.isEmpty() && rows.stream().allMatch(r -> r.errors().isEmpty());
        String hash=CanonicalJson.hash(map("batch_id",batchId,"source_hash",b.sourceHash(),"source_selection",b.delimiter(),"publication_hash",version.hash(),"mapping_id",revisionId,"mapping_hash",revision.hash(),"rows",rows,"issues",issues,"overlap_candidates",candidates));
        return new Preview(batchId,revisionId,hash,List.copyOf(rows),List.copyOf(issues),candidates.size(),List.copyOf(candidates),valid);
    }
    @Transactional
    public List<String> confirm(String batchId,String revisionId,String previewHash,boolean overlapReviewed,String actor) {
        var b=batch(batchId); publications.lockForCapture(b.versionId()); jdbc.queryForMap("SELECT id FROM csv_import_batch WHERE id = ? FOR UPDATE",batchId);
        var confirmed=jdbc.queryForList("SELECT mapping_id, preview_hash FROM csv_import_confirmation WHERE batch_id = ?",batchId);
        if(!confirmed.isEmpty()) {
            var prior=confirmed.getFirst(); if(!revisionId.equals(prior.get("mapping_id"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Este lote ya se confirmó con otra revisión. Se conserva la vista previa nueva, pero su incorporación requiere reconciliar las respuestas existentes.");
            if(!previewHash.equals(prior.get("preview_hash")) && !previewHash.equals(preview(batchId,revisionId).hash())) throw new ResponseStatusException(HttpStatus.CONFLICT,"La vista previa no corresponde al lote confirmado.");
            List<String> ids=imported(batchId); ids.forEach(captures::input); return ids;
        }
        var preview=preview(batchId,revisionId);
        if(!preview.hash().equals(previewHash)) throw new ResponseStatusException(HttpStatus.CONFLICT,"La vista previa cambió. Revísala antes de confirmar.");
        if(!preview.valid()) throw invalid("Hay errores o evidencia pendiente; no se importó ninguna fila.");
        if(preview.overlaps()>0 && !overlapReviewed) throw invalid("Revisa los candidatos a solapamiento antes de confirmar. No se deduplican personas por coincidencias.");
        actor=text(actor,100,true); var mapping=revision(batchId,revisionId);
        for(Row row:preview.rows()) {
            String token=UUID.nameUUIDFromBytes(("csv:"+batchId+":"+row.number()).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            boolean historical=historical(mapping.mapping());
            String id=historical?responses.saveHistoricalImport(b.versionId(),token,row.validation()):responses.savePublished(b.versionId(),token,row.validation());
            String captureHash=jdbc.queryForObject("SELECT capture_hash FROM response_capture WHERE response_id=?",String.class,id);
            String rawHash=CanonicalJson.hash(row.cells());
            var evidence=map("batch_id",batchId,"mapping_id",revisionId,"row_number",row.number(),"response_id",id,"source_hash",b.sourceHash(),"mapping_hash",mapping.hash(),"raw_row_hash",rawHash,
                    "capture_hash",captureHash,"platform_declared",b.platform(),"external_date",row.externalDate(),"date_precision",mapping.mapping().dateColumn()<0?"SIN_COLUMNA_DECLARADA":datePrecision(row.externalDate()),
                    "external_id",row.externalId(),"external_id_status",mapping.mapping().externalIdColumn()<0?"SIN_COLUMNA_DECLARADA":row.externalId().isBlank()?"VACIO_EN_FUENTE":"PROPORCIONADO_EN_FUENTE",
                    "overlap_reviewed",overlapReviewed,"overlap_candidates",preview.candidates());
            if(historical) { evidence.put("receipt_mode",HISTORICAL_VALUE); evidence.put("consent_status","NO_REGISTRADO_EN_FUENTE"); evidence.put("receipt_evidence",mapping.mapping().consentEvidence()); }
            if(sheet(b.delimiter())>0) { evidence.put("source_format","XLSX"); evidence.put("source_sheet",sheet(b.delimiter())); evidence.put("source_sheet_name",XlsxFile.open(original(batchId)).sheets().get(sheet(b.delimiter())-1).name()); }
            jdbc.update("INSERT INTO csv_import_row (batch_id, row_number, mapping_id, response_id, raw_row_json, raw_row_hash, capture_hash, evidence_json, evidence_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    batchId,row.number(),revisionId,id,CanonicalJson.json(row.cells()),rawHash,captureHash,CanonicalJson.json(evidence),CanonicalJson.hash(evidence));
        }
        jdbc.update("INSERT INTO csv_import_confirmation (batch_id, mapping_id, preview_hash, overlap_reviewed, actor, created_at) VALUES (?, ?, ?, ?, ?, ?)",batchId,revisionId,previewHash,overlapReviewed,actor,OffsetDateTime.now(ZoneOffset.UTC));
        var ids=imported(batchId); ids.forEach(captures::input); return ids;
    }
    public List<String> imported(String batchId) { return jdbc.queryForList("SELECT response_id FROM csv_import_row WHERE batch_id = ? ORDER BY row_number",String.class,batchId); }
    public List<Summary> recent() { return jdbc.query("SELECT id, file_name, version_id, created_at FROM csv_import_batch ORDER BY created_at DESC, id DESC LIMIT 100",(rs,n)->new Summary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class).toInstant().toString())); }
    public List<Summary> recentForVersion(String versionId) { return jdbc.query("SELECT id, file_name, version_id, created_at FROM csv_import_batch WHERE version_id = ? ORDER BY created_at DESC, id DESC LIMIT 100",(rs,n)->new Summary(rs.getString(1),rs.getString(2),rs.getString(3),rs.getObject(4,OffsetDateTime.class).toInstant().toString()),versionId); }
    private String cell(List<String> cells,int index) { return index<0?"":cells.get(index); }
    private String datePrecision(String date) {
        if(date.isBlank()) return "NO_PROPORCIONADA";
        var match=java.util.regex.Pattern.compile("(?i)T\\d{2}:\\d{2}(?::(\\d{2})(?:\\.(\\d+))?)?").matcher(date);
        if(!match.find()) return "SIN_PRECISION_IDENTIFICADA";
        return match.group(1)==null?"MINUTO":match.group(2)==null?"SEGUNDO":"FRACCION_SEGUNDO_"+match.group(2).length();
    }
    private void index(int i,int n) { if(i < -1 || i>=n) throw invalid("Índice de columna no válido."); }
    private void require(Set<String> allowed,String value,String message) { if(value==null || !allowed.contains(value)) throw invalid(message); }
    private String text(String value,int max,boolean required) { if(value==null || value.length()>max || required && value.isBlank() || value.indexOf('\0')>=0) throw invalid("Texto requerido ausente o demasiado largo."); return value; }
    private IllegalArgumentException invalid(String m) { return new IllegalArgumentException(m); }
    private ResponseStatusException missing(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND,m); }
    private IllegalStateException corrupt() { return new IllegalStateException("El original o las correspondencias no coinciden con sus huellas."); }
    public static Map<String,Object> map(Object... pairs) { var m=new LinkedHashMap<String,Object>(); for(int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return m; }
}

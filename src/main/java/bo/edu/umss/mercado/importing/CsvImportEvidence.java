package bo.edu.umss.mercado.importing;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifica procedencia sin depender del controlador/importador y sin exponer identificadores externos. */
public final class CsvImportEvidence {
    private CsvImportEvidence() {}
    private static final ThreadLocal<ReadScope> READ_SCOPE=new ThreadLocal<>();
    private static final String JOIN=" FROM csv_import_row r JOIN csv_import_batch b ON b.id=r.batch_id JOIN csv_import_mapping m ON m.id=r.mapping_id AND m.batch_id=r.batch_id ";
    private static class ReadScope {
        final JdbcTemplate jdbc;final Map<String,Map<String,Object>> rows=new HashMap<>();final Map<String,Map<String,Object>> sources=new HashMap<>();
        final Map<String,byte[]> bytes=new HashMap<>();final Map<String,String> hashes=new HashMap<>();final Map<String,Parsed> parsed=new HashMap<>();final Map<String,Map<String,Object>> verified=new HashMap<>();
        ReadScope(JdbcTemplate jdbc){this.jdbc=jdbc;}
    }
    private record Parsed(List<List<String>> table,String sheetName) {}
    /** Cache limitado a una lectura transaccional; nunca sobrevive a la carga o a una actualización. */
    public static <T> T inVersionSnapshot(JdbcTemplate jdbc,String versionId,java.util.function.Supplier<T> action){
        if(!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("La lectura agrupada requiere una transacción.");
        var previous=READ_SCOPE.get();var scope=new ReadScope(jdbc);
        jdbc.queryForList("SELECT r.*,b.version_id,b.source_hash,b.platform,b.delimiter,b.source_sheet,m.mapping_json,m.mapping_hash"+JOIN+" WHERE b.version_id=?",versionId).forEach(row->scope.rows.put((String)row.get("response_id"),row));READ_SCOPE.set(scope);
        try{return action.get();}finally{if(previous==null)READ_SCOPE.remove();else READ_SCOPE.set(previous);}
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> verify(JdbcTemplate jdbc,String responseId,String versionId,String captureHash) {
        var scope=READ_SCOPE.get();if(scope!=null&&scope.jdbc!=jdbc)scope=null;
        String verifiedKey=responseId+"/"+versionId+"/"+captureHash;
        if(scope!=null&&scope.verified.containsKey(verifiedKey))return scope.verified.get(verifiedKey);
        var rows=scope!=null?(scope.rows.containsKey(responseId)?List.of(scope.rows.get(responseId)):List.<Map<String,Object>>of()):jdbc.queryForList("""
                SELECT r.*, b.version_id, b.source_hash, b.source_base64, b.platform, b.delimiter, b.source_sheet, m.mapping_json, m.mapping_hash
                FROM csv_import_row r JOIN csv_import_batch b ON b.id = r.batch_id JOIN csv_import_mapping m ON m.id = r.mapping_id AND m.batch_id = r.batch_id
                WHERE r.response_id = ?
                """,responseId);
        if(rows.isEmpty()) return Map.of(); var row=rows.getFirst();
        Map<String,Object> evidence=CanonicalJson.read((String)row.get("evidence_json"),Map.class);
        var mapping=CanonicalJson.read((String)row.get("mapping_json"),CsvImportService.Mapping.class);
        byte[] original;String sourceHash;
        if(scope!=null){String batch=(String)row.get("batch_id");var source=scope.sources.get(batch);if(source==null){source=jdbc.queryForMap("SELECT source_base64,source_hash,delimiter,source_sheet FROM csv_import_batch WHERE id=?",batch);scope.sources.put(batch,source);}
            if(!Objects.equals(source.get("source_hash"),row.get("source_hash"))||!Objects.equals(source.get("delimiter"),row.get("delimiter"))||!Objects.equals(source.get("source_sheet"),row.get("source_sheet")))throw corrupt();
            original=scope.bytes.get(batch);if(original==null){try{original=Base64.getDecoder().decode((String)source.get("source_base64"));}catch(IllegalArgumentException ex){throw corrupt();}scope.bytes.put(batch,original);scope.hashes.put(batch,CsvFile.hash(original));}sourceHash=scope.hashes.get(batch);
        }else{try { original=Base64.getDecoder().decode((String)row.get("source_base64")); } catch(IllegalArgumentException ex) { throw corrupt(); }sourceHash=CsvFile.hash(original);}
        Object raw=CanonicalJson.read((String)row.get("raw_row_json"),List.class);
        if(!Objects.equals(versionId,row.get("version_id")) || !captureHash.equals(row.get("capture_hash")) || !sourceHash.equals(row.get("source_hash"))
                || !CanonicalJson.hash(mapping).equals(row.get("mapping_hash")) || !CanonicalJson.hash(raw).equals(row.get("raw_row_hash")) || !CanonicalJson.hash(evidence).equals(row.get("evidence_hash"))
                || !responseId.equals(evidence.get("response_id")) || !captureHash.equals(evidence.get("capture_hash")) || !row.get("source_hash").equals(evidence.get("source_hash"))
                || !row.get("mapping_hash").equals(evidence.get("mapping_hash")) || !row.get("raw_row_hash").equals(evidence.get("raw_row_hash"))
                || !row.get("batch_id").equals(evidence.get("batch_id")) || !row.get("mapping_id").equals(evidence.get("mapping_id"))
                || ((Number)row.get("row_number")).intValue()!=((Number)evidence.get("row_number")).intValue() || !row.get("platform").equals(evidence.get("platform_declared"))) throw corrupt();
        int sheet=CsvImportService.sheet((String)row.get("delimiter"));
        if(((Number)row.get("source_sheet")).intValue()!=sheet) throw corrupt();
        boolean historical=CsvImportService.historical(mapping);
        if(historical) {
            if(!CsvImportService.HISTORICAL_VALUE.equals(evidence.get("receipt_mode"))
                    || !"NO_REGISTRADO_EN_FUENTE".equals(evidence.get("consent_status"))
                    || !mapping.consentEvidence().equals(evidence.get("receipt_evidence"))) throw corrupt();
        } else if(mapping.consentColumn()==-2 || evidence.containsKey("receipt_mode") || evidence.containsKey("consent_status") || evidence.containsKey("receipt_evidence")) throw corrupt();
        String sourceKey=row.get("batch_id").toString();Parsed parsed=scope==null?null:scope.parsed.get(sourceKey);
        if(parsed==null){if(sheet>0){var book=XlsxFile.open(original);parsed=new Parsed(book.table(sheet),book.sheets().get(sheet-1).name());}else parsed=new Parsed(CsvFile.parse(original,(String)row.get("delimiter")),"");if(scope!=null)scope.parsed.put(sourceKey,parsed);}
        if(sheet>0) {
            int number=((Number)row.get("row_number")).intValue(); var table=parsed.table();
            if(!"XLSX".equals(evidence.get("source_format")) || !Integer.valueOf(sheet).equals(evidence.get("source_sheet")) || !parsed.sheetName().equals(evidence.get("source_sheet_name")) || number<2 || number>table.size() || !table.get(number-1).equals(raw)) throw corrupt();
        }
        else {
            var table=parsed.table(); int number=((Number)row.get("row_number")).intValue();
            if(number<2 || number>table.size() || !table.get(number-1).equals(raw)) throw corrupt();
        }
        var result=CsvImportService.map("batch_id",row.get("batch_id"),"mapping_id",row.get("mapping_id"),"row_number",row.get("row_number"),"source_hash",row.get("source_hash"),
                "evidence_hash",row.get("evidence_hash"),"platform_declared",row.get("platform"),"external_date",evidence.get("external_date"),"date_precision",evidence.get("date_precision"));
        if(sheet>0) { result.put("source_format","XLSX"); result.put("source_sheet",sheet); result.put("source_sheet_name",evidence.get("source_sheet_name")); }
        if(historical) { result.put("receipt_mode",CsvImportService.HISTORICAL_VALUE); result.put("consent_status","NO_REGISTRADO_EN_FUENTE"); }
        if(scope!=null)scope.verified.put(verifiedKey,result);return result;
    }
    private static IllegalStateException corrupt() { return new IllegalStateException("La procedencia CSV no coincide con sus huellas o captura."); }
}

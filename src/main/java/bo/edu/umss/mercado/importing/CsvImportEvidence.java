package bo.edu.umss.mercado.importing;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Verifica procedencia sin depender del controlador/importador y sin exponer identificadores externos. */
public final class CsvImportEvidence {
    private CsvImportEvidence() {}
    @SuppressWarnings("unchecked")
    public static Map<String,Object> verify(JdbcTemplate jdbc,String responseId,String versionId,String captureHash) {
        var rows=jdbc.queryForList("""
                SELECT r.*, b.version_id, b.source_hash, b.source_base64, b.platform, b.delimiter, b.source_sheet, m.mapping_json, m.mapping_hash
                FROM csv_import_row r JOIN csv_import_batch b ON b.id = r.batch_id JOIN csv_import_mapping m ON m.id = r.mapping_id AND m.batch_id = r.batch_id
                WHERE r.response_id = ?
                """,responseId);
        if(rows.isEmpty()) return Map.of(); var row=rows.getFirst();
        Map<String,Object> evidence=CanonicalJson.read((String)row.get("evidence_json"),Map.class);
        var mapping=CanonicalJson.read((String)row.get("mapping_json"),CsvImportService.Mapping.class);
        byte[] original;
        try { original=Base64.getDecoder().decode((String)row.get("source_base64")); } catch(IllegalArgumentException ex) { throw corrupt(); }
        Object raw=CanonicalJson.read((String)row.get("raw_row_json"),List.class);
        if(!Objects.equals(versionId,row.get("version_id")) || !captureHash.equals(row.get("capture_hash")) || !CsvFile.hash(original).equals(row.get("source_hash"))
                || !CanonicalJson.hash(mapping).equals(row.get("mapping_hash")) || !CanonicalJson.hash(raw).equals(row.get("raw_row_hash")) || !CanonicalJson.hash(evidence).equals(row.get("evidence_hash"))
                || !responseId.equals(evidence.get("response_id")) || !captureHash.equals(evidence.get("capture_hash")) || !row.get("source_hash").equals(evidence.get("source_hash"))
                || !row.get("mapping_hash").equals(evidence.get("mapping_hash")) || !row.get("raw_row_hash").equals(evidence.get("raw_row_hash"))
                || !row.get("batch_id").equals(evidence.get("batch_id")) || !row.get("mapping_id").equals(evidence.get("mapping_id"))
                || ((Number)row.get("row_number")).intValue()!=((Number)evidence.get("row_number")).intValue() || !row.get("platform").equals(evidence.get("platform_declared"))) throw corrupt();
        int sheet=CsvImportService.sheet((String)row.get("delimiter"));
        if(((Number)row.get("source_sheet")).intValue()!=sheet) throw corrupt();
        if(sheet>0) {
            var book=XlsxFile.open(original); int number=((Number)row.get("row_number")).intValue(); var table=book.table(sheet);
            if(!"XLSX".equals(evidence.get("source_format")) || !Integer.valueOf(sheet).equals(evidence.get("source_sheet")) || !book.sheets().get(sheet-1).name().equals(evidence.get("source_sheet_name")) || number>table.size() || !table.get(number-1).equals(raw)) throw corrupt();
        }
        var result=CsvImportService.map("batch_id",row.get("batch_id"),"mapping_id",row.get("mapping_id"),"row_number",row.get("row_number"),"source_hash",row.get("source_hash"),
                "evidence_hash",row.get("evidence_hash"),"platform_declared",row.get("platform"),"external_date",evidence.get("external_date"),"date_precision",evidence.get("date_precision"));
        if(sheet>0) { result.put("source_format","XLSX"); result.put("source_sheet",sheet); result.put("source_sheet_name",evidence.get("source_sheet_name")); }
        return result;
    }
    private static IllegalStateException corrupt() { return new IllegalStateException("La procedencia CSV no coincide con sus huellas o captura."); }
}

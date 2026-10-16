package bo.edu.umss.mercado.reporting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Exports only verified, frozen aggregate results. Never reads current submissions. */
public final class ReportExport {
    private ReportExport() {}
    @SuppressWarnings("unchecked")
    private static Map<String,Object> map(Object o) { return (Map<String,Object>) o; }
    @SuppressWarnings("unchecked")
    private static List<Map<String,Object>> list(Object o) { return (List<Map<String,Object>>) o; }
    public static List<List<Object>> rows(Map<String,Object> report) {
        var rows = new ArrayList<List<Object>>();
        rows.add(List.of("seccion", "variable", "fila", "columna", "medida", "valor", "estado_celda"));
        var content=map(report.get("content")); var analysis=map(content.get("analysis"));
        var result=map(analysis.get("result")); var template=map(content.get("template"));
        for (String k:List.of("id","created_at","actor","logical_hash")) add(rows,"referencias","","","",k,report.get(k));
        for (String k:List.of("title","study_id","survey_id","version_id","instrument_version","origin","mode","protocol","scope","submission_count")) add(rows,"referencias","","","",k,result.get(k));
        for (String k:List.of("id","result_hash","source_hash")) add(rows,"analisis","","","",k,analysis.get(k));
        flatten(rows,"contexto","", "", "", "", content.get("context"));
        add(rows,"plantilla","","","","revision",content.get("template_revision_id"));
        add(rows,"plantilla","","","","titulo",template.get("title"));
        add(rows,"plantilla","","","","introduccion",template.get("introduction"));
        for (Object section:(List<?>)template.get("sections")) {
            if (section.equals("DESCRIPTIVOS")) for (var v:list(result.get("variables"))) {
                String q=v.get("label").toString();
                for (var e:v.entrySet()) if (!Set.of("numeric","distribution").contains(e.getKey())) add(rows,"descriptivos",q,"","",e.getKey(),e.getValue());
                if (v.get("numeric")!=null) flatten(rows,"descriptivos",q,"","","numeric",v.get("numeric"));
                if (v.get("distribution")!=null) for (var option:list(v.get("distribution")))
                    flatten(rows,"descriptivos",q,option.get("label").toString(),"","",option);
            }
            if (section.equals("CRUCES")) for (var cross:list(content.get("crosses"))) {
                var c=map(cross.get("result")); String q=c.get("row_label")+" × "+c.get("column_label");
                for (String k:List.of("id","result_hash","source_hash","spec_hash")) add(rows,"cruce",q,"","",k,cross.get(k));
                flatten(rows,"cruce",q,"","","especificacion",cross.get("spec"));
                for (String k:List.of("protocol","row_question","column_question","scope","total_submissions","excluded_pairs","multiplicity","pair_states")) flatten(rows,"cruce",q,"","",k,c.get(k));
                var table=map(c.get("table")); var rowOptions=list(c.get("row_options")); var colOptions=list(c.get("column_options"));
                Set<String> matrices=Set.of("observed","expected","row_percentages","column_percentages","total_percentages");
                for (var e:table.entrySet()) {
                    if (matrices.contains(e.getKey())) {
                        var matrix=(List<?>)e.getValue();
                        for(int r=0;r<rowOptions.size();r++) for(int col=0;col<colOptions.size();col++)
                            add(rows,"cruce",q,rowOptions.get(r).get("label").toString(),colOptions.get(col).get("label").toString(),e.getKey(),((List<?>)matrix.get(r)).get(col));
                    } else if (e.getKey().equals("row_totals") || e.getKey().equals("column_totals")) {
                        boolean byRow=e.getKey().equals("row_totals"); var options=byRow?rowOptions:colOptions;
                        for(int i=0;i<options.size();i++) add(rows,"cruce",q,byRow?options.get(i).get("label").toString():"",byRow?"":options.get(i).get("label").toString(),e.getKey(),((List<?>)e.getValue()).get(i));
                    } else flatten(rows,"cruce",q,"","",e.getKey(),e.getValue());
                }
            }
            if (section.equals("COMENTARIOS")) add(rows,"comentarios","","","","texto_administrativo",content.get("comment"));
        }
        return rows;
    }
    private static void flatten(List<List<Object>> rows,String section,String variable,String row,String column,String key,Object value) {
        if (value instanceof Map<?,?> m) m.forEach((k,v)->flatten(rows,section,variable,row,column,key.isEmpty()?k.toString():key+"."+k,v));
        else if (value instanceof List<?> l) { for(int i=0;i<l.size();i++) flatten(rows,section,variable,row,column,key+"["+i+"]",l.get(i)); }
        else add(rows,section,variable,row,column,key,value);
    }
    private static void add(List<List<Object>> rows,String section,String variable,String row,String column,String key,Object value) {
        rows.add(List.of(section,variable,row,column,key,value==null?"":value,value==null?"SIN_VALOR":"CON_VALOR"));
    }
    public static byte[] csv(List<List<Object>> rows) {
        var out=new StringBuilder("\uFEFF");
        for(var row:rows) {
            for(int i=0;i<row.size();i++) {
                if(i>0) out.append(','); Object value=row.get(i); String s=value.toString();
                // Spreadsheet formula injection protection applies to text, not numeric negatives.
                if (!(value instanceof Number) && s.stripLeading().matches("(?s)^[=+@\\-\\t\\r\\n].*")) s="'"+s;
                out.append('"').append(s.replace("\"","\"\"")).append('"');
            }
            out.append("\r\n");
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
    public static byte[] xlsx(List<List<Object>> rows) throws IOException {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
            part(zip,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/></Types>");
            part(zip,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
            part(zip,"xl/workbook.xml","<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Resultados\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            part(zip,"xl/_rels/workbook.xml.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/></Relationships>");
            var sheet=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
            int n=0;
            for(var row:rows) {
                sheet.append("<row r=\"").append(++n).append("\">"); int col=0;
                for(Object value:row) {
                    sheet.append("<c r=\"").append((char)('A'+col++)).append(n).append("\"");
                    if(value instanceof Number) sheet.append("><v>").append(value).append("</v></c>");
                    else sheet.append(" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(value.toString())).append("</t></is></c>");
                }
                sheet.append("</row>");
            }
            part(zip,"xl/worksheets/sheet1.xml",sheet.append("</sheetData></worksheet>").toString());
        }
        return bytes.toByteArray();
    }
    private static String xml(String text) {
        var safe=new StringBuilder();
        text.codePoints().filter(c->c==9||c==10||c==13||c>=32&&c<=0xD7FF||c>=0xE000&&c<=0xFFFD||c>=0x10000&&c<=0x10FFFF).forEach(safe::appendCodePoint);
        return safe.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    private static void part(ZipOutputStream zip,String name,String xml) throws IOException {
        var entry=new ZipEntry(name); entry.setTime(0); zip.putNextEntry(entry);
        zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+xml).getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
    }
}

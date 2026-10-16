package bo.edu.umss.mercado.importing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

public class XlsxFileTest {
    public static byte[] workbook(String... sheets) {
        var parts=new LinkedHashMap<String,String>();
        var workbook=new StringBuilder("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        var rels=new StringBuilder("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        var types=new StringBuilder("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for(int i=1;i<=sheets.length;i++) {
            workbook.append("<sheet name=\"Respuestas "+i+"\" sheetId=\""+i+"\" r:id=\"r"+i+"\"/>");
            rels.append("<Relationship Id=\"r"+i+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet"+i+".xml\"/>");
            types.append("<Override PartName=\"/xl/worksheets/sheet"+i+".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
            parts.put("xl/worksheets/sheet"+i+".xml",sheets[i-1]);
        }
        parts.put("xl/workbook.xml",workbook+"</sheets></workbook>"); parts.put("xl/_rels/workbook.xml.rels",rels+"</Relationships>");
        parts.put("[Content_Types].xml",types+"</Types>");
        parts.put("_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"r\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>");
        return zip(parts);
    }
    public static byte[] zip(Map<String,String> parts) {
        try { var out=new ByteArrayOutputStream(); try(var zip=new ZipOutputStream(out)) { for(var p:parts.entrySet()) { zip.putNextEntry(new ZipEntry(p.getKey())); zip.write(p.getValue().getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); } } return out.toByteArray(); }
        catch(IOException e) { throw new UncheckedIOException(e); }
    }
    public static String sheet(List<List<String>> rows) {
        var xml=new StringBuilder("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        for(int r=0;r<rows.size();r++) {
            xml.append("<row r=\""+(r+1)+"\">");
            for(int c=0;c<rows.get(r).size();c++) { String v=rows.get(r).get(c); if(!v.isEmpty()) xml.append("<c r=\""+column(c)+(r+1)+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+"</t></is></c>"); }
            xml.append("</row>");
        }
        return xml+"</sheetData></worksheet>";
    }
    private static String column(int c) { return c<26?""+(char)('A'+c):""+(char)('A'+c/26-1)+(char)('A'+c%26); }
    @Test void choosesSheetAndKeepsBlankCellPositionsAndUnicode() {
        var table=List.of(List.of("Edad","Dato","Dato"),List.of("0","","  Ñandú\nSí  "));
        var file=XlsxFile.open(workbook(sheet(List.of(List.of("otra"),List.of("1"))),sheet(table)));
        assertThat(file.sheets()).extracting(XlsxFile.Sheet::name).containsExactly("Respuestas 1","Respuestas 2");
        assertThat(file.table(2)).isEqualTo(table);
        assertThatThrownBy(()->file.table(0)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void readsNumericStoredValueWithoutInventingDisplayFormatOrDateZone() {
        String xml=sheet(List.of(List.of("a","b","c"),List.of("x","y","z"))).replace("<c r=\"A2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">x</t></is></c>","<c r=\"A2\" s=\"1\"><v>45200</v></c>");
        assertThat(XlsxFile.open(workbook(xml)).table(1).get(1)).containsExactly("45200","y","z");
    }
    @Test void rejectsFormulaEvenWithCachedValueAndMergedCells() {
        String xml=sheet(List.of(List.of("a"),List.of("x")));
        assertThatThrownBy(()->XlsxFile.open(workbook(xml.replace("<is><t xml:space=\"preserve\">x</t></is>","<f>1+1</f><v>2</v>"))).table(1)).hasMessageContaining("fórmulas");
        assertThatThrownBy(()->XlsxFile.open(workbook(xml.replace("</worksheet>","<mergeCells><mergeCell ref=\"A1:B1\"/></mergeCells></worksheet>"))).table(1)).hasMessageContaining("combinadas");
    }
    @Test void rejectsMalformedZipXmlAndExternalEntity() {
        assertThatThrownBy(()->XlsxFile.open("no es Excel".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->XlsxFile.open(workbook("<!DOCTYPE worksheet [<!ENTITY x SYSTEM 'file:///never-read'>]><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">&x;</worksheet>")).table(1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundsRowsColumnsAndExpandedBytes() {
        String xml=sheet(List.of(List.of("a"),List.of("x")));
        assertThatThrownBy(()->XlsxFile.open(workbook(xml.replace("r=\"2\"","r=\"502\""))).table(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->XlsxFile.open(workbook(xml.replace("A2","ZZ2"))).table(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->XlsxFile.open(zip(Map.of("bomb.xml","a".repeat(17*1024*1024))))).hasMessageContaining("descomprimido");
    }
    @Test void readsSharedRichTextAndKeepsLeadingZerosInText() {
        String xml=sheet(List.of(List.of("a"),List.of("001")));
        assertThat(XlsxFile.open(workbook(xml)).table(1).get(1).getFirst()).isEqualTo("001");
        var entries=new LinkedHashMap<String,String>();
        try(var z=new ZipInputStream(new ByteArrayInputStream(workbook(xml.replace("<c r=\"A2\" t=\"inlineStr\"><is><t xml:space=\"preserve\">001</t></is></c>","<c r=\"A2\" t=\"s\"><v>0</v></c>"))))) { ZipEntry e; while((e=z.getNextEntry())!=null) entries.put(e.getName(),new String(z.readAllBytes(),StandardCharsets.UTF_8)); } catch(IOException e) { throw new UncheckedIOException(e); }
        entries.put("xl/sharedStrings.xml","<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><si><r><t>Sí</t></r><r><t> ahora</t></r></si></sst>");
        assertThat(XlsxFile.open(zip(entries)).table(1).get(1).getFirst()).isEqualTo("Sí ahora");
    }
}

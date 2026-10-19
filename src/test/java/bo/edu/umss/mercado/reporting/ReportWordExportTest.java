package bo.edu.umss.mercado.reporting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ReportWordExportTest {
    static Map<String,Object> m(Object... pairs){var out=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)out.put(pairs[i].toString(),pairs[i+1]);return out;}
    static Map<String,byte[]> unzip(byte[] bytes)throws IOException{var out=new LinkedHashMap<String,byte[]>();try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){for(ZipEntry e;(e=zip.getNextEntry())!=null;)out.put(e.getName(),zip.readAllBytes());}return out;}
    static String xml(Map<String,byte[]> parts,String name){return new String(parts.get(name),StandardCharsets.UTF_8);}
    static void parseAll(Map<String,byte[]> parts)throws Exception{var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);for(var p:parts.entrySet()){if(p.getKey().endsWith(".xml")||p.getKey().endsWith(".rels"))factory.newDocumentBuilder().parse(new ByteArrayInputStream(p.getValue()));if(p.getKey().endsWith(".xlsx"))parseAll(unzip(p.getValue()));}}
    @Test void nativeChartsHaveEditableWorkbooksAndNoImages()throws Exception{
        var doc=new WordDocument();doc.heading("Prueba sintética < & >",1);doc.table(List.of(List.of("Respuesta"),List.of("Texto <script>\nsegunda línea")));
        var series=List.of(new WordDocument.Series("Envíos",Arrays.asList(3,null,5)));
        for(String kind:List.of("columns","bars","pie","donut","line","area","histogram","grouped","stacked","radar"))doc.chart("Gráfico "+kind,kind,List.of("A","B","C"),series);
        doc.chart("Dispersión","scatter",List.of(1,2,3),series);
        // More than 26 series must still yield valid spreadsheet cell references.
        var many=new ArrayList<WordDocument.Series>();for(int i=0;i<30;i++)many.add(new WordDocument.Series("Serie "+i,List.of(i)));doc.chart("Muchas series","grouped",List.of("A"),many);
        byte[] bytes=doc.finish();var parts=unzip(bytes);parseAll(parts);
        assertThat(parts.keySet()).noneMatch(k->k.contains("media/")||k.endsWith(".png"));
        assertThat(xml(parts,"word/document.xml")).contains("<w:tbl>","&lt;script&gt;","<w:br/>","<c:chart r:id=\"chart1\"");
        assertThat(xml(parts,"word/charts/chart4.xml")).contains("doughnutChart","holeSize");
        assertThat(xml(parts,"word/charts/chart1.xml")).contains("'Resultados'!$B$1","'Resultados'!$B$2:$B$4");
        String cache=xml(parts,"word/charts/chart5.xml").split("<c:numCache>")[1].split("</c:numCache>")[0];
        assertThat(cache).contains("<c:ptCount val=\"3\"/>").doesNotContain("<c:pt idx=\"1\">");
        assertThat(xml(parts,"word/charts/chart11.xml")).contains("<c:xVal>","<c:yVal>","scatterChart");
        assertThat(xml(parts,"word/charts/chart12.xml")).contains("$AE$2");
        var workbook=unzip(parts.get("word/embeddings/chart12.xlsx"));assertThat(xml(workbook,"xl/worksheets/sheet1.xml")).contains("r=\"AE2\"");
        Files.createDirectories(Path.of(".codex-tmp/word-export"));Files.write(Path.of(".codex-tmp/word-export/native-charts.docx"),bytes);
    }
    @Test void respectsFrozenSelectionsAndKeepsCompoundTextCountsAndDiagnostics()throws Exception{
        var option=m("label","A & B","count",2,"respondent_base",3,"respondent_percent",66.67,"mention_base",2,"mention_percent",100);
        var text=m("label","Opinión","total",3,"answered",3,"missing",0,"not_applicable",0,"applicable_base",3,"response_percent",100,"distribution",List.of(option),"report_graphs",List.of("text-bars","text-pie","text-donut"),"report_text_content",true,"text_content",List.of("IA <y> inteligencia artificial"),"text_counts",m("base",3,"dictionary_scope","PRIVATE","dictionary_revision",7,"distribution",List.of(m("label","inteligencia artificial","count",2,"base",3))));
        var unselected=m("label","No exportar originales","distribution",List.of(option),"text_content",List.of("SECRETO_NO_SELECCIONADO"),"text_counts",m("distribution",List.of(m("label","TEMA_NO_SELECCIONADO","count",5))),"report_graphs",List.of());
        var raw=m("variables",List.of(m("label","PREGUNTA_EXCLUIDA")),"submission_count",999);
        var shown=m("variables",List.of(text,unselected),"submission_count",3,"origin","NO_DECLARADO");
        var cross=m("result",m("row_label","Fila","column_label","Columna","row_options",List.of(m("label","R")),"column_options",List.of(m("label","C")),"table",m("n",2,"observed",List.of(List.of(2)),"row_percentages",List.of(List.of(100)),"column_percentages",List.of(List.of(100)),"total_percentages",List.of(List.of(100)),"expected",List.of(List.of(2)),"status","NO_APLICABLE","reason","Datos insuficientes")),"report_graphs",List.of("heat","grouped"));
        var content=m("template",m("title","Reporte de prueba","introduction","Prueba","charts",true,"sections",List.of("CONTEXTO","DESCRIPTIVOS","CRUCES","COMENTARIOS")),"analysis",m("result",raw),"display_result",shown,"context",m("cutoff_utc","2026-10-19T08:00:00Z"),"crosses",List.of(cross),"comment","Comentario conservado");
        var bytes=ReportWordExport.docx(m("id","ficticio","content",content));var parts=unzip(bytes);parseAll(parts);String document=xml(parts,"word/document.xml");
        assertThat(document).contains("IA &lt;y&gt; inteligencia artificial","Revisión: 7","2026-10-19T08:00:00Z","Mapa de calor: tabla coloreada editable","Prueba no ejecutada","Comentario conservado").doesNotContain("SECRETO_NO_SELECCIONADO","TEMA_NO_SELECCIONADO","PREGUNTA_EXCLUIDA");
        assertThat(xml(parts,"word/charts/chart1.xml")).contains("inteligencia artificial","<c:v>2</c:v>");
        Files.createDirectories(Path.of(".codex-tmp/word-export"));Files.write(Path.of(".codex-tmp/word-export/report-synthetic.docx"),bytes);
    }
}

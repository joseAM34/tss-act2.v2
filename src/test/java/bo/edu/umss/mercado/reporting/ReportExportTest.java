package bo.edu.umss.mercado.reporting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReportExportTest {
    List<List<Object>> values() { return List.of(List.of("texto","numero","ausencia"), List.of("=1+1",-2,""), List.of("  @SUM(A1)",0,"SIN_VALOR"), List.of("á < & \"\n🙂",2.5,"CON_VALOR")); }
    @Test void csvKeepsUtf8QuotesNewlinesAndNumbersWhileNeutralizingFormulaText() {
        String csv=new String(ReportExport.csv(values()),StandardCharsets.UTF_8);
        assertThat(csv).startsWith("\uFEFF").contains("\"'=1+1\",\"-2\"", "\"'  @SUM(A1)\"", "á < & \"\"\n🙂", "\"0\"", "SIN_VALOR");
    }
    @Test void excelIsValidXmlWithNumericCellsAndLiteralFormulaLikeText() throws Exception {
        var entries=new HashMap<String,byte[]>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(ReportExport.xlsx(values())))) {
            ZipEntry entry; while((entry=zip.getNextEntry())!=null) entries.put(entry.getName(),zip.readAllBytes());
        }
        var factory=DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        for(byte[] xml:entries.values()) factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        String sheet=new String(entries.get("xl/worksheets/sheet1.xml"),StandardCharsets.UTF_8);
        assertThat(sheet).contains("t=\"inlineStr\"", "=1+1", "<v>-2</v>", "<v>0</v>", "<v>2.5</v>", "&lt;", "&amp;", "🙂").doesNotContain("<f>");
        assertThat(entries).containsKeys("[Content_Types].xml","_rels/.rels","xl/workbook.xml","xl/_rels/workbook.xml.rels");
    }
}

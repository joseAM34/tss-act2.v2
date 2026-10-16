package bo.edu.umss.mercado.importing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CsvFileTest {
    byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
    @Test void preservesDuplicateHeadersQuotesEmbeddedNewlinesAndFormulaText() {
        var rows=CsvFile.parse(bytes("\uFEFFa,a,b\r\n\"dos, palabras\",\"línea1\r\nlínea2 \"\"citada\"\"\",=1+1\r\n"),"COMA");
        assertThat(rows.getFirst()).containsExactly("a","a","b"); assertThat(rows.get(1)).containsExactly("dos, palabras","línea1\r\nlínea2 \"citada\"","=1+1");
    }
    @Test void readsExplicitSeparatorsAndTrailingEmptyCells() {
        assertThat(CsvFile.parse(bytes("a;b;c\n1;;"),"PUNTO_COMA")).isEqualTo(List.of(List.of("a","b","c"),List.of("1","","")));
        assertThat(CsvFile.parse(bytes("a\tb\n1\t2"),"TAB").get(1)).containsExactly("1","2");
    }
    @Test void rejectsBadEncodingRaggedRowsUnclosedQuotesAndNulls() {
        assertThatThrownBy(()->CsvFile.parse(new byte[]{(byte)0xff},"COMA")).isInstanceOf(IllegalArgumentException.class);
        for(String s:List.of("a,b\n1","a\n\"sin cierre","a\n\"x\"z","a\nnu\0lo","solo encabezado")) assertThatThrownBy(()->CsvFile.parse(bytes(s),"COMA")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void boundsFileRowsColumnsAndCellLengths() {
        assertThatThrownBy(()->CsvFile.parse(new byte[CsvFile.MAX_BYTES+1],"COMA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->CsvFile.parse(bytes("a\n"+"x\n".repeat(501)),"COMA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->CsvFile.parse(bytes("a\n"+"x".repeat(8001)),"COMA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->CsvFile.parse(bytes("h,".repeat(200)+"h\n"+"1,".repeat(200)+"1"),"COMA")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void originalHashUsesExactBytes() {
        assertThat(CsvFile.hash(bytes("abc"))).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(CsvFile.hash(bytes("a\n"))).isNotEqualTo(CsvFile.hash(bytes("a\r\n")));
    }
}

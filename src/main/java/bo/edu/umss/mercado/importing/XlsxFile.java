package bo.edu.umss.mercado.importing;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.zip.*;
import javax.xml.stream.*;

/** Lectura acotada de OOXML. No ejecuta fórmulas, relaciones externas ni macros. */
public final class XlsxFile {
    private static final int MAX_EXPANDED = 16 * 1024 * 1024;
    private static final String MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    public record Sheet(int index, String name, String path) {}
    private final Map<String,byte[]> parts;
    private final List<Sheet> sheets;
    private XlsxFile(Map<String,byte[]> parts,List<Sheet> sheets) { this.parts=parts; this.sheets=List.copyOf(sheets); }
    public List<Sheet> sheets() { return sheets; }
    public static XlsxFile open(byte[] bytes) {
        if(bytes.length==0 || bytes.length>CsvFile.MAX_BYTES) throw bad("El Excel debe tener como máximo 1 MiB.");
        var parts=new HashMap<String,byte[]>(); int total=0;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                if(parts.size()>=300 || entry.isDirectory()) { if(entry.isDirectory()) continue; throw bad("Demasiadas partes en el Excel."); }
                String name=entry.getName();
                if(name.startsWith("/") || name.contains("..") || name.contains("\\") || parts.containsKey(name)) throw bad("Paquete Excel inválido.");
                var out=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int n;
                while((n=zip.read(buffer))!=-1) { total+=n; if(total>MAX_EXPANDED) throw bad("El Excel descomprimido supera 16 MiB."); out.write(buffer,0,n); }
                parts.put(name,out.toByteArray());
            }
        } catch(IOException ex) { throw bad("No se pudo leer el archivo XLSX."); }
        if(parts.containsKey("xl/vbaProject.bin")) throw bad("No se admiten libros con macros.");
        var relations=new HashMap<String,String>();
        read(parts,"xl/_rels/workbook.xml.rels",r -> {
            if(r.isStartElement() && r.getLocalName().equals("Relationship")) {
                String type=attr(r,"Type");
                if(type.endsWith("/worksheet")) {
                    if("External".equals(attr(r,"TargetMode"))) throw bad("No se admiten hojas externas.");
                    String path;
                    try { var uri=URI.create("xl/").resolve(attr(r,"Target")).normalize(); if(uri.isAbsolute()) throw bad("Relación externa inválida."); path=uri.getPath(); }
                    catch(IllegalArgumentException ex) { throw bad("Ruta de hoja inválida."); }
                    if(path.startsWith("/")) path=path.substring(1);
                    if(!path.startsWith("xl/worksheets/") || !parts.containsKey(path)) throw bad("Hoja Excel no encontrada.");
                    if(relations.put(attr(r,"Id"),path)!=null) throw bad("Relación repetida.");
                }
            }
        });
        var sheets=new ArrayList<Sheet>();
        read(parts,"xl/workbook.xml",r -> {
            if(start(r,"sheet")) {
                String name=attr(r,"name"),path=relations.get(r.getAttributeValue(REL,"id"));
                if(path==null || name.isBlank() || sheets.size()>=30) throw bad("Libro sin hojas compatibles o con más de 30 hojas.");
                sheets.add(new Sheet(sheets.size()+1,name,path));
            }
        });
        if(sheets.isEmpty()) throw bad("El archivo no contiene hojas XLSX compatibles.");
        return new XlsxFile(parts,sheets);
    }
    public List<List<String>> table(int sheetIndex) {
        if(sheetIndex<1 || sheetIndex>sheets.size()) throw bad("Selecciona una hoja válida.");
        var strings=new ArrayList<String>(); var text=new StringBuilder(); boolean[] inItem={false};
        if(parts.containsKey("xl/sharedStrings.xml")) read(parts,"xl/sharedStrings.xml",r -> {
            if(start(r,"si")) { inItem[0]=true; text.setLength(0); }
            if(start(r,"t") && inItem[0]) append(text,r.getElementText());
            if(end(r,"si")) { strings.add(text.toString()); inItem[0]=false; if(strings.size()>100_000) throw bad("Demasiados textos compartidos."); }
        });
        var rows=new TreeMap<Integer,Map<Integer,String>>(); int[] row={0},col={-1}; String[] type={""}; var value=new StringBuilder(); boolean[] cell={false};
        read(parts,sheets.get(sheetIndex-1).path(),r -> {
            if(start(r,"mergeCell")) throw bad("La hoja contiene celdas combinadas; usa una tabla sin combinaciones.");
            if(start(r,"row")) {
                row[0]=number(attr(r,"r"));
                if(row[0]<1 || row[0]>CsvFile.MAX_ROWS+1 || rows.put(row[0],new TreeMap<>())!=null) throw bad("La hoja supera 500 filas de datos o contiene filas inválidas.");
            }
            if(start(r,"c")) {
                if(row[0]==0) throw bad("Celda fuera de una fila.");
                String ref=attr(r,"r"); col[0]=column(ref,row[0]); type[0]=attr(r,"t"); value.setLength(0); cell[0]=true;
                if(!Set.of("","n","s","inlineStr","str","b","d","e").contains(type[0])) throw bad("Tipo de celda no admitido.");
            }
            if(start(r,"f")) throw bad("La hoja contiene fórmulas. Exporta o pega sus resultados como valores antes de importar.");
            if(cell[0] && (start(r,"v") || start(r,"t"))) append(value,r.getElementText());
            if(end(r,"c")) {
                String result=value.toString();
                if(type[0].equals("e")) throw bad("La hoja contiene una celda con error.");
                if(!result.isEmpty() && (type[0].isEmpty() || type[0].equals("n"))) {
                    try {
                        var number=new java.math.BigDecimal(result).stripTrailingZeros();
                        if(Math.abs((long)number.scale())>1000 || number.precision()>1000) throw bad("Número Excel fuera de los límites admitidos.");
                        result=number.toPlainString();
                    } catch(NumberFormatException ex) { throw bad("Número Excel inválido."); }
                }
                if(type[0].equals("s")) { int index=number(result); if(index<0 || index>=strings.size()) throw bad("Texto compartido inválido."); result=strings.get(index); }
                if(type[0].equals("b")) { if(!Set.of("0","1").contains(result)) throw bad("Booleano inválido."); result=result.equals("1")?"TRUE":"FALSE"; }
                if(rows.get(row[0]).put(col[0],result)!=null) throw bad("Celda repetida."); cell[0]=false;
            }
        });
        var header=rows.get(1);
        if(header==null || header.isEmpty()) throw bad("La primera fila debe contener encabezados.");
        int width=Collections.max(header.keySet())+1;
        if(header.values().stream().allMatch(String::isBlank)) throw bad("La primera fila debe contener encabezados.");
        var table=new ArrayList<List<String>>(); int last=rows.lastKey();
        for(int i=1;i<=last;i++) {
            var values=rows.getOrDefault(i,Map.of());
            if(values.keySet().stream().anyMatch(c -> c>=width)) throw bad("Hay celdas fuera de las columnas del encabezado.");
            var line=new ArrayList<String>(); for(int c=0;c<width;c++) line.add(values.getOrDefault(c,"")); table.add(List.copyOf(line));
        }
        if(table.size()<2) throw bad("La hoja no contiene filas de datos.");
        return List.copyOf(table);
    }
    private static int column(String ref,int row) {
        if(!ref.matches("[A-Z]{1,3}[1-9][0-9]*")) throw bad("Referencia de celda inválida.");
        int n=0,i=0; while(i<ref.length() && Character.isLetter(ref.charAt(i))) n=n*26+ref.charAt(i++)-'A'+1;
        if(number(ref.substring(i))!=row || n>CsvFile.MAX_COLUMNS) throw bad("La hoja supera 200 columnas o contiene referencias inválidas."); return n-1;
    }
    private static int number(String s) { try { return Integer.parseInt(s); } catch(NumberFormatException ex) { throw bad("Índice de celda inválido."); } }
    private static void append(StringBuilder b,String s) { if(b.length()+s.length()>CsvFile.MAX_CELL) throw bad("Una celda supera 8000 caracteres."); b.append(s); }
    private static String attr(XMLStreamReader r,String name) { String v=r.getAttributeValue(null,name); return v==null?"":v; }
    private static boolean start(XMLStreamReader r,String name) { return r.isStartElement() && MAIN.equals(r.getNamespaceURI()) && name.equals(r.getLocalName()); }
    private static boolean end(XMLStreamReader r,String name) { return r.isEndElement() && MAIN.equals(r.getNamespaceURI()) && name.equals(r.getLocalName()); }
    @FunctionalInterface private interface Reader { void accept(XMLStreamReader r) throws XMLStreamException; }
    private static void read(Map<String,byte[]> parts,String path,Reader action) {
        if(!parts.containsKey(path)) throw bad("Falta una parte requerida del XLSX.");
        var factory=XMLInputFactory.newFactory(); factory.setProperty(XMLInputFactory.SUPPORT_DTD,false); factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
        factory.setXMLResolver((a,b,c,d) -> { throw new XMLStreamException("Relación externa rechazada"); });
        try { var r=factory.createXMLStreamReader(new ByteArrayInputStream(parts.get(path))); try {
            while(r.hasNext()) { r.next(); if(r.getEventType()==XMLStreamConstants.DTD || r.getEventType()==XMLStreamConstants.ENTITY_REFERENCE) throw bad("No se admiten entidades XML."); action.accept(r); }
        } finally { r.close(); } } catch(XMLStreamException ex) { throw bad("XML de Excel inválido."); }
    }
    private static IllegalArgumentException bad(String message) { return new IllegalArgumentException(message); }
}

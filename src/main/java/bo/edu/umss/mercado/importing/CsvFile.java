package bo.edu.umss.mercado.importing;

import java.nio.*;
import java.nio.charset.*;
import java.security.*;
import java.util.*;

/** Lectura UTF-8 acotada; no evalúa fórmulas ni adivina encabezados o separadores. */
public final class CsvFile {
    public static final int MAX_BYTES=1_048_576, MAX_ROWS=500, MAX_COLUMNS=200, MAX_CELL=8000;
    private CsvFile() {}
    public static String detectDelimiter(byte[] bytes) {
        var candidates=new LinkedHashMap<String,List<List<String>>>();
        for(String delimiter:List.of("COMA","PUNTO_COMA","TAB"))try{candidates.put(delimiter,parse(bytes,delimiter));}catch(IllegalArgumentException ignored){}
        if(candidates.isEmpty())throw invalid("No se pudo leer el CSV. Comprueba UTF-8, comillas y filas; o elige su separador manualmente.");
        boolean multiple=candidates.values().stream().anyMatch(t->t.getFirst().size()>1);
        var best=candidates.entrySet().stream().filter(e->!multiple || e.getValue().getFirst().size()>1).toList();
        if(best.stream().map(Map.Entry::getValue).distinct().count()>1)throw invalid("El separador del CSV es ambiguo. Elige coma, punto y coma o tabulación en las opciones del archivo.");
        return best.getFirst().getKey();
    }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    public static List<List<String>> parse(byte[] bytes,String delimiter) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid("El CSV debe tener entre 1 byte y 1 MiB.");
        char separator=switch(delimiter) { case "COMA" -> ','; case "PUNTO_COMA" -> ';'; case "TAB" -> '\t'; default -> throw invalid("Separador no admitido."); };
        String source;
        try { source=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch(CharacterCodingException ex) { throw invalid("El archivo debe estar codificado como UTF-8."); }
        if (source.startsWith("\uFEFF")) source=source.substring(1);
        if (source.indexOf('\0') >= 0) throw invalid("El CSV contiene caracteres nulos.");
        var rows=new ArrayList<List<String>>(); var row=new ArrayList<String>(); var cell=new StringBuilder();
        boolean quoted=false,closed=false,started=false;
        for(int i=0;i<source.length();i++) {
            char c=source.charAt(i); started=true;
            if(quoted) {
                if(c=='"') { if(i+1<source.length() && source.charAt(i+1)=='"') { cell.append('"'); i++; } else { quoted=false; closed=true; } }
                else cell.append(c);
            } else if(c==separator || c=='\n' || c=='\r') {
                row.add(cell.toString()); cell.setLength(0); closed=false;
                if(row.size()>MAX_COLUMNS) throw invalid("Máximo 200 columnas.");
                if(c!=separator) {
                    if(c=='\r' && i+1<source.length() && source.charAt(i+1)=='\n') i++;
                    add(rows,row); row=new ArrayList<>(); started=false;
                }
            } else if(c=='"') {
                if(closed || cell.length()!=0) throw invalid("Comillas mal ubicadas en el CSV."); quoted=true;
            } else { if(closed) throw invalid("Hay texto después de cerrar una celda entre comillas."); cell.append(c); }
            if(cell.length()>MAX_CELL) throw invalid("Máximo 8000 caracteres por celda.");
        }
        if(quoted) throw invalid("Hay una celda entre comillas sin cerrar.");
        if(started || !row.isEmpty() || !cell.isEmpty()) { row.add(cell.toString()); add(rows,row); }
        if(rows.size()<2) throw invalid("Se requiere encabezado y al menos una fila de datos.");
        int columns=rows.getFirst().size();
        if(columns>MAX_COLUMNS) throw invalid("Máximo 200 columnas.");
        if(rows.stream().anyMatch(r -> r.size()!=columns)) throw invalid("Todas las filas deben tener la misma cantidad de columnas.");
        return List.copyOf(rows);
    }
    private static void add(List<List<String>> rows,List<String> row) { if(row.size()>MAX_COLUMNS || rows.size()>MAX_ROWS) throw invalid("Máximo 500 filas de datos y 200 columnas."); rows.add(List.copyOf(row)); }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}

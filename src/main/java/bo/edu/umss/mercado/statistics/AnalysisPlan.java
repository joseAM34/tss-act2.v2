package bo.edu.umss.mercado.statistics;

import java.util.ArrayList;
import java.util.List;

/** Plan registrado antes de calcular: no representa una aprobación institucional. */
public record AnalysisPlan(List<String> contrasts, String independenceUnit, String independenceEvidence,
                           boolean structuralZeros, double alpha) {
    public static final String PROTOCOL_VERSION = "PROTOCOLO_ESTADISTICO_V1";
    public static final List<String> CONTRASTS;
    static {
        var ids = new ArrayList<>(List.of("B01", "B02", "B03", "B04", "B05"));
        for (int i = 1; i <= 10; i++) ids.add("B06_%02d".formatted(i));
        ids.addAll(List.of("B07", "B08"));
        CONTRASTS = List.copyOf(ids);
    }
    public AnalysisPlan {
        if (contrasts == null || contrasts.stream().anyMatch(id -> !CONTRASTS.contains(id)))
            throw new IllegalArgumentException("Seleccione únicamente cruces del protocolo registrado.");
        contrasts = CONTRASTS.stream().filter(contrasts::contains).toList();
        // La familia de criterios se registra completa, incluso si alguna prueba no resulta aplicable.
        if (contrasts.stream().anyMatch(id -> id.startsWith("B06_")) &&
                contrasts.stream().filter(id -> id.startsWith("B06_")).count() != 10)
            throw new IllegalArgumentException("Los diez criterios constituyen una familia completa de contrastes.");
        independenceUnit = independenceUnit == null ? "" : independenceUnit.strip();
        independenceEvidence = independenceEvidence == null ? "" : independenceEvidence.strip();
        if (independenceUnit.length() > 200 || independenceEvidence.length() > 2000)
            throw new IllegalArgumentException("La documentación de independencia supera el tamaño permitido.");
        if (independenceUnit.isEmpty() != independenceEvidence.isEmpty())
            throw new IllegalArgumentException("Documente tanto la unidad de análisis como la evidencia de independencia.");
        if (!Double.isFinite(alpha) || alpha <= 0 || alpha >= 1)
            throw new IllegalArgumentException("El nivel alfa debe estar entre cero y uno.");
    }
    public static AnalysisPlan defaults() { return new AnalysisPlan(CONTRASTS, "", "", false, 0.05); }
    public boolean independenceDocumented() { return !independenceUnit.isBlank() && !independenceEvidence.isBlank(); }
}

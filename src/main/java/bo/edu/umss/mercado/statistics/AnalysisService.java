package bo.edu.umss.mercado.statistics;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.wrangling.WranglingService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Calcula únicamente desde un conjunto verificado y conserva la salida exacta. */
@Service
public class AnalysisService {
    private final JdbcTemplate jdbc;
    private final WranglingService wrangling;
    private final StatisticalEngine engine;
    public AnalysisService(JdbcTemplate jdbc, WranglingService wrangling, StatisticalEngine engine) {
        this.jdbc = jdbc; this.wrangling = wrangling; this.engine = engine;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public String create(String sourceRunId, AnalysisPlan plan, String actor) {
        if (plan == null || actor == null || actor.isBlank() || actor.length() > 100)
            throw new IllegalArgumentException("Se requiere un plan y un administrador identificado.");
        Map<String, Object> source = CanonicalJson.read(wrangling.bundle(sourceRunId), Map.class);
        Map<String, Object> sourceManifest = (Map<String, Object>) source.get("manifest");
        Map<String, Object> result = engine.analyze(source, plan);
        if (!AnalysisPlan.PROTOCOL_VERSION.equals(result.get("protocol_version")))
            throw new IllegalStateException("La salida no corresponde al protocolo registrado.");
        String id = UUID.randomUUID().toString();
        Instant created = Instant.now().truncatedTo(ChronoUnit.MICROS);
        String sourceHash = (String) sourceManifest.get("logical_hash");
        var protocol = map("version", AnalysisPlan.PROTOCOL_VERSION, "status", "BORRADOR", "quantiles", "TIPO_7",
                "variance_divisor", "N_MENOS_1", "chi_square", "PEARSON_INDEPENDENCIA_SIN_CORRECCION_CONTINUIDAD",
                "expected_policy", "TODAS_ESPERADAS_GE_5", "inference_library", "Apache Commons Statistics Inference 1.3",
                "stratification", List.of("survey_slug", "instrument_version", "study_id"),
                "general_families", "UNA_FAMILIA_EXPLORATORIA_POR_CRUCE_Y_ESTRATO",
                "criteria_family", "DIEZ_CRITERIOS_POR_ESTRATO_M_10_HOLM", "registered_contrasts", AnalysisPlan.CONTRASTS,
                "missing_values", "SIN_IMPUTACION", "ordinal_scores", "MAPAS_EXPLICITOS_COMPLEMENTARIOS_EN_CADA_RESULTADO");
        var logical = map("protocol_version", AnalysisPlan.PROTOCOL_VERSION, "source_logical_hash", sourceHash,
                "protocol_definition", protocol, "plan", plan, "result", result);
        String hash = CanonicalJson.hash(logical);
        var manifest = map("analysis_id", id, "source_run_id", sourceRunId, "source_logical_hash", sourceHash,
                "source_bundle_hash", CanonicalJson.hash(source), "source_origin", sourceManifest.get("origin"),
                "source_mode", sourceManifest.get("mode"), "source_cutoff_utc", sourceManifest.get("cutoff_utc"),
                "protocol_version", AnalysisPlan.PROTOCOL_VERSION, "status", "BORRADOR", "created_at", created.toString(),
                "protocol_definition", protocol, "protocol_hash", CanonicalJson.hash(protocol),
                "actor", actor, "logical_hash", hash, "canonical_format", "CANONICAL_JSON_SHA256_V1",
                "independence_documented", plan.independenceDocumented(),
                "approval", "La documentación administrativa no sustituye aprobación metodológica ni institucional.",
                "unit", "Una fila de origen es un envío. La independencia requiere documentación específica.",
                "expected_frequency_rule", "Todas las celdas activas deben tener frecuencia esperada de al menos 5.",
                "scope", "Resultados descriptivos y contrastes exploratorios del conjunto seleccionado; sin inferencia poblacional automática.");
        var bundle = map("manifest", manifest, "plan", plan, "result", result);
        jdbc.update("""
                INSERT INTO statistical_analysis (id, source_run_id, source_logical_hash, protocol_version,
                  created_at, actor, logical_hash, bundle_hash, bundle_json) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, sourceRunId, sourceHash, AnalysisPlan.PROTOCOL_VERSION, OffsetDateTime.ofInstant(created, ZoneOffset.UTC),
                actor, hash, CanonicalJson.hash(bundle), CanonicalJson.json(bundle));
        return id;
    }

    public List<AnalysisSummary> recent() {
        return jdbc.query("SELECT * FROM statistical_analysis ORDER BY created_at DESC, id DESC LIMIT 100", (rs, i) ->
                new AnalysisSummary(rs.getString("id"), rs.getString("source_run_id"), rs.getString("protocol_version"),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant().toString(), rs.getString("logical_hash")));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> detail(String id) { return CanonicalJson.read(bundle(id), Map.class); }

    @SuppressWarnings("unchecked")
    public String bundle(String id) {
        var records = jdbc.queryForList("SELECT * FROM statistical_analysis WHERE id = ?", id);
        if (records.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Análisis no encontrado.");
        var stored = records.getFirst();
        String json = (String) stored.get("bundle_json");
        Map<String, Object> data = CanonicalJson.read(json, Map.class);
        if (!(data.get("manifest") instanceof Map<?, ?>) || !CanonicalJson.hash(data).equals(stored.get("bundle_hash")))
            throw new IllegalStateException("El análisis no coincide con sus huellas; no puede descargarse.");
        Map<String, Object> manifest = (Map<String, Object>) data.get("manifest");
        var logical = map("protocol_version", manifest.get("protocol_version"), "source_logical_hash", manifest.get("source_logical_hash"),
                "protocol_definition", manifest.get("protocol_definition"), "plan", data.get("plan"), "result", data.get("result"));
        if (!id.equals(manifest.get("analysis_id")) || !stored.get("source_run_id").equals(manifest.get("source_run_id"))
                || !stored.get("source_logical_hash").equals(manifest.get("source_logical_hash"))
                || !stored.get("protocol_version").equals(manifest.get("protocol_version"))
                || !CanonicalJson.hash(manifest.get("protocol_definition")).equals(manifest.get("protocol_hash"))
                || !CanonicalJson.hash(logical).equals(stored.get("logical_hash"))
                || !stored.get("logical_hash").equals(manifest.get("logical_hash")))
            throw new IllegalStateException("El análisis no coincide con sus huellas; no puede descargarse.");
        return json;
    }

    private static Map<String, Object> map(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) result.put((String) entries[i], entries[i + 1]);
        return result;
    }
}

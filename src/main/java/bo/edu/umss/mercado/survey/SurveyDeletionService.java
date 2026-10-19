package bo.edu.umss.mercado.survey;

import bo.edu.umss.mercado.data.CanonicalJson;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Respaldo revisable y eliminación acotada de una encuesta, sin borrar recursos compartidos. */
@Service
public class SurveyDeletionService {
    private static final int MAX_ARCHIVE_BYTES = 64 * 1024 * 1024;
    private static final Duration VALIDITY = Duration.ofHours(1);
    private static final String VERSIONS = "SELECT id FROM survey_version WHERE survey_id = ?";
    private static final String RESPONSES = "SELECT id FROM survey_response WHERE dynamic_survey_id = ?";
    private static final String PREPARATIONS = "SELECT id FROM survey_preparation_run WHERE version_id IN (" + VERSIONS + ")";
    private static final String ANALYSES = "SELECT id FROM survey_general_analysis WHERE preparation_id IN (" + PREPARATIONS + ")";
    private static final String CROSSES = "SELECT id FROM survey_general_cross WHERE preparation_id IN (" + PREPARATIONS + ")";
    private static final String REPORTS = "SELECT id FROM generated_report WHERE analysis_id IN (" + ANALYSES + ")";
    private static final String BATCHES = "SELECT id FROM csv_import_batch WHERE version_id IN (" + VERSIONS + ")";
    private final JdbcTemplate jdbc;
    private final SurveyManagementService management;

    public SurveyDeletionService(JdbcTemplate jdbc, SurveyManagementService management) {
        this.jdbc = jdbc;
        this.management = management;
    }

    public record Receipt(String id, String stateHash, String filename, String createdAt) {}
    public record Backup(Receipt receipt, byte[] bytes, String hash) {}
    private record Snapshot(SurveyManagementService.State state, Map<String, List<Map<String, Object>>> tables,
                            Map<String, Object> content, String hash) {}

    @Transactional
    public Backup prepare(String slug, String expectedHash, String actor) {
        actor(actor);
        Snapshot snapshot = lockedSnapshot(slug, expectedHash);
        OffsetDateTime now = now();
        String id = UUID.randomUUID().toString();
        String filename = "respaldo-encuesta-" + snapshot.state().slug().replaceAll("[^a-zA-Z0-9_-]", "_") + "-" + id + ".zip";
        byte[] bytes = archive(snapshot, now);
        String hash = hashBytes(bytes);
        jdbc.update("""
                INSERT INTO survey_deletion_backup
                    (id, survey_id, slug, actor, state_hash, content_hash, archive_hash, filename, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, snapshot.state().id(), slug, actor, snapshot.state().hash(), snapshot.hash(), hash, filename, now);
        return new Backup(new Receipt(id, snapshot.state().hash(), filename, now.toString()), bytes, hash);
    }

    @Transactional(readOnly = true)
    public Optional<Receipt> latest(String slug, String actor, String stateHash) {
        actor(actor);
        if (stateHash == null || !stateHash.matches("[a-f0-9]{64}")) return Optional.empty();
        var rows = jdbc.queryForList("""
                SELECT id, state_hash, filename, created_at FROM survey_deletion_backup
                WHERE slug = ? AND actor = ? AND state_hash = ? AND consumed_at IS NULL AND created_at >= ?
                ORDER BY created_at DESC, id DESC LIMIT 1
                """, slug, actor, stateHash, now().minus(VALIDITY));
        return rows.isEmpty() ? Optional.empty() : Optional.of(receipt(rows.getFirst()));
    }

    @Transactional
    public void delete(String slug, String backupId, String expectedHash, String actor) {
        actor(actor);
        Snapshot snapshot = lockedSnapshot(slug, expectedHash);
        var backups = jdbc.queryForList("SELECT * FROM survey_deletion_backup WHERE id = ? FOR UPDATE", backupId);
        if (backups.isEmpty()) throw conflict("Descarga primero un respaldo actualizado de esta encuesta.");
        var backup = backups.getFirst();
        OffsetDateTime created = date(backup.get("created_at"));
        OffsetDateTime now = now();
        if (!actor.equals(backup.get("actor")) || !slug.equals(backup.get("slug"))
                || !snapshot.state().id().equals(backup.get("survey_id")) || backup.get("consumed_at") != null
                || created.isBefore(now.minus(VALIDITY)) || created.isAfter(now)
                || !snapshot.state().hash().equals(backup.get("state_hash")))
            throw conflict("El respaldo no corresponde a esta revisión o ya caducó. Descarga otro antes de eliminar.");
        if (!snapshot.hash().equals(backup.get("content_hash")))
            throw conflict("Los datos cambiaron después del respaldo. Descarga uno actualizado antes de eliminar.");

        // Hasta aquí únicamente se han leído/bloqueado datos. Todo lo siguiente se confirma en la misma transacción.
        String id = snapshot.state().id();
        var audit = map("backup_id", backupId, "content_hash", snapshot.hash(), "archive_hash", backup.get("archive_hash"),
                "counts", counts(snapshot.tables()), "preserved_shared", snapshot.content().get("preserved_shared"));
        jdbc.update("INSERT INTO survey_management_event VALUES (?, ?, ?, 'ELIMINAR', ?, ?, ?, ?)",
                UUID.randomUUID().toString(), id, slug, actor, now, CanonicalJson.json(audit), snapshot.state().hash());
        deleteOwned(id, slug);
        jdbc.update("UPDATE survey_deletion_backup SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL", now, backupId);
    }

    private Snapshot lockedSnapshot(String slug, String expectedHash) {
        var roots = jdbc.queryForList("SELECT id FROM survey WHERE slug = ? FOR UPDATE", String.class, slug);
        if (roots.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Encuesta administrable no encontrada.");
        String id = roots.getFirst();
        lock("survey_version", "survey_id = ?", id);
        lock("survey_response", "dynamic_survey_id = ?", id);
        lock("csv_import_batch", "version_id IN (" + VERSIONS + ")", id);
        lock("xlsx_import_workbook", "version_id IN (" + VERSIONS + ")", id);
        lock("survey_preparation_config", "version_id IN (" + VERSIONS + ")", id);
        lock("survey_preparation_run", "version_id IN (" + VERSIONS + ")", id);
        lock("survey_general_analysis", "preparation_id IN (" + PREPARATIONS + ")", id);
        lock("survey_general_cross", "preparation_id IN (" + PREPARATIONS + ")", id);
        lock("generated_report", "analysis_id IN (" + ANALYSES + ")", id);
        var state = management.get(slug);
        if (expectedHash == null || !state.hash().equals(expectedHash))
            throw conflict("La encuesta o sus datos cambiaron. Recarga esta pantalla antes de continuar.");
        validateOwnership(id, slug);
        var tables = new LinkedHashMap<String, List<Map<String, Object>>>();
        add(tables, "survey", "id = ?", "id", id);
        add(tables, "survey_version", "survey_id = ?", "id", id);
        add(tables, "survey_section", "version_id IN (" + VERSIONS + ")", "version_id, position, section_id", id);
        add(tables, "survey_question", "version_id IN (" + VERSIONS + ")", "version_id, position, question_id", id);
        add(tables, "survey_question_option", "version_id IN (" + VERSIONS + ")", "version_id, question_id, position, option_code", id);
        add(tables, "survey_definition_event", "version_id IN (" + VERSIONS + ")", "version_id, revision, id", id);
        add(tables, "instrument_snapshot", "survey_slug = ?", "survey_slug, instrument_version", slug);
        add(tables, "survey_response", "dynamic_survey_id = ?", "id", id);
        add(tables, "survey_answer", "response_id IN (" + RESPONSES + ")", "response_id, question_index, item_index", id);
        add(tables, "survey_question_state", "response_id IN (" + RESPONSES + ")", "response_id, question_index", id);
        add(tables, "response_capture", "response_id IN (" + RESPONSES + ")", "response_id", id);
        for (String table : List.of("wrangling_correction", "wrangling_origin_classification", "wrangling_text_coding"))
            add(tables, table, "response_id IN (" + RESPONSES + ")", "revision_order, id", id);
        add(tables, "csv_import_batch", "version_id IN (" + VERSIONS + ")", "id", id);
        add(tables, "csv_import_mapping", "batch_id IN (" + BATCHES + ")", "batch_id, revision, id", id);
        add(tables, "csv_import_confirmation", "batch_id IN (" + BATCHES + ")", "batch_id", id);
        add(tables, "csv_import_row", "batch_id IN (" + BATCHES + ")", "batch_id, row_number", id);
        add(tables, "xlsx_import_workbook", "version_id IN (" + VERSIONS + ")", "id", id);
        add(tables, "survey_preparation_config", "version_id IN (" + VERSIONS + ")", "version_id, revision, id", id);
        add(tables, "survey_preparation_run", "version_id IN (" + VERSIONS + ")", "id", id);
        add(tables, "survey_result_cut", "survey_id = ?", "id", id);
        add(tables,"scoped_text_dictionary_revision","survey_id = ?","context_key,revision",id);
        add(tables, "question_text_dictionary", "version_id IN (" + VERSIONS + ")", "id", id);
        add(tables, "survey_general_analysis", "preparation_id IN (" + PREPARATIONS + ")", "id", id);
        add(tables, "survey_general_cross", "preparation_id IN (" + PREPARATIONS + ")", "id", id);
        add(tables, "generated_report", "analysis_id IN (" + ANALYSES + ")", "id", id);
        add(tables, "generated_report_cross", "report_id IN (" + REPORTS + ")", "report_id, position", id);
        var context = map("survey_study", rows("SELECT * FROM survey_study WHERE id IN (SELECT study_id FROM survey WHERE id = ?) ORDER BY id", id),
                "survey_management_event", rows("SELECT * FROM survey_management_event WHERE survey_id = ? ORDER BY created_at, id", id),
                "report_template_revision", rows("SELECT * FROM report_template_revision WHERE id IN (SELECT template_revision_id FROM generated_report WHERE id IN (" + REPORTS + ")) ORDER BY id", id),
                "report_template", rows("SELECT * FROM report_template WHERE id IN (SELECT template_id FROM report_template_revision WHERE id IN (SELECT template_revision_id FROM generated_report WHERE id IN (" + REPORTS + "))) ORDER BY id", id));
        var preserved = map("wrangling_runs", rows("SELECT id, logical_hash, bundle_hash FROM wrangling_run WHERE id IN (SELECT run_id FROM wrangling_member WHERE response_id IN (" + RESPONSES + ")) ORDER BY id", id),
                "statistical_analyses", rows("SELECT id, source_run_id, source_logical_hash, logical_hash, bundle_hash FROM statistical_analysis WHERE source_run_id IN (SELECT run_id FROM wrangling_member WHERE response_id IN (" + RESPONSES + ")) ORDER BY id", id),
                "policy", "Se conservan los conjuntos históricos congelados compartidos y sus miembros; pueden contener referencias a envíos eliminados. No se modifican sus huellas ni los resultados de otras encuestas.",
                "other_resources", List.of("universal_dictionary_state", "universal_dictionary_revision", "universal_term_lookup", "administrator", "survey_study", "report_template", "report_template_revision", "report_presentation_template", "report_template_management_event", "hidden_legacy_report_template", "survey_management_event"));
        var content = map("format", "RESPALDO_ENCUESTA_V1", "tables", tables, "context", context, "preserved_shared", preserved);
        return new Snapshot(state, tables, content, CanonicalJson.hash(content));
    }

    private void validateOwnership(String id, String slug) {
        reject("SELECT COUNT(*) FROM survey_response WHERE survey_slug = ? AND (dynamic_survey_id IS NULL OR dynamic_survey_id <> ?)", slug, id);
        reject("SELECT COUNT(*) FROM survey_response WHERE dynamic_survey_id = ? AND survey_slug <> ?", id, slug);
        reject("SELECT COUNT(*) FROM response_capture WHERE survey_slug = ? AND response_id NOT IN (" + RESPONSES + ")", slug, id);
        reject("SELECT COUNT(*) FROM survey_version WHERE survey_id <> ? AND predecessor_id IN (" + VERSIONS + ")", id, id);
        reject("SELECT COUNT(*) FROM survey_version WHERE survey_id = ? AND predecessor_id IS NOT NULL AND predecessor_id NOT IN (" + VERSIONS + ")", id, id);
        reject("SELECT COUNT(*) FROM survey_preparation_run r JOIN survey_preparation_config c ON c.id = r.config_id WHERE r.version_id <> c.version_id AND (r.version_id IN (" + VERSIONS + ") OR c.version_id IN (" + VERSIONS + "))", id, id);
        reject("SELECT COUNT(*) FROM csv_import_row r JOIN csv_import_mapping m ON m.id = r.mapping_id WHERE r.batch_id <> m.batch_id AND (r.batch_id IN (" + BATCHES + ") OR m.batch_id IN (" + BATCHES + "))", id, id);
        reject("SELECT COUNT(*) FROM csv_import_confirmation c JOIN csv_import_mapping m ON m.id = c.mapping_id WHERE c.batch_id <> m.batch_id AND (c.batch_id IN (" + BATCHES + ") OR m.batch_id IN (" + BATCHES + "))", id, id);
        reject("SELECT COUNT(*) FROM csv_import_row WHERE (batch_id IN (" + BATCHES + ") AND response_id NOT IN (" + RESPONSES + ")) OR (batch_id NOT IN (" + BATCHES + ") AND response_id IN (" + RESPONSES + "))", id, id, id, id);
        reject("SELECT COUNT(*) FROM csv_import_row r JOIN csv_import_batch b ON b.id = r.batch_id JOIN survey_response s ON s.id = r.response_id WHERE b.version_id <> s.publication_version_id AND (b.version_id IN (" + VERSIONS + ") OR s.dynamic_survey_id = ?)", id, id);
        reject("SELECT COUNT(*) FROM generated_report_cross x JOIN generated_report r ON r.id = x.report_id JOIN survey_general_analysis a ON a.id = r.analysis_id JOIN survey_general_cross c ON c.id = x.cross_id WHERE a.preparation_id <> c.preparation_id AND (r.id IN (" + REPORTS + ") OR c.id IN (" + CROSSES + "))", id, id);
    }

    private void deleteOwned(String id, String slug) {
        remove("generated_report_cross", "report_id IN (" + REPORTS + ")", id);
        remove("generated_report", "analysis_id IN (" + ANALYSES + ")", id);
        remove("survey_general_cross", "preparation_id IN (" + PREPARATIONS + ")", id);
        remove("survey_general_analysis", "preparation_id IN (" + PREPARATIONS + ")", id);
        remove("survey_result_cut", "survey_id = ?", id);
        remove("scoped_text_dictionary_revision","survey_id = ?",id);
        remove("question_text_dictionary", "version_id IN (" + VERSIONS + ")", id);
        remove("survey_preparation_run", "version_id IN (" + VERSIONS + ")", id);
        remove("survey_preparation_config", "version_id IN (" + VERSIONS + ")", id);
        for (String table : List.of("csv_import_row", "csv_import_confirmation", "csv_import_mapping"))
            remove(table, "batch_id IN (" + BATCHES + ")", id);
        remove("csv_import_batch", "version_id IN (" + VERSIONS + ")", id);
        remove("xlsx_import_workbook", "version_id IN (" + VERSIONS + ")", id);
        for (String table : List.of("survey_answer", "survey_question_state", "response_capture", "wrangling_correction", "wrangling_origin_classification", "wrangling_text_coding"))
            remove(table, "response_id IN (" + RESPONSES + ")", id);
        remove("survey_response", "dynamic_survey_id = ?", id);
        jdbc.update("UPDATE survey SET active_version_id = NULL, draft_version_id = NULL WHERE id = ?", id);
        for (String table : List.of("survey_question_option", "survey_question", "survey_section", "survey_definition_event"))
            remove(table, "version_id IN (" + VERSIONS + ")", id);
        jdbc.update("UPDATE survey_version SET predecessor_id = NULL WHERE survey_id = ?", id);
        remove("survey_version", "survey_id = ?", id);
        remove("instrument_snapshot", "survey_slug = ?", slug);
        remove("survey", "id = ?", id);
    }

    private byte[] archive(Snapshot snapshot, OffsetDateTime createdAt) {
        var sourceManifest = new ArrayList<Map<String, Object>>();
        var originals = new LinkedHashMap<String, byte[]>();
        for (String table : List.of("csv_import_batch", "xlsx_import_workbook")) {
            for (var row : snapshot.tables().get(table)) {
                byte[] bytes;
                try { bytes = Base64.getDecoder().decode((String) row.get("source_base64")); }
                catch (IllegalArgumentException ex) { throw conflict("Un archivo original no supera la comprobación de integridad."); }
                if (!hashBytes(bytes).equals(row.get("source_hash")))
                    throw conflict("Un archivo original no coincide con su huella. No se puede preparar el respaldo.");
                String extension = table.equals("xlsx_import_workbook") || String.valueOf(row.get("delimiter")).startsWith("XLSX:") ? ".xlsx" : ".csv";
                String entry = "originales/" + (table.equals("xlsx_import_workbook") ? "libro-" : "lote-") + safeId(row.get("id")) + extension;
                originals.put(entry, bytes);
                sourceManifest.add(map("table", table, "id", row.get("id"), "original_filename", row.get("file_name"), "entry", entry,
                        "sha256", row.get("source_hash"), "bytes", bytes.length, "source_sheet", row.get("source_sheet")));
            }
        }
        var manifest = map("format", "RESPALDO_ENCUESTA_V1", "created_at", createdAt.toString(), "survey_id", snapshot.state().id(),
                "survey_slug", snapshot.state().slug(), "state_hash", snapshot.state().hash(), "content_hash", snapshot.hash(),
                "counts", counts(snapshot.tables()), "sources", sourceManifest, "preserved_shared", snapshot.content().get("preserved_shared"),
                "data", "datos.json conserva las filas originales completas, capturas, correspondencias y revisiones. respuestas.csv es una copia de lectura con protección de fórmulas; no sustituye los valores originales.",
                "restore", "Respaldo de datos y trazabilidad para recuperación técnica; no es un archivo de importación automática.");
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("manifest.json", CanonicalJson.json(manifest).getBytes(StandardCharsets.UTF_8));
        entries.put("datos.json", CanonicalJson.json(snapshot.content()).getBytes(StandardCharsets.UTF_8));
        entries.put("respuestas.csv", csv(snapshot).getBytes(StandardCharsets.UTF_8));
        entries.putAll(originals);
        long size = entries.values().stream().mapToLong(b -> b.length).sum();
        if (size > MAX_ARCHIVE_BYTES) throw tooLarge();
        try (var output = new ByteArrayOutputStream(); var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (var entry : entries.entrySet()) {
                var item = new ZipEntry(entry.getKey()); item.setTime(0L);
                zip.putNextEntry(item); zip.write(entry.getValue()); zip.closeEntry();
            }
            zip.finish();
            if (output.size() > MAX_ARCHIVE_BYTES) throw tooLarge();
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo crear el respaldo completo. La encuesta se conserva.", ex);
        }
    }

    private String csv(Snapshot snapshot) {
        var output = new StringBuilder("\uFEFF");
        csvRow(output, List.of("response_id", "instrument_version", "submitted_at", "consent", "question_id", "question_label", "answer_state", "item_index", "answer_value", "display_value"));
        var responses = new LinkedHashMap<String, Map<String, Object>>();
        for (var row : snapshot.tables().get("survey_response")) responses.put((String) row.get("id"), row);
        var values = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var answer : snapshot.tables().get("survey_answer"))
            values.computeIfAbsent(answer.get("response_id") + "/" + answer.get("question_id"), ignored -> new ArrayList<>()).add(answer);
        for (var state : snapshot.tables().get("survey_question_state")) {
            var response = responses.get(state.get("response_id"));
            var answers = values.remove(state.get("response_id") + "/" + state.get("question_id"));
            if (answers == null || answers.isEmpty()) csvAnswer(output, response, state, null);
            else for (var answer : answers) csvAnswer(output, response, state, answer);
        }
        // También conserva visibles las respuestas históricas que no registraron estados.
        for (var answers : values.values()) for (var answer : answers)
            csvAnswer(output, responses.get(answer.get("response_id")), answer, answer);
        return output.toString();
    }

    private void csvAnswer(StringBuilder output, Map<String, Object> response, Map<String, Object> state, Map<String, Object> answer) {
        var cells = new ArrayList<Object>();
        for (String key : List.of("id", "instrument_version", "submitted_at", "consent")) cells.add(response.get(key));
        for (String key : List.of("question_id", "question_label", "answer_state")) cells.add(state.get(key));
        for (String key : List.of("item_index", "answer_value", "display_value")) cells.add(answer == null ? null : answer.get(key));
        csvRow(output, cells);
    }

    private void csvRow(StringBuilder output, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) output.append(',');
            String value = cells.get(i) == null ? "" : cells.get(i).toString();
            String first = value.stripLeading();
            if ((!first.isEmpty() && "=+-@".indexOf(first.charAt(0)) >= 0)
                    || (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0)) value = "'" + value;
            output.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        output.append("\r\n");
    }

    private void lock(String table, String condition, String id) {
        jdbc.queryForList("SELECT id FROM " + table + " WHERE " + condition + " ORDER BY id FOR UPDATE", String.class, id);
    }
    private void add(Map<String, List<Map<String, Object>>> tables, String table, String condition, String order, String id) {
        tables.put(table, rows("SELECT * FROM " + table + " WHERE " + condition + " ORDER BY " + order, id));
    }
    private List<Map<String, Object>> rows(String query, Object... args) {
        return jdbc.query(query, (rs, index) -> {
            var row = new LinkedHashMap<String, Object>(); var metadata = rs.getMetaData();
            for (int column = 1; column <= metadata.getColumnCount(); column++) {
                Object value = rs.getObject(column);
                if (value instanceof OffsetDateTime time) value = time.toInstant().toString();
                else if (value instanceof java.sql.Timestamp time) value = time.toInstant().toString();
                else if (value instanceof java.sql.Clob clob) value = clob.getSubString(1, Math.toIntExact(clob.length()));
                row.put(metadata.getColumnLabel(column).toLowerCase(Locale.ROOT), value);
            }
            return row;
        }, args);
    }
    private void remove(String table, String condition, String id) { jdbc.update("DELETE FROM " + table + " WHERE " + condition, id); }
    private void reject(String query, Object... args) {
        if (jdbc.queryForObject(query, Long.class, args) != 0)
            throw conflict("Hay referencias de datos entre encuestas o versiones que requieren revisión. La encuesta se conserva.");
    }
    private static Map<String, Long> counts(Map<String, List<Map<String, Object>>> tables) {
        var counts = new LinkedHashMap<String, Long>(); tables.forEach((name, rows) -> counts.put(name, (long) rows.size())); return counts;
    }
    private static Receipt receipt(Map<String, Object> row) {
        return new Receipt((String) row.get("id"), (String) row.get("state_hash"), (String) row.get("filename"), date(row.get("created_at")).toString());
    }
    private static OffsetDateTime date(Object value) {
        if (value instanceof OffsetDateTime time) return time;
        if (value instanceof java.sql.Timestamp time) return time.toInstant().atOffset(ZoneOffset.UTC);
        return OffsetDateTime.parse(value.toString());
    }
    private static String safeId(Object value) {
        try { return UUID.fromString(value.toString()).toString(); }
        catch (RuntimeException ex) { throw conflict("Un identificador del respaldo no es válido."); }
    }
    private static void actor(String actor) {
        if (actor == null || actor.isBlank() || actor.length() > 100 || actor.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("El administrador no es válido.");
    }
    private static OffsetDateTime now() { return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS); }
    private static String hashBytes(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 no está disponible", ex); }
    }
    private static Map<String, Object> map(Object... fields) {
        var map = new LinkedHashMap<String, Object>(); for (int i = 0; i < fields.length; i += 2) map.put((String) fields[i], fields[i + 1]); return map;
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException tooLarge() { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El respaldo completo supera 64 MiB. Prepara una copia de la base antes de eliminar; la encuesta se conserva."); }
}

package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.InstrumentRegistry;
import bo.edu.umss.mercado.survey.DraftSurveyService;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class DraftMigrationPersistenceTest {
    @TempDir Path directory;

    @Test void v10PreservesPublishedV9AndPreparedBundleSurvivesRestart() {
        String url = "jdbc:h2:file:" + directory.resolve("preparation-upgrade").toAbsolutePath().toString().replace('\\', '/') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        String versionId, runId, originalBundle, analysisId, originalAnalysis, reportId, originalReport, csvBatchId, csvMappingId, csvPreviewHash;
        java.util.List<String> csvResponseIds, excelResponseIds; String workbookId,excelBatchId,excelMappingId,excelPreviewHash;
        byte[] excelBytes=bo.edu.umss.mercado.importing.XlsxFileTest.workbook(bo.edu.umss.mercado.importing.XlsxFileTest.sheet(java.util.List.of(java.util.List.of("consentimiento","meses"),java.util.List.of("SI","9"))));
        byte[] csvBytes="consentimiento,meses\r\nSI,7\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Map<String, Object> before;
        var catalog = new SurveyCatalog();
        try (var source = dataSource(url)) {
            Flyway.configure().dataSource(source).target("9").load().migrate();
            var jdbc = new JdbcTemplate(source);
            var registry = new InstrumentRegistry(jdbc, catalog);
            var drafts = new DraftSurveyService(jdbc, catalog);
            var publications = new bo.edu.umss.mercado.survey.SurveyPublicationService(jdbc, drafts, registry);
            versionId = drafts.create("Publicada antes de V10", "Prueba sintética", "admin");
            drafts.section(versionId, 0, null, "Datos", "admin");
            drafts.question(versionId, 1, drafts.get(versionId).sections().getFirst().id(),
                    new bo.edu.umss.mercado.survey.Question("meses", "Datos", "Meses", "number", false, java.util.List.of(), null, null, 0, 1, "", 0, 120), true, "admin");
            publications.publish(versionId, 2, "admin");
            before = jdbc.queryForMap("SELECT * FROM survey_version WHERE id = ?", versionId);
            jdbc.update("INSERT INTO survey_response (id, submission_token, survey_slug, instrument_version, submitted_at, consent) VALUES ('old-v9', 'old-v9-token', 'titulados', 'historical', CURRENT_TIMESTAMP, TRUE)");
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForMap("SELECT * FROM survey_version WHERE id = ?", versionId)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT publication_version_id FROM survey_response WHERE id = 'old-v9'", String.class)).isNull();
            var preparation = new bo.edu.umss.mercado.wrangling.GenericPreparationService(jdbc, publications, new bo.edu.umss.mercado.data.CaptureStore(jdbc, registry));
            String cfg = preparation.configure(versionId, 0, java.util.List.of(new bo.edu.umss.mercado.wrangling.GenericPreparationService.Field("meses", "MAGNITUD_ENTERA", "meses")), "admin");
            runId = preparation.prepare(versionId, cfg, "NO_DECLARADO", "BORRADOR", null, null, "admin");
            originalBundle = bo.edu.umss.mercado.data.CanonicalJson.json(preparation.bundle(runId));
            var analyses = new bo.edu.umss.mercado.statistics.GenericAnalysisService(jdbc, preparation);
            analysisId = analyses.create(runId, "admin");
            originalAnalysis = bo.edu.umss.mercado.data.CanonicalJson.json(analyses.get(analysisId));
            var reportService = new bo.edu.umss.mercado.reporting.ReportService(jdbc, analyses, new bo.edu.umss.mercado.statistics.GenericCrossService(jdbc, preparation), preparation);
            String templateId = reportService.createTemplate(new bo.edu.umss.mercado.reporting.ReportService.Template("Reporte persistente", "Prueba", java.util.List.of("CONTEXTO", "DESCRIPTIVOS", "COMENTARIOS"), false), "admin");
            reportId = reportService.generate(reportService.latest(templateId).id(), analysisId, java.util.List.of(), "Comentario persistente", "admin");
            originalReport = bo.edu.umss.mercado.data.CanonicalJson.json(reportService.get(reportId));
            var captures = new bo.edu.umss.mercado.data.CaptureStore(jdbc, registry);
            var csv = new bo.edu.umss.mercado.importing.CsvImportService(jdbc, publications, new bo.edu.umss.mercado.response.SurveyResponseRepository(jdbc, catalog, captures, publications), captures, new bo.edu.umss.mercado.survey.SurveyValidator());
            csvBatchId = csv.upload(versionId, "persistente.csv", csvBytes, "COMA", "GENERICO", "admin");
            csvMappingId = csv.mapColumns(csvBatchId, 0, new bo.edu.umss.mercado.importing.CsvImportService.Mapping(java.util.List.of(new bo.edu.umss.mercado.importing.CsvImportService.Column("meses", 1)), 0, "SI", -1, -1, "CODIGOS", "", true, "Revisé unidad meses y reglas originales.", "La fuente recoge consentimiento académico."), "admin");
            csvPreviewHash = csv.preview(csvBatchId, csvMappingId).hash();
            csvResponseIds = csv.confirm(csvBatchId, csvMappingId, csvPreviewHash, false, "admin");
            var excel=new bo.edu.umss.mercado.importing.XlsxImportService(jdbc,publications,csv);
            workbookId=excel.upload(versionId,"persistente.xlsx",excelBytes,"GENERICO","admin"); excelBatchId=excel.select(workbookId,1,"admin");
            excelMappingId=csv.mapColumns(excelBatchId,0,csv.revision(csvBatchId,csvMappingId).mapping(),"admin"); excelPreviewHash=csv.preview(excelBatchId,excelMappingId).hash();
            excelResponseIds=csv.confirm(excelBatchId,excelMappingId,excelPreviewHash,false,"admin");
        }
        try (var source = dataSource(url)) {
            Flyway.configure().dataSource(source).load().migrate();
            var jdbc = new JdbcTemplate(source);
            var registry = new InstrumentRegistry(jdbc, catalog);
            var publications = new bo.edu.umss.mercado.survey.SurveyPublicationService(jdbc, new DraftSurveyService(jdbc, catalog), registry);
            var preparation = new bo.edu.umss.mercado.wrangling.GenericPreparationService(jdbc, publications, new bo.edu.umss.mercado.data.CaptureStore(jdbc, registry));
            assertThat(bo.edu.umss.mercado.data.CanonicalJson.json(preparation.bundle(runId))).isEqualTo(originalBundle);
            var analyses = new bo.edu.umss.mercado.statistics.GenericAnalysisService(jdbc, preparation);
            assertThat(bo.edu.umss.mercado.data.CanonicalJson.json(analyses.get(analysisId))).isEqualTo(originalAnalysis);
            var reportService = new bo.edu.umss.mercado.reporting.ReportService(jdbc, analyses, new bo.edu.umss.mercado.statistics.GenericCrossService(jdbc, preparation), preparation);
            assertThat(bo.edu.umss.mercado.data.CanonicalJson.json(reportService.get(reportId))).isEqualTo(originalReport);
            var captures = new bo.edu.umss.mercado.data.CaptureStore(jdbc, registry);
            var csv = new bo.edu.umss.mercado.importing.CsvImportService(jdbc, publications, new bo.edu.umss.mercado.response.SurveyResponseRepository(jdbc, catalog, captures, publications), captures, new bo.edu.umss.mercado.survey.SurveyValidator());
            var excel=new bo.edu.umss.mercado.importing.XlsxImportService(jdbc,publications,csv);
            assertThat(excel.original(workbookId)).isEqualTo(excelBytes); assertThat(csv.original(excelBatchId)).isEqualTo(excelBytes);
            assertThat(excel.select(workbookId,1,"admin")).isEqualTo(excelBatchId);
            assertThat(csv.confirm(excelBatchId,excelMappingId,excelPreviewHash,false,"admin")).isEqualTo(excelResponseIds);
            assertThat(captures.input(excelResponseIds.getFirst()).provenance()).isEqualTo("IMPORTACION_XLSX");
            assertThat(csv.original(csvBatchId)).isEqualTo(csvBytes);
            assertThat(csv.confirm(csvBatchId, csvMappingId, csvPreviewHash, false, "admin")).isEqualTo(csvResponseIds);
            assertThat(captures.input(csvResponseIds.getFirst()).provenance()).isEqualTo("IMPORTACION_CSV");
            assertThat(preparation.configuration(versionId).revision()).isEqualTo(1);
            assertThat(jdbc.queryForMap("SELECT * FROM survey_version WHERE id = ?", versionId)).isEqualTo(before);
        }
    }

    @Test void v7DraftAndItsOriginalAuditRemainIntactWhenPublicationIsAdded() {
        String url = "jdbc:h2:file:" + directory.resolve("v7-upgrade").toAbsolutePath().toString().replace('\\', '/') + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE";
        try (var source = dataSource(url)) {
            Flyway.configure().dataSource(source).target("7").load().migrate();
            var jdbc = new JdbcTemplate(source);
            jdbc.update("INSERT INTO survey VALUES ('survey-before', 'umss-mercado-2026-borrador', 'encuesta-before', CURRENT_TIMESTAMP, 'test-admin')");
            jdbc.update("INSERT INTO survey_version VALUES ('version-before', 'survey-before', 'borrador-1', 'BORRADOR', 'Título guardado', 'Descripción guardada', 4, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO survey_section VALUES ('version-before', 'section-before', 'Datos', 0)");
            jdbc.update("INSERT INTO survey_question VALUES ('version-before', 'edad', 'section-before', 0, 'Edad', 'number', FALSE, '', 0, 1, 0, 120, NULL, NULL)");
            jdbc.update("INSERT INTO survey_definition_event VALUES ('event-before', 'version-before', 4, 'EDITAR_PREGUNTA', 'test-admin', CURRENT_TIMESTAMP, '{}', 'original-hash')");
            var originalEvent = jdbc.queryForMap("SELECT * FROM survey_definition_event WHERE id = 'event-before'");
            Flyway.configure().dataSource(source).load().migrate();
            var restored = new DraftSurveyService(jdbc, new SurveyCatalog()).get("version-before");
            assertThat(restored.title()).isEqualTo("Título guardado"); assertThat(restored.revision()).isEqualTo(4);
            assertThat(restored.items().getFirst().question().max()).isEqualTo(120);
            assertThat(jdbc.queryForMap("SELECT * FROM survey_definition_event WHERE id = 'event-before'")).isEqualTo(originalEvent);
            assertThat(jdbc.queryForObject("SELECT draft_version_id FROM survey WHERE id = 'survey-before'", String.class)).isEqualTo("version-before");
        }
    }

    @Test void migrationPreservesHistoricalRowsAndDraftSurvivesConnectionRestart() {
        String url = "jdbc:h2:file:" + directory.resolve("draft-test").toAbsolutePath().toString().replace('\\', '/') + ";MODE=PostgreSQL";
        SurveyCatalog catalog = new SurveyCatalog();
        Map<String, Object> originalSnapshot;
        String id;
        try (var source = dataSource(url)) {
            Flyway.configure().dataSource(source).target("6").load().migrate();
            var jdbc = new JdbcTemplate(source);
            new InstrumentRegistry(jdbc, catalog).register(catalog.get("titulados"));
            jdbc.update("INSERT INTO survey_response VALUES ('historical', 'historical-token', 'titulados', 'old-version', CURRENT_TIMESTAMP, TRUE)");
            jdbc.update("INSERT INTO survey_answer VALUES ('historical', 'edad', 0, 'Edad original', 0, '27', '27')");
            originalSnapshot = jdbc.queryForMap("SELECT definition_json, definition_hash FROM instrument_snapshot WHERE survey_slug = 'titulados'");
            Flyway.configure().dataSource(source).load().migrate();
            assertThat(jdbc.queryForObject("SELECT answer_value FROM survey_answer WHERE response_id = 'historical'", String.class)).isEqualTo("27");
            assertThat(jdbc.queryForMap("SELECT definition_json, definition_hash FROM instrument_snapshot WHERE survey_slug = 'titulados'")).isEqualTo(originalSnapshot);
            var drafts = new DraftSurveyService(jdbc, catalog);
            id = drafts.create("Persistencia", "Borrador sintético", "test-admin");
            drafts.section(id, 0, null, "Sección persistente", "test-admin");
        }
        try (var source = dataSource(url)) {
            Flyway.configure().dataSource(source).load().migrate();
            var jdbc = new JdbcTemplate(source);
            var restored = new DraftSurveyService(jdbc, catalog).get(id);
            assertThat(restored.title()).isEqualTo("Persistencia");
            assertThat(restored.sections().getFirst().title()).isEqualTo("Sección persistente");
            assertThat(restored.revision()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE id = 'historical'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForMap("SELECT definition_json, definition_hash FROM instrument_snapshot WHERE survey_slug = 'titulados'")).isEqualTo(originalSnapshot);
        }
    }

    private HikariDataSource dataSource(String url) {
        var config = new HikariConfig(); config.setJdbcUrl(url); config.setUsername("sa"); config.setPassword("");
        config.setMaximumPoolSize(2); return new HikariDataSource(config);
    }
}

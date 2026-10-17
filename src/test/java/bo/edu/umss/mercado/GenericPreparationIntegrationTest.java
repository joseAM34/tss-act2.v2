package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.*;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.survey.*;
import bo.edu.umss.mercado.wrangling.*;
import java.util.*;
import java.time.*;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:generic-tests;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}", "spring.datasource.password=${TEST_DATABASE_PASSWORD:}"})
@AutoConfigureMockMvc @ActiveProfiles("test") @WithMockUser(username = "preparation-admin", roles = "ADMIN")
class GenericPreparationIntegrationTest {
    @Autowired GenericPreparationService service;
    @Autowired bo.edu.umss.mercado.statistics.GenericAnalysisService analyses;
    @Autowired bo.edu.umss.mercado.statistics.GenericCrossService crosses;
    @Autowired bo.edu.umss.mercado.reporting.ReportService reports;
    @Autowired bo.edu.umss.mercado.reporting.ReportCrossComposer reportCrosses;
    @Autowired bo.edu.umss.mercado.importing.CsvImportService csv;
    @Autowired bo.edu.umss.mercado.importing.XlsxImportService excel;
    @Autowired DraftSurveyService drafts;
    @Autowired SurveyPublicationService publications;
    @Autowired SurveyResponseRepository responses;
    @Autowired SurveyValidator validator;
    @Autowired WranglingService wrangling;
    @Autowired CaptureStore captures;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    String version;
    @BeforeEach void setup() {
        clear(); version = drafts.create("Encuesta general <script>evil()</script>", "Prueba ficticia", "test-admin");
        drafts.section(version, 0, null, "Datos", "test-admin"); String section = drafts.get(version).sections().getFirst().id();
        drafts.question(version, 1, section, new Question("control", "Datos", "Filtro", "radio", true,
                List.of(new Question.Option("SI", "Sí"), new Question.Option("NO", "No"), new Question.Option("NO_SABE", "No sabe")), null, null, 0, 1, "", null, null), true, "test-admin");
        drafts.question(version, 2, section, new Question("opciones", "Datos", "Opciones", "checkbox", false,
                List.of(new Question.Option("A", "Uno"), new Question.Option("B", "Dos")), "control", "SI", 1, 2, "", null, null), true, "test-admin");
        drafts.question(version, 3, section, new Question("meses", "Datos", "Duración", "number", false, List.of(), null, null, 0, 1, "", 0, 120), true, "test-admin");
        drafts.question(version, 4, section, new Question("correo", "Datos", "Correo", "email", false, List.of(), null, null, 0, 1, "", null, null), true, "test-admin");
        drafts.question(version, 5, section, new Question("organizacion", "Datos", "Organización", "text", false, List.of(), null, null, 0, 1, "", null, null), true, "test-admin");
        publications.publish(version, 6, "test-admin");
    }
    @AfterEach void clear() {
        for(String table:List.of("xlsx_import_workbook","csv_import_row","csv_import_confirmation","csv_import_mapping","csv_import_batch")) jdbc.update("DELETE FROM "+table);
        for (String table : List.of("report_presentation_template","generated_report_cross","generated_report","report_template_revision","report_template")) jdbc.update("DELETE FROM " + table);
        jdbc.update("DELETE FROM survey_general_cross");
        jdbc.update("DELETE FROM survey_general_analysis");
        jdbc.update("DELETE FROM survey_preparation_run"); jdbc.update("DELETE FROM survey_preparation_config");
        jdbc.update("DELETE FROM survey_answer WHERE response_id IN (SELECT id FROM survey_response WHERE publication_version_id IS NOT NULL)");
        jdbc.update("DELETE FROM survey_question_state WHERE response_id IN (SELECT id FROM survey_response WHERE publication_version_id IS NOT NULL)");
        jdbc.update("DELETE FROM survey_response WHERE publication_version_id IS NOT NULL");
        jdbc.update("UPDATE survey SET draft_version_id = NULL, active_version_id = NULL");
        for (String t : List.of("survey_definition_event", "survey_question_option", "survey_question", "survey_section", "survey_version", "survey")) jdbc.update("DELETE FROM " + t);
        jdbc.update("DELETE FROM instrument_snapshot WHERE survey_slug LIKE 'encuesta-%'");
        jdbc.update("UPDATE survey_study SET status = 'BORRADOR' WHERE id = 'umss-mercado-2026-borrador'");
    }
    List<GenericPreparationService.Field> fields() {
        return List.of(new GenericPreparationService.Field("control", "NOMINAL", ""), new GenericPreparationService.Field("opciones", "NOMINAL", ""),
                new GenericPreparationService.Field("meses", "MAGNITUD_ENTERA", "meses completos"), new GenericPreparationService.Field("correo", "EXCLUIR", ""), new GenericPreparationService.Field("organizacion", "EXCLUIR", ""));
    }
    String config() { return service.configure(version, 0, fields(), "test-admin"); }
    String response(String control, String months, String... choices) {
        var values = new LinkedMultiValueMap<String, String>(); values.set("consent", "SI"); values.set("control", control);
        if (months != null) values.set("meses", months); if (choices.length > 0) values.put("opciones", List.of(choices));
        values.set("correo", "privado@example.test"); values.set("organizacion", "Organización privada ficticia");
        var validation = validator.validate(publications.get(version).content().definition(), values); assertThat(validation.valid()).isTrue();
        return responses.savePublished(version, UUID.randomUUID().toString(), validation);
    }
    String run(String config, String origin) { return service.prepare(version, config, origin, "BORRADOR", null, null, "test-admin"); }
    @SuppressWarnings("unchecked") Map<String, Object> logical(String run) { return (Map<String, Object>) service.bundle(run).get("logical"); }
    @SuppressWarnings("unchecked") List<Map<String, Object>> rows(Map<String, Object> data, String key) { return (List<Map<String, Object>>) data.get(key); }

    @Test void defaultsExcludeEveryQuestionAndCannotPrepareBeforeSaving() {
        var initial = service.configuration(version); assertThat(initial.revision()).isZero();
        assertThat(initial.content().fields()).allMatch(f -> f.treatment().equals("EXCLUIR"));
        assertThatThrownBy(() -> run("", "NO_DECLARADO")).isInstanceOf(IllegalArgumentException.class);
        var excluded = service.configure(version, 0, initial.content().fields(), "admin");
        assertThatThrownBy(() -> run(excluded, "NO_DECLARADO")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void bulkSelectionOfOriginalGraduatesCompletesUnitsAndExcludesContactsAndText() throws Exception {
        String draft=drafts.copyLegacy("titulados","admin"); publications.publish(draft,0,"admin");
        mvc.perform(post("/admin/preparacion/"+draft+"/incluir-todos").param("revision","0")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/preparacion/"+draft+"/incluir-todos").with(csrf()).with(user("viewer").roles("USER")).param("revision","0")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/preparacion/"+draft+"/incluir-todos").with(csrf()).param("revision","0")).andExpect(status().is3xxRedirection());
        var fields=service.configuration(draft).content().fields();
        assertThat(fields).contains(new GenericPreparationService.Field("edad","MAGNITUD_ENTERA","años completos"),new GenericPreparationService.Field("anio_egreso","MAGNITUD_ENTERA","año calendario"),new GenericPreparationService.Field("meses_vida_profesional","MAGNITUD_ENTERA","meses completos"),new GenericPreparationService.Field("meses_desempleado","MAGNITUD_ENTERA","meses completos"),new GenericPreparationService.Field("estado_laboral","NOMINAL",""));
        assertThat(fields.stream().filter(f->List.of("email_contacto","telefono_contacto","linkedin","programa_cursado").contains(f.questionId()))).allMatch(f->f.treatment().equals("EXCLUIR"));
        assertThatThrownBy(()->service.selectCompatible(draft,0,"admin")).isInstanceOf(ResponseStatusException.class);
        mvc.perform(get("/admin/preparacion").param("versionId",draft)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Incluir todos los datos compatibles")));
    }
    @Test void bulkSelectionKeepsDeclaredUnitsWithoutGuessingUnknownNumericMeaning() {
        service.selectCompatible(version,0,"admin");
        assertThat(service.configuration(version).content().fields()).contains(new GenericPreparationService.Field("meses","EXCLUIR",""),new GenericPreparationService.Field("control","NOMINAL",""));
        service.configure(version,1,fields(),"admin"); service.selectCompatible(version,2,"admin");
        assertThat(service.configuration(version).content().fields()).contains(new GenericPreparationService.Field("meses","MAGNITUD_ENTERA","meses completos"));
    }

    byte[] excelBytes() {
        var table=bo.edu.umss.mercado.importing.CsvFile.parse(validCsv(),"COMA");
        String sheet=bo.edu.umss.mercado.importing.XlsxFileTest.sheet(table);
        return bo.edu.umss.mercado.importing.XlsxFileTest.workbook(sheet,sheet);
    }
    @Test void excelOriginalSelectionImportAnalysisAndReportRemainBound() {
        byte[] bytes=excelBytes(); String book=excel.upload(version,"respuestas.xlsx",bytes,"MICROSOFT_FORMS","admin");
        assertThat(excel.upload(version,"renombrado.xlsx",bytes,"MICROSOFT_FORMS","admin")).isEqualTo(book);
        assertThat(excel.get(book).sheets()).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE publication_version_id=?",Integer.class,version)).isZero();
        String batch=excel.select(book,2,"admin"),rev=csvRevision(batch); var preview=csv.preview(batch,rev);
        assertThat(preview.valid()).isTrue(); var ids=csv.confirm(batch,rev,preview.hash(),false,"admin");
        assertThat(csv.original(batch)).isEqualTo(bytes); assertThat(excel.original(book)).isEqualTo(bytes);
        assertThat(excel.select(book,2,"admin")).isEqualTo(batch);
        assertThat(captures.input(ids.getFirst()).provenance()).isEqualTo("IMPORTACION_XLSX");
        assertThat(bo.edu.umss.mercado.importing.CsvImportEvidence.verify(jdbc,ids.getFirst(),version,captures.input(ids.getFirst()).captureHash())).containsEntry("source_sheet",2).containsEntry("source_sheet_name","Respuestas 2");
        String cfg=config(),prepared=run(cfg,"NO_DECLARADO"),analysis=analyses.create(prepared,"admin");
        assertThat(reports.get(reports.generate(templateRevision(),analysis,List.of(),"Prueba Excel","admin"))).isNotEmpty();
        assertThat(csv.confirm(batch,rev,preview.hash(),false,"admin")).containsExactlyElementsOf(ids);
        String other=excel.select(book,1,"admin"); assertThat(other).isNotEqualTo(batch);
        var p=csv.preview(other,csvRevision(other)); assertThat(p.overlaps()).isPositive();
    }
    @Test void excelRejectsInvalidSheetAndTamperedSelection() {
        String book=excel.upload(version,"prueba.xlsx",excelBytes(),"GENERICO","admin");
        assertThatThrownBy(()->excel.select(book,3,"admin")).isInstanceOf(IllegalArgumentException.class);
        String batch=excel.select(book,1,"admin"),rev=csvRevision(batch); var p=csv.preview(batch,rev); var ids=csv.confirm(batch,rev,p.hash(),false,"admin");
        jdbc.update("UPDATE csv_import_batch SET delimiter='XLSX:2',source_sheet=2 WHERE id=?",batch);
        assertThatThrownBy(()->captures.input(ids.getFirst())).isInstanceOf(IllegalStateException.class);
    }
    @Test void excelRoutesRequireAdminCsrfAndRenderSheetSelection() throws Exception {
        var file=new org.springframework.mock.web.MockMultipartFile("file","prueba.xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",excelBytes());
        mvc.perform(multipart("/admin/importaciones").file(file).param("versionId",version).param("delimiter","COMA").param("platform","GENERICO")).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/importaciones").file(file).with(csrf()).param("versionId",version).param("delimiter","COMA").param("platform","GENERICO")).andExpect(status().is3xxRedirection());
        String book=excel.recent().getFirst().id();
        mvc.perform(get("/admin/importaciones/excel/"+book).with(user("visitante").roles("USER"))).andExpect(status().isForbidden());
        var html=mvc.perform(get("/admin/importaciones/excel/"+book)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Respuestas 2"))).andReturn().getResponse().getContentAsString();
        Path dir=Path.of(".codex-tmp/csv-browser"); Files.createDirectories(dir); Files.writeString(dir.resolve("sheets.html"),html);
        mvc.perform(post("/admin/importaciones/excel/"+book+"/hoja").param("sheet","1")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/importaciones/excel/"+book+"/hoja").with(csrf()).param("sheet","1")).andExpect(status().is3xxRedirection());
        String batch=excel.select(book,1,"admin");
        mvc.perform(get("/admin/importaciones/"+batch+"/original.txt")).andExpect(status().isOk()).andExpect(content().bytes(file.getBytes())).andExpect(header().string("Content-Type","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }
    byte[] csvBytes(String body) { return ("Consentimiento,Filtro,Opciones,Meses,Correo,Organización,Fecha,ID\r\n"+body).getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    byte[] validCsv() { return csvBytes("SI,SI,A|B,0,privado@example.test,Empresa ficticia,2026-01-01T12:30:00-04:00,id-1\r\nSI,NO,,12,otro@example.test,Otra empresa,,id-2\r\n"); }
    bo.edu.umss.mercado.importing.CsvImportService.Mapping csvMapping() {
        var cols=List.of(new bo.edu.umss.mercado.importing.CsvImportService.Column("control",1),new bo.edu.umss.mercado.importing.CsvImportService.Column("opciones",2),new bo.edu.umss.mercado.importing.CsvImportService.Column("meses",3),new bo.edu.umss.mercado.importing.CsvImportService.Column("correo",4),new bo.edu.umss.mercado.importing.CsvImportService.Column("organizacion",5));
        return new bo.edu.umss.mercado.importing.CsvImportService.Mapping(cols,0,"SI",6,7,"CODIGOS","|",true,"Revisé preguntas, unidades y condiciones del formulario original.","La fuente recoge aceptación del uso académico de estas respuestas.");
    }
    String csvBatch(byte[] bytes) { return csv.upload(version,"archivo.csv",bytes,"COMA","GENERICO","admin"); }
    String csvRevision(String batch) { return csv.mapColumns(batch,0,csvMapping(),"admin"); }
    @Test void csvUploadAndPreviewNeverSaveResponsesAndOriginalIsExact() {
        byte[] bytes=validCsv(); String batch=csvBatch(bytes), revision=csvRevision(batch);
        var preview=csv.preview(batch,revision); assertThat(preview.valid()).isTrue(); assertThat(preview.rows()).hasSize(2);
        assertThat(csv.original(batch)).isEqualTo(bytes); assertThat(csv.imported(batch)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE publication_version_id = ?",Integer.class,version)).isZero();
    }
    @Test void csvDuplicateHeadersKeepIndependentColumnPositions() {
        byte[] bytes=new String(validCsv(),java.nio.charset.StandardCharsets.UTF_8).replace("Correo,Organización","Dato,Dato").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String batch=csvBatch(bytes),rev=csvRevision(batch); var p=csv.preview(batch,rev);
        assertThat(p.valid()).isTrue();
        var id=csv.confirm(batch,rev,p.hash(),false,"admin").getFirst();
        assertThat(captures.input(id).answers()).containsEntry("correo",List.of("privado@example.test")).containsEntry("organizacion",List.of("Empresa ficticia"));
    }
    @Test void csvDatabaseFailureAfterFirstRowRollsBackEveryResponse() {
        String batch=csvBatch(validCsv()),rev=csvRevision(batch); var p=csv.preview(batch,rev);
        jdbc.execute("ALTER TABLE csv_import_row ADD CONSTRAINT csv_test_single_row CHECK (row_number < 3)");
        try { assertThatThrownBy(()->csv.confirm(batch,rev,p.hash(),false,"admin")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class); }
        finally { jdbc.execute("ALTER TABLE csv_import_row DROP CONSTRAINT csv_test_single_row"); }
        assertThat(csv.imported(batch)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_response WHERE publication_version_id = ?",Integer.class,version)).isZero();
        assertThat(csv.original(batch)).isEqualTo(validCsv());
    }
    @Test void csvConfirmationPreservesConsentStatesOriginDatesAndTraceableImportSource() {
        String batch=csvBatch(validCsv()),revision=csvRevision(batch); var preview=csv.preview(batch,revision);
        var ids=csv.confirm(batch,revision,preview.hash(),false,"admin"); assertThat(ids).hasSize(2);
        var first=captures.input(ids.getFirst()); assertThat(first.provenance()).isEqualTo("IMPORTACION_CSV"); assertThat(first.origin()).isEqualTo("NO_DECLARADO");
        assertThat(first.answers()).containsEntry("opciones",List.of("A","B")).containsEntry("meses",List.of("0"));
        assertThat(captures.input(ids.getLast()).states()).containsEntry("opciones","NO_APLICA");
        var ref=responses.importReference(ids.getFirst()); assertThat(ref).containsEntry("external_date","2026-01-01T12:30:00-04:00").containsEntry("date_precision","SEGUNDO").containsEntry("batch_id",batch);
        assertThat(first.submittedAt()).isNotEqualTo("2026-01-01T16:30:00Z");
        String cfg=config(); var prepared=logical(run(cfg,"NO_DECLARADO")); assertThat(rows(prepared,"members").getFirst()).containsEntry("capture_provenance","IMPORTACION_CSV");
        assertThat(CanonicalJson.json(prepared)).doesNotContain("privado@example.test","Empresa ficticia","id-1");
    }
    @Test void csvReuploadAndReconfirmReturnSameBatchAndResponsesEvenAfterClose() {
        String batch=csvBatch(validCsv()); assertThat(csv.upload(version,"otro nombre.csv",validCsv(),"COMA","GENERICO","admin")).isEqualTo(batch);
        String revision=csvRevision(batch); var preview=csv.preview(batch,revision); var ids=csv.confirm(batch,revision,preview.hash(),false,"admin");
        publications.close(version,publications.get(version).revision(),"admin");
        assertThat(csv.confirm(batch,revision,preview.hash(),false,"admin")).isEqualTo(ids);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM csv_import_row",Integer.class)).isEqualTo(2);
    }
    @Test void csvInvalidConsentAndMissingRulesNeverBecomeAcceptedResponses() {
        String batch=csvBatch(csvBytes("NO,NO,,0,,,2026-01-01T12:30:00Z,id-1\n")),revision=csvRevision(batch); var preview=csv.preview(batch,revision);
        assertThat(preview.valid()).isFalse(); assertThat(preview.rows().getFirst().errors()).containsKey("consent");
        assertThatThrownBy(()->csv.confirm(batch,revision,preview.hash(),false,"admin")).isInstanceOf(IllegalArgumentException.class);
        var m=csvMapping(); var unverified=new bo.edu.umss.mercado.importing.CsvImportService.Mapping(m.columns(),-1,"SI",6,7,"CODIGOS","|",false,"","");
        String next=csv.mapColumns(batch,1,unverified,"admin"); assertThat(csv.preview(batch,next).issues()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(csv.imported(batch)).isEmpty();
    }
    @Test void csvMissingColumnDoesNotInferHistoricalMissingState() {
        String batch=csvBatch(validCsv()); var m=csvMapping(); var cols=new ArrayList<>(m.columns()); cols.set(2,new bo.edu.umss.mercado.importing.CsvImportService.Column("meses",-1));
        String rev=csv.mapColumns(batch,0,new bo.edu.umss.mercado.importing.CsvImportService.Mapping(cols,0,"SI",6,7,"CODIGOS","|",true,m.compatibilityEvidence(),m.consentEvidence()),"admin");
        var preview=csv.preview(batch,rev); assertThat(preview.valid()).isFalse(); assertThat(preview.issues()).anyMatch(i->i.contains("no se infieren estados"));
    }
    @Test void csvInactiveValuesInvalidOptionsAndDuplicateExternalIdsBlockWholeBatch() {
        for(String body:List.of("SI,NO,A,0,,,2026-01-01T12:30:00Z,id-1\n","SI,SI,X,0,,,2026-01-01T12:30:00Z,id-1\n","SI,NO,,0,,,fecha no ISO,id-1\n","SI,NO,,0,,,,same\nSI,NO,,1,,,,same\n")) {
            String batch=csvBatch(csvBytes(body)),revision=csvRevision(batch); var preview=csv.preview(batch,revision); assertThat(preview.valid()).isFalse();
            assertThatThrownBy(()->csv.confirm(batch,revision,preview.hash(),false,"admin")).isInstanceOf(IllegalArgumentException.class); assertThat(csv.imported(batch)).isEmpty();
        }
    }
    @Test void csvExplicitLabelsAndMultipleSeparatorAreRequiredForNormalization() {
        String batch=csvBatch(csvBytes("SI,Sí,Uno|Dos,0,,,,\n")); var m=csvMapping();
        String rev=csv.mapColumns(batch,0,new bo.edu.umss.mercado.importing.CsvImportService.Mapping(m.columns(),0,"SI",6,7,"ETIQUETAS","|",true,m.compatibilityEvidence(),m.consentEvidence()),"admin");
        assertThat(csv.preview(batch,rev).valid()).isTrue();
        assertThat(captures.input(csv.confirm(batch,rev,csv.preview(batch,rev).hash(),false,"admin").getFirst()).answers()).containsEntry("control",List.of("SI")).containsEntry("opciones",List.of("A","B"));
    }
    @Test void csvNewMappingAfterConfirmationIsReviewedWithoutDuplicatingRows() {
        String batch=csvBatch(validCsv()),rev=csvRevision(batch); var preview=csv.preview(batch,rev); var ids=csv.confirm(batch,rev,preview.hash(),false,"admin");
        String next=csv.mapColumns(batch,1,csvMapping(),"admin"); assertThat(csv.preview(batch,next).valid()).isTrue();
        assertThatThrownBy(()->csv.confirm(batch,next,csv.preview(batch,next).hash(),false,"admin")).isInstanceOf(ResponseStatusException.class);
        assertThat(csv.imported(batch)).isEqualTo(ids);
        assertThatThrownBy(()->csv.mapColumns(batch,1,csvMapping(),"admin")).isInstanceOf(ResponseStatusException.class);
    }
    @Test void csvOverlapsRequireReviewAndNeverDeduplicateDistinctRows() {
        String batch=csvBatch(validCsv()),rev=csvRevision(batch); csv.confirm(batch,rev,csv.preview(batch,rev).hash(),false,"admin");
        String another=csvBatch((new String(validCsv(),java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n")).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        String mapping=csvRevision(another); var preview=csv.preview(another,mapping); assertThat(preview.overlaps()).isPositive();
        assertThatThrownBy(()->csv.confirm(another,mapping,preview.hash(),false,"admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(csv.confirm(another,mapping,preview.hash(),true,"admin")).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM csv_import_row",Integer.class)).isEqualTo(4);
    }
    @Test void csvNewOverlapCandidatesInvalidateAnAlreadyReviewedPreview() {
        String a=csvBatch(validCsv()),ar=csvRevision(a); var ap=csv.preview(a,ar);
        byte[] changed=new String(validCsv(),java.nio.charset.StandardCharsets.UTF_8).replace("\r\n","\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String b=csvBatch(changed),br=csvRevision(b); var before=csv.preview(b,br); assertThat(before.overlaps()).isZero();
        csv.confirm(a,ar,ap.hash(),false,"admin");
        assertThatThrownBy(()->csv.confirm(b,br,before.hash(),true,"admin")).isInstanceOf(ResponseStatusException.class);
        var refreshed=csv.preview(b,br); assertThat(refreshed.overlaps()).isPositive();
        csv.confirm(b,br,refreshed.hash(),true,"admin");
        assertThat(csv.confirm(a,ar,csv.preview(a,ar).hash(),false,"admin")).hasSize(2);
    }
    @Test void csvOriginalEvidenceTamperingAndWrongPreviewHashesAreRejected() {
        String batch=csvBatch(validCsv()),rev=csvRevision(batch); var p=csv.preview(batch,rev);
        assertThatThrownBy(()->csv.confirm(batch,rev,"falsa",false,"admin")).isInstanceOf(ResponseStatusException.class);
        var ids=csv.confirm(batch,rev,p.hash(),false,"admin");
        jdbc.update("UPDATE csv_import_row SET evidence_hash = 'alterado' WHERE response_id = ?",ids.getFirst());
        assertThatThrownBy(()->captures.input(ids.getFirst())).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE csv_import_batch SET source_hash = 'alterado' WHERE id = ?",batch); assertThatThrownBy(()->csv.original(batch)).isInstanceOf(IllegalStateException.class);
    }
    @Test void csvClosedVersionCannotReceiveNewRowsAndParallelConfirmIsIdempotent() throws Exception {
        String batch=csvBatch(validCsv()),rev=csvRevision(batch); var p=csv.preview(batch,rev);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var futures=List.of(1,2).stream().map(n->executor.submit(()->{start.await(); return csv.confirm(batch,rev,p.hash(),false,"admin");})).toList();
            start.countDown(); assertThat(futures.getFirst().get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(futures.getLast().get(15,java.util.concurrent.TimeUnit.SECONDS));
        }
        String other=csvBatch(csvBytes("SI,NO,,2,,,,\n")),mapping=csvRevision(other); var preview=csv.preview(other,mapping);
        publications.close(version,publications.get(version).revision(),"admin");
        assertThatThrownBy(()->csv.confirm(other,mapping,preview.hash(),false,"admin")).isInstanceOf(ResponseStatusException.class);
        assertThat(csv.imported(other)).isEmpty();
    }
    @Test void csvRoutesProtectSourcePreviewConfirmationAndEscapeOriginal() throws Exception {
        String batch=csvBatch(csvBytes("SI,NO,,0,,<script>bad()</script>,,\n")),rev=csvRevision(batch);
        for(String route:List.of("/admin/importaciones","/admin/importaciones/"+batch,"/admin/importaciones/"+batch+"/vista-previa?revisionId="+rev,"/admin/importaciones/"+batch+"/original.txt")) {
            mvc.perform(get(route).with(anonymous())).andExpect(status().is3xxRedirection()); mvc.perform(get(route).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        }
        var file=new org.springframework.mock.web.MockMultipartFile("file","prueba.csv","text/csv",validCsv());
        mvc.perform(multipart("/admin/importaciones").file(file).param("versionId",version).param("delimiter","COMA").param("platform","GENERICO")).andExpect(status().isForbidden());
        mvc.perform(multipart("/admin/importaciones").file(file).with(csrf()).param("versionId",version).param("delimiter","COMA").param("platform","GENERICO")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        mvc.perform(post("/admin/importaciones/"+batch+"/confirmar").param("revisionId",rev).param("previewHash",csv.preview(batch,rev).hash())).andExpect(status().isForbidden());
        mvc.perform(get("/admin/importaciones/"+batch+"/original.txt")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void csvBrowserFixturesUseRealProtectedHtmlWithSyntheticOriginals() throws Exception {
        String batch=csvBatch(validCsv()),rev=csvRevision(batch); Path dir=Path.of(".codex-tmp/csv-browser"); Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.html"),mvc.perform(get("/admin/importaciones")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("batch.html"),mvc.perform(get("/admin/importaciones/"+batch)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("preview.html"),mvc.perform(get("/admin/importaciones/"+batch+"/vista-previa").param("revisionId",rev)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    bo.edu.umss.mercado.reporting.ReportService.Template template(String title) { return new bo.edu.umss.mercado.reporting.ReportService.Template(title,"Introducción ficticia",List.of("CONTEXTO","DESCRIPTIVOS","CRUCES","COMENTARIOS"),true); }
    String templateRevision() { String root=reports.createTemplate(template("Reporte <script>unsafe()</script>"),"admin"); return reports.latest(root).id(); }
    @SuppressWarnings("unchecked") Map<String,Object> reportContent(String id) { return (Map<String,Object>) reports.get(id).get("content"); }
    @Test void advancedReportFreezesBackendHistogramsBoxesTimeScatterAndEmployerRadar() throws Exception {
        String next=publications.successor(version,"admin"),section=drafts.get(next).sections().getFirst().id();
        drafts.question(next,0,section,new Question("anios","Datos","Años de experiencia","number",false,List.of(),null,null,0,1,"",0,100),true,"admin");
        var options=List.of("TOTALMENTE_ACUERDO","PARCIALMENTE_ACUERDO","TOTALMENTE_DESACUERDO","NO_SABE").stream().map(value->new Question.Option(value,value)).toList();
        for(int i=1;i<=5;i++)drafts.question(next,i,section,new Question("criterio_%02d".formatted(i),"Datos","Criterio "+i,"radio",false,options,null,null,0,1,"",null,null),true,"admin");
        publications.publish(next,6,"admin");version=next;
        var fields=new ArrayList<>(fields());fields.add(new GenericPreparationService.Field("anios","MAGNITUD_ENTERA","años"));for(int i=1;i<=5;i++)fields.add(new GenericPreparationService.Field("criterio_%02d".formatted(i),"NOMINAL",""));
        String cfg=service.configure(version,0,fields,"admin");
        for(int n=0;n<4;n++){var values=new LinkedMultiValueMap<String,String>();values.set("consent","SI");values.set("control","SI");values.set("opciones","A");values.set("meses",String.valueOf(n*3));values.set("anios",String.valueOf(n));for(int i=1;i<=5;i++)values.set("criterio_%02d".formatted(i),n==3?"NO_SABE":n==2?"TOTALMENTE_DESACUERDO":"PARCIALMENTE_ACUERDO");var validation=validator.validate(publications.get(version).content().definition(),values);assertThat(validation.valid()).isTrue();responses.savePublished(version,UUID.randomUUID().toString(),validation);}
        String source=run(cfg,"NO_DECLARADO"),analysis=analyses.create(source,"admin");var base=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of());
        var questions=base.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),true,q.title(),q.comment(),q.id().equals("meses")?List.of("table","histogram","box","line","area"):q.id().equals("anios")?List.of("table","columns"):List.of("table"))).toList();
        var advanced=List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Advanced("scatter",List.of("meses","anios"),"Meses y años","Pares del mismo envío",true),new bo.edu.umss.mercado.reporting.ReportCustomization.Advanced("radar",java.util.stream.IntStream.rangeClosed(1,5).mapToObj(i->"criterio_%02d".formatted(i)).toList(),"Acuerdo de empleadores","No es un índice",true));
        var config=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,base.title(),"",true,true,true,"",questions,List.of(),List.of(),advanced);
        var preview=reports.preview(config,"admin");assertThat(preview.get("format")).isEqualTo("REPORTE_HTML_V6");
        String report=reports.generateCustomized(config,CanonicalJson.hash(preview),"admin");assertThat(CanonicalJson.json(reportContent(report))).isEqualTo(CanonicalJson.json(preview));
        String html=mvc.perform(get("/admin/reportes/"+report)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-representation=\"box\"","data-representation=\"histogram\"","data-report-advanced=\"0\"","data-report-advanced=\"1\"","Base sustantiva");
        String csv=new String(bo.edu.umss.mercado.reporting.ReportExport.csv(bo.edu.umss.mercado.reporting.ReportExport.rows(reports.get(report))),java.nio.charset.StandardCharsets.UTF_8);assertThat(csv).contains("graficos_avanzados","histogram","No sabe").doesNotContain("response_id","submitted_at");
        Path dir=Path.of(".codex-tmp/report-step6-browser");Files.createDirectories(dir);
        Files.writeString(dir.resolve("generator.html"),mvc.perform(get("/admin/reportes/"+report+"/nuevo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());Files.writeString(dir.resolve("report.html"),html);
        Files.writeString(dir.resolve("report.json"),mvc.perform(get("/admin/reportes/"+report+"/contenido.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(config))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var changed=new LinkedHashMap<>(reportContent(report));changed.put("advanced_charts",List.of());jdbc.update("UPDATE generated_report SET content_json=?,content_hash=? WHERE id=?",CanonicalJson.json(changed),CanonicalJson.hash(changed),report);assertThatThrownBy(()->reports.get(report)).isInstanceOf(IllegalStateException.class);
    }
    bo.edu.umss.mercado.reporting.ReportCustomization.Configuration multipleRepresentations(String analysis,String cross) {
        var base=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of(cross));
        var questions=base.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),true,q.title(),q.comment(),
                q.id().equals("control")?List.of("table","columns","bars","pie","donut"):q.id().equals("grupo")?List.of("table","columns"):List.of("table","columns","bars"))).toList();
        return new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,base.title(),"",true,true,true,"",questions,List.of(cross),List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(cross,"Cruce múltiple","Comentario",List.of("table","heat","grouped","stacked"))));
    }
    @Test void severalCompatibleRepresentationsFreezeAndRenderWithoutChangingFigures() throws Exception {
        String source=crossSource();response("SI","0","A");String analysis=analyses.create(source,"admin");
        // Numeric values must belong to the same frozen source, not later receipts.
        source=run(service.configuration(version).id(),"NO_DECLARADO");analysis=analyses.create(source,"admin");
        String cross=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        var config=multipleRepresentations(analysis,cross);var preview=reports.preview(config,"admin");
        assertThat(preview.get("format")).isEqualTo("REPORTE_HTML_V5");
        assertThat(rows((Map<String,Object>)preview.get("display_result"),"variables").getFirst().get("report_graphs")).isEqualTo(List.of("columns","bars","pie","donut"));
        String id=reports.generateCustomized(config,CanonicalJson.hash(preview),"admin");assertThat(CanonicalJson.json(reportContent(id))).isEqualTo(CanonicalJson.json(preview));
        String html=mvc.perform(get("/admin/reportes/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("data-representation=\"pie\"","data-representation=\"donut\"","data-representation=\"grouped\"","data-representation=\"stacked\"","Barras apiladas");
        String again=mvc.perform(get("/admin/reportes/"+id+"/nuevo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertThat(again).contains("representations");
        Path dir=Path.of(".codex-tmp/report-step5-browser");Files.createDirectories(dir);
        Files.writeString(dir.resolve("generator.html"),again);Files.writeString(dir.resolve("report.html"),html);
        Files.writeString(dir.resolve("report.json"),mvc.perform(get("/admin/reportes/"+id+"/contenido.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").param("focusCross",cross).with(csrf()).contentType("application/json").content(CanonicalJson.json(config))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var tableOnly=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(config.analysisId(),config.title(),"",true,true,true,"",config.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),true,q.title(),q.comment(),List.of("table"))).toList(),config.crossIds(),List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(cross,"Cruce múltiple","Comentario",List.of("table"))));
        Files.writeString(dir.resolve("table-preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").param("focusCross",cross).with(csrf()).contentType("application/json").content(CanonicalJson.json(tableOnly))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void serverRejectsIncompatibleAndRepeatedRepresentationsAndHiddenIncludedItems() {
        String analysis=analyses.create(crossSource(),"admin");var base=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of());
        for(var entry:List.of(Map.entry("meses",List.of("pie")),Map.entry("opciones",List.of("donut")),Map.entry("control",List.of("radar")),Map.entry("control",List.of("columns","columns")),Map.entry("control",List.<String>of()))){
            var questions=base.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),true,q.title(),q.comment(),q.id().equals(entry.getKey())?entry.getValue():List.of("table"))).toList();
            var invalid=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,base.title(),"",true,true,true,"",questions,List.of());
            assertThatThrownBy(()->reports.preview(invalid,"admin")).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
    }
    @Test void comparisonRepresentationsAreReviewedAndKeepOneStatisticalSource() {
        String analysis=analyses.create(crossSource(),"admin");
        var pair=new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control","grupo",List.of("table","heat","grouped","stacked"));
        var preview=reportCrosses.preview(pair);assertThat(preview.get("report_graphs")).isEqualTo(List.of("heat","grouped","stacked"));
        var saved=reportCrosses.save(new bo.edu.umss.mercado.reporting.ReportCrossComposer.Save(pair,CanonicalJson.hash(preview)),"admin");
        var table=new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control","grupo",List.of("table"));
        assertThatThrownBy(()->reportCrosses.save(new bo.edu.umss.mercado.reporting.ReportCrossComposer.Save(table,CanonicalJson.hash(preview)),"admin")).isInstanceOf(ResponseStatusException.class);
        var tablePreview=reportCrosses.preview(table);assertThat(reportCrosses.save(new bo.edu.umss.mercado.reporting.ReportCrossComposer.Save(table,CanonicalJson.hash(tablePreview)),"admin").get("id")).isEqualTo(saved.get("id"));
        assertThatThrownBy(()->reportCrosses.preview(new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control","grupo",List.of("pie")))).isInstanceOf(IllegalArgumentException.class);
        var config=multipleRepresentations(analysis,(String)saved.get("id"));var content=reports.preview(config,"admin");String report=reports.generateCustomized(config,CanonicalJson.hash(content),"admin");
        var altered=new LinkedHashMap<>(reportContent(report));altered.put("display_crosses",List.of());
        jdbc.update("UPDATE generated_report SET content_json=?,content_hash=? WHERE id=?",CanonicalJson.json(altered),CanonicalJson.hash(altered),report);
        assertThatThrownBy(()->reports.get(report)).isInstanceOf(IllegalStateException.class);
    }
    @Test void excludedItemsMayHaveNoRepresentationsButIncludedCrossMustHaveOne() throws Exception {
        String source=crossSource(),analysis=analyses.create(source,"admin");String cross=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        var base=multipleRepresentations(analysis,cross);
        var emptyCross=new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(cross,"Cruce","",List.of());
        var invalid=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,base.title(),"",true,true,true,"",base.questions(),List.of(cross),List.of(emptyCross));
        assertThatThrownBy(()->reports.preview(invalid,"admin")).isInstanceOf(IllegalArgumentException.class);
        var questions=base.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),!q.id().equals("control"),q.title(),"",q.id().equals("control")?List.of():List.of("table"))).toList();
        var valid=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,base.title(),"",true,true,true,"",questions,List.of(),List.of(emptyCross));
        mvc.perform(post("/admin/reportes/personalizar/vista-previa").param("focusCross",cross).with(csrf()).contentType("application/json").content(CanonicalJson.json(valid))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
    }
    @Test void reportCrossPreviewUsesFrozenGroupsAndRejectsIncompatibleQuestionsWithoutWrites() {
        String analysis=analyses.create(crossSource(),"admin");
        var pair=new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control","grupo");
        var preview=reportCrosses.preview(pair);var result=(Map<?,?>)preview.get("result");
        assertThat(((Map<?,?>)result.get("table")).get("p")).isNull();
        assertThat(reportCrosses.choices(analysis)).allMatch(q->q.get("group").equals("Datos"));
        assertThat(reportCrosses.choices(analysis)).anyMatch(q->q.get("id").equals("meses")&&q.get("eligible").equals(false));
        for(String other:List.of("control","meses","opciones","correo","forged")){
            assertThatThrownBy(()->reportCrosses.preview(new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control",other))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_general_cross",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
    }
    @Test void reportCrossSaveRequiresReviewedHashAndConcurrentRequestsReuseOneCross() throws Exception {
        String analysis=analyses.create(crossSource(),"admin");
        var pair=new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control","grupo");
        var preview=reportCrosses.preview(pair);
        assertThatThrownBy(()->reportCrosses.save(new bo.edu.umss.mercado.reporting.ReportCrossComposer.Save(pair,"wrong"),"admin")).isInstanceOf(ResponseStatusException.class);
        var request=new bo.edu.umss.mercado.reporting.ReportCrossComposer.Save(pair,CanonicalJson.hash(preview));
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->reportCrosses.save(request,"admin"));var second=executor.submit(()->reportCrosses.save(request,"admin"));
            assertThat(first.get().get("id")).isEqualTo(second.get().get("id"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_general_cross",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
        assertThat(reportCrosses.preview(pair).get("result_hash")).isEqualTo(preview.get("result_hash"));
    }
    @Test void namedReportCrossKeepsSourceIntactAndFreezesItsComment() throws Exception {
        String analysis=analyses.create(crossSource(),"admin");var pair=new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control","grupo");
        var pairPreview=reportCrosses.preview(pair);var saved=reportCrosses.save(new bo.edu.umss.mercado.reporting.ReportCrossComposer.Save(pair,CanonicalJson.hash(pairPreview)),"admin");String id=(String)saved.get("id");
        var initial=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of(id));
        var configuration=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,initial.title(),"",true,true,true,"",initial.questions(),initial.crossIds(),List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(id,"Nombre <script>bad()</script>","Nota <img src=x>")));
        var preview=reports.preview(configuration,"admin");String report=reports.generateCustomized(configuration,CanonicalJson.hash(preview),"admin");
        assertThat(reportContent(report).get("format")).isEqualTo("REPORTE_HTML_V4");
        assertThat(crosses.get(id)).isEqualTo(saved);
        String html=mvc.perform(get("/admin/reportes/"+report)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Nombre &lt;script&gt;","Nota &lt;img").doesNotContain("<script>bad()");
        mvc.perform(get("/admin/reportes/"+report+"/nuevo")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("crossSettings")));
        String csv=new String(bo.edu.umss.mercado.reporting.ReportExport.csv(bo.edu.umss.mercado.reporting.ReportExport.rows(reports.get(report))),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Nombre <script>bad()</script>","Nota <img src=x>");
        var modified=new LinkedHashMap<>(reportContent(report));modified.put("display_crosses",List.of());
        jdbc.update("UPDATE generated_report SET content_json=?,content_hash=? WHERE id=?",CanonicalJson.json(modified),CanonicalJson.hash(modified),report);
        assertThatThrownBy(()->reports.get(report)).isInstanceOf(IllegalStateException.class);
    }
    @Test void reportCrossHttpChecksAdminCsrfPreviewAndNamedCatalog() throws Exception {
        String analysis=analyses.create(crossSource(),"admin");var pair=new bo.edu.umss.mercado.reporting.ReportCrossComposer.Pair(analysis,"control","grupo");String body=CanonicalJson.json(pair);
        for(String path:List.of("/admin/reportes/personalizar/cruces/vista-previa","/admin/reportes/personalizar/cruces")){
            mvc.perform(post(path).contentType("application/json").content(body)).andExpect(status().isForbidden());
            mvc.perform(post(path).with(csrf()).with(user("viewer").roles("USER")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        }
        String json=mvc.perform(post("/admin/reportes/personalizar/cruces/vista-previa").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        var preview=CanonicalJson.read(json,Map.class);assertThat(preview.get("html").toString()).contains("Pares con valor");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_general_cross",Integer.class)).isZero();
        String savedJson=mvc.perform(post("/admin/reportes/personalizar/cruces").with(csrf()).contentType("application/json").content(CanonicalJson.json(Map.of("pair",pair,"expectedHash",preview.get("hash")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var saved=CanonicalJson.read(savedJson,Map.class);String id=(String)saved.get("id");
        var initial=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of(id));
        var config=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,initial.title(),"",true,true,true,"",initial.questions(),initial.crossIds(),List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(id,"Mi comparación","Mi comentario")));
        var customized=mvc.perform(post("/admin/reportes/personalizar/vista-previa").param("focusCross",id).with(csrf()).contentType("application/json").content(CanonicalJson.json(config))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(customized).contains("Mi comparación","Mi comentario");
        Path dir=Path.of(".codex-tmp/report-step4-browser");Files.createDirectories(dir);
        Files.writeString(dir.resolve("pair-preview.json"),json);Files.writeString(dir.resolve("saved-cross.json"),savedJson);Files.writeString(dir.resolve("preview.json"),customized);
        Files.writeString(dir.resolve("generator.html"),mvc.perform(get("/admin/reportes/nuevo").param("analysisId",analysis)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var defaults=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of());
        Files.writeString(dir.resolve("default-preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(defaults))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        var edited=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,initial.title(),"",true,true,true,"",initial.questions(),initial.crossIds(),List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(id,"Comparación editada","Comentario editado")));
        Files.writeString(dir.resolve("edited-preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").param("focusCross",id).with(csrf()).contentType("application/json").content(CanonicalJson.json(edited))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    bo.edu.umss.mercado.reporting.ReportCustomization.Configuration customization(String analysis) {
        var initial=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of());
        var qs=new ArrayList<>(initial.questions());
        qs.set(0,new bo.edu.umss.mercado.reporting.ReportCustomization.Question(qs.getFirst().id(),true,"Título <script>custom()</script>","Comentario <img src=x>"));
        qs.set(1,new bo.edu.umss.mercado.reporting.ReportCustomization.Question(qs.get(1).id(),false,qs.get(1).title(),""));
        return new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,"Reporte personalizado","Introducción <b>segura</b>",false,false,true,"Comentario general",qs,List.of());
    }
    @Test void customizationPreviewAndGenerationShareFrozenContentWithoutChangingOriginalFigures() throws Exception {
        String cfg=config();response("SI","0","A");String analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin");
        var custom=customization(analysis);String original=CanonicalJson.json(analyses.get(analysis));
        var preview=reports.preview(custom,"admin");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
        var shown=rows((Map<String,Object>)preview.get("display_result"),"variables");
        assertThat(shown).hasSize(2);assertThat(shown.getFirst()).containsEntry("label","Título <script>custom()</script>").containsEntry("report_tables",false);
        var source=rows((Map<String,Object>)analyses.get(analysis).get("result"),"variables");
        assertThat(shown.getFirst().get("distribution")).isEqualTo(source.getFirst().get("distribution"));
        String id=reports.generateCustomized(custom,CanonicalJson.hash(preview),"admin");
        assertThat(CanonicalJson.json(reportContent(id))).isEqualTo(CanonicalJson.json(preview));
        response("NO","3");assertThat(CanonicalJson.json(analyses.get(analysis))).isEqualTo(original);
        assertThat(CanonicalJson.json(reportContent(id))).isEqualTo(CanonicalJson.json(preview));
        String html=mvc.perform(get("/admin/reportes/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Título &lt;script&gt;","Comentario &lt;img","Introducción &lt;b&gt;","Consultar tabla o resumen equivalente").doesNotContain("<script>custom()");
        var csv=new String(mvc.perform(get("/admin/reportes/"+id+"/resultados.csv")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("Título <script>custom()</script>").doesNotContain("\"opciones\"");
        mvc.perform(get("/admin/reportes/"+id+"/nuevo")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("report-customizer.js")));
        Path dir=Path.of(".codex-tmp/custom-report-browser");Files.createDirectories(dir);
        Files.writeString(dir.resolve("generator.html"),mvc.perform(get("/admin/reportes/"+id+"/nuevo")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("report.html"),html);
        Files.writeString(dir.resolve("report.json"),mvc.perform(get("/admin/reportes/"+id+"/contenido.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(custom))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void customizationRequiresMatchingPreviewAndRejectsUnknownQuestionsAndEmptyContent() {
        String cfg=config();response("SI","0","A");String analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin");var custom=customization(analysis);
        assertThatThrownBy(()->reports.generateCustomized(custom,"not-reviewed","admin")).isInstanceOf(ResponseStatusException.class);
        var qs=new ArrayList<>(custom.questions());qs.set(0,new bo.edu.umss.mercado.reporting.ReportCustomization.Question("forged",true,"Título",""));
        var bad=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,custom.title(),"",true,true,true,"",qs,List.of());
        assertThatThrownBy(()->reports.preview(bad,"admin")).isInstanceOf(IllegalArgumentException.class);
        var none=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,custom.title(),"",true,true,true,"",custom.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),false,q.title(),q.comment())).toList(),List.of());
        assertThatThrownBy(()->reports.preview(none,"admin")).isInstanceOf(IllegalArgumentException.class);
        var hidden=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,custom.title(),"",true,false,false,"",custom.questions(),List.of());
        assertThatThrownBy(()->reports.preview(hidden,"admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
    }
    @Test void customizationIntegrityRejectsForgedDisplayAndIncompatibleCrosses() {
        String source=crossSource(),analysis=analyses.create(source,"admin");var custom=customization(analysis);
        String other=run(service.configuration(version).id(),"NO_DECLARADO");
        String cross=crosses.create(other,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        var mixed=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,custom.title(),"",true,true,true,"",custom.questions(),List.of(cross));
        assertThatThrownBy(()->reports.preview(mixed,"admin")).isInstanceOf(IllegalArgumentException.class);
        var preview=reports.preview(custom,"admin");String id=reports.generateCustomized(custom,CanonicalJson.hash(preview),"admin");
        var altered=new LinkedHashMap<>(reportContent(id));altered.put("display_result",Map.of("variables",List.of()));
        jdbc.update("UPDATE generated_report SET content_json=?,content_hash=? WHERE id=?",CanonicalJson.json(altered),CanonicalJson.hash(altered),id);
        assertThatThrownBy(()->reports.get(id)).isInstanceOf(IllegalStateException.class);
    }
    @Test void customizationHttpChecksCsrfAdminReadOnlyPreviewAndNoStore() throws Exception {
        String cfg=config();response("SI","0","A");String analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin");var custom=customization(analysis);String json=CanonicalJson.json(custom);
        for(String path:List.of("/admin/reportes/personalizar/vista-previa","/admin/reportes/personalizar/generar")){
            mvc.perform(post(path).contentType("application/json").content(json)).andExpect(status().isForbidden());
            mvc.perform(post(path).with(csrf()).with(user("viewer").roles("USER")).contentType("application/json").content(json)).andExpect(status().isForbidden());
        }
        String result=mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(json))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        var preview=CanonicalJson.read(result,Map.class);
        assertThat(preview.get("html").toString()).contains("Título &lt;script&gt;","Comentario &lt;img");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
        mvc.perform(post("/admin/reportes/personalizar/generar").with(csrf()).contentType("application/json").content(CanonicalJson.json(Map.of("configuration",custom,"expectedHash",preview.get("hash")))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.url").exists());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isEqualTo(1);
    }
    @Autowired bo.edu.umss.mercado.reporting.ReportPresentationTemplates presentationTemplates;
    @Test void optionalPresentationTemplateRebindsCrossesAndNeverCopiesComments() throws Exception {
        String source=crossSource(),analysis=analyses.create(source,"admin");
        String cross=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        var defaults=bo.edu.umss.mercado.reporting.ReportCustomization.defaults(analyses.get(analysis),List.of(cross));
        var config=new bo.edu.umss.mercado.reporting.ReportCustomization.Configuration(analysis,defaults.title(),"Introducción",true,true,true,"Comentario privado",defaults.questions().stream().map(q->new bo.edu.umss.mercado.reporting.ReportCustomization.Question(q.id(),q.included(),q.title(),"Nota privada",List.of("table","columns"))).toList(),List.of(cross),List.of(new bo.edu.umss.mercado.reporting.ReportCustomization.Cross(cross,"Mi cruce","Nota privada",List.of("table","heat"))));
        String hash=CanonicalJson.hash(reports.preview(config,"admin"));
        presentationTemplates.generate(config,hash,false,"","admin");
        assertThat(presentationTemplates.recent()).isEmpty();
        assertThatThrownBy(()->presentationTemplates.generate(config,"changed",true,"Plantilla","admin")).isInstanceOf(ResponseStatusException.class);
        assertThat(presentationTemplates.recent()).isEmpty();
        presentationTemplates.generate(config,hash,true,"","admin");
        var saved=presentationTemplates.recent().getFirst();assertThat(saved.name()).isEqualTo(config.title());
        String json=jdbc.queryForObject("SELECT definition_json FROM report_presentation_template WHERE id=?",String.class,saved.id());
        assertThat(json).doesNotContain("Nota privada","Comentario privado",analysis,cross,source,"submission_count");
        String next=run(service.configuration(version).id(),"NO_DECLARADO"),nextAnalysis=analyses.create(next,"admin");
        var applied=presentationTemplates.apply(saved.id(),nextAnalysis,"admin");
        assertThat(applied.analysisId()).isEqualTo(nextAnalysis);assertThat(applied.comment()).isEmpty();
        assertThat(applied.crossIds()).hasSize(1).doesNotContain(cross);
        assertThat(crosses.get(applied.crossIds().getFirst()).get("preparation_id")).isEqualTo(next);
        assertThat(presentationTemplates.apply(saved.id(),nextAnalysis,"admin").crossIds()).isEqualTo(applied.crossIds());
        mvc.perform(post("/admin/reportes/personalizar/plantillas/aplicar").contentType("application/json").content(CanonicalJson.json(Map.of("templateId",saved.id(),"analysisId",nextAnalysis)))).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reportes/personalizar/plantillas/aplicar").with(csrf()).contentType("application/json").content(CanonicalJson.json(Map.of("templateId",saved.id(),"analysisId",nextAnalysis)))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/admin/reportes/nuevo").param("analysisId",nextAnalysis)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Aplicar y revisar")));
        var fixture=Path.of(".codex-tmp/report-step7-browser");Files.createDirectories(fixture);
        Files.writeString(fixture.resolve("generator.html"),mvc.perform(get("/admin/reportes/nuevo").param("analysisId",nextAnalysis)).andReturn().getResponse().getContentAsString());
        Files.writeString(fixture.resolve("apply.json"),CanonicalJson.json(Map.of("configuration",applied,"crosses",applied.crossIds().stream().map(crosses::get).toList())));
        Files.writeString(fixture.resolve("preview.json"),mvc.perform(post("/admin/reportes/personalizar/vista-previa").with(csrf()).contentType("application/json").content(CanonicalJson.json(applied))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThatThrownBy(()->presentationTemplates.generate(config,hash,true,"x".repeat(201),"admin")).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("UPDATE report_presentation_template SET definition_hash='alterado' WHERE id=?",saved.id());
        assertThatThrownBy(()->presentationTemplates.apply(saved.id(),nextAnalysis,"admin")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void directReportFreezesPresentationSourcesAndExportsWithoutCreatingTemplates() throws Exception {
        String source=crossSource(),analysis=analyses.create(source,"admin");
        String cross=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        String id=reports.generateDirect(analysis,"Directo <script>unsafe()</script>",List.of(cross),"Comentario <img src=x>","admin");
        var content=reportContent(id);
        assertThat(content).containsEntry("format","REPORTE_HTML_V2").containsEntry("template_revision_id",null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_template",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_template_revision",Integer.class)).isZero();
        assertThat(((Map<?,?>)content.get("context")).get("cutoff_utc")).isEqualTo(service.bundle(source).get("cutoff_utc"));
        String frozen=CanonicalJson.json(reports.get(id));response("NO","7");
        assertThat(CanonicalJson.json(reports.get(id))).isEqualTo(frozen);
        var html=mvc.perform(get("/admin/reportes/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Directo &lt;script&gt;","Comentario &lt;img", "sin plantilla").doesNotContain("<script>unsafe()");
        Path browser=Path.of(".codex-tmp/direct-report-browser");Files.createDirectories(browser);
        Files.writeString(browser.resolve("report.html"),html);
        Files.writeString(browser.resolve("report.json"),mvc.perform(get("/admin/reportes/"+id+"/contenido.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(browser.resolve("generator.html"),mvc.perform(get("/admin/reportes/nuevo").param("analysisId",analysis).param("crossId",cross)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/admin/reportes/"+id+"/nuevo")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Directo &lt;script&gt;")));
        for(String format:List.of("csv","xlsx"))mvc.perform(get("/admin/reportes/"+id+"/resultados."+format)).andExpect(status().isOk());
        String again=reports.generateDirect(analysis,"Directo <script>unsafe()</script>",List.of(cross),"Comentario <img src=x>","other-admin");
        assertThat(reports.get(again).get("logical_hash")).isEqualTo(reports.get(id).get("logical_hash"));
    }
    @Test void directReportValidatesBeforeWritingAndDetectsAlteredPresentation() {
        String cfg=config();response("SI","0","A");String analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin");
        assertThatThrownBy(()->reports.generateDirect(analysis,"x".repeat(201),List.of(),"","admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->reports.generateDirect(analysis,"Reporte",List.of(),"x".repeat(8001),"admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
        String id=reports.generateDirect(analysis,"",List.of(),"","admin");
        var altered=new LinkedHashMap<>(reportContent(id));altered.put("template_hash","alterado");
        jdbc.update("UPDATE generated_report SET content_json=?,content_hash=? WHERE id=?",CanonicalJson.json(altered),CanonicalJson.hash(altered),id);
        assertThatThrownBy(()->reports.get(id)).isInstanceOf(IllegalStateException.class);
    }
    @Test void directReportHttpRequiresAdminAndCsrfAndPreservesValidationFields() throws Exception {
        String cfg=config();response("SI","0","A");String analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin");
        mvc.perform(post("/admin/reportes").param("analysisId",analysis)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reportes").with(csrf()).with(user("viewer").roles("USER")).param("analysisId",analysis)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reportes").with(csrf()).param("analysisId",analysis).param("title","x".repeat(201)).param("comment","Conservar comentario"))
                .andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.containsString("Conservar comentario")));
        var made=mvc.perform(post("/admin/reportes").with(csrf()).param("analysisId",analysis)).andExpect(status().is3xxRedirection()).andReturn();
        String location=made.getResponse().getRedirectedUrl();
        mvc.perform(get(location).with(anonymous())).andExpect(status().is3xxRedirection());
        mvc.perform(get(location+"/contenido.json").with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get(location)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM report_template",Integer.class)).isZero();
    }
    @Test void reportFreezesSourcesCommentAndExactTemplateRevision() {
        String source=crossSource(), analysis=analyses.create(source,"admin"), revision=templateRevision();
        String cross=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        String id=reports.generate(revision,analysis,List.of(cross),"Interpretación original","admin"); String original=CanonicalJson.json(reports.get(id));
        var r=reports.revision(revision); reports.revise(r.templateId(),1,template("Título nuevo"),"admin"); response("NO","5");
        assertThat(CanonicalJson.json(reports.get(id))).isEqualTo(original);
        assertThat(reportContent(id)).containsEntry("template_revision",1).containsEntry("comment","Interpretación original");
        assertThat(((Map<?,?>)reportContent(id).get("analysis")).get("result_hash")).isEqualTo(analyses.get(analysis).get("result_hash"));
    }
    @Test void twoReportsFromOneAnalysisHaveDifferentPresentationAndSameFigures() {
        String cfg=config(); response("SI","0","A"); String analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin"), r1=templateRevision();
        String root=reports.createTemplate(new bo.edu.umss.mercado.reporting.ReportService.Template("Otra presentación","",List.of("COMENTARIOS","DESCRIPTIVOS"),false),"admin");
        String a=reports.generate(r1,analysis,List.of(),"Uno","admin"), b=reports.generate(reports.latest(root).id(),analysis,List.of(),"Dos","admin");
        assertThat(reportContent(a).get("analysis")).isEqualTo(reportContent(b).get("analysis"));
        assertThat(reports.get(a).get("logical_hash")).isNotEqualTo(reports.get(b).get("logical_hash"));
        assertThat(rows(reportContent(b),"crosses")).isEmpty();
    }
    @Test void incompatibleCrossesDuplicateSelectionsAndHiddenCommentsAreRejectedWithoutWrites() {
        String source=crossSource(), analysis=analyses.create(source,"admin"), revision=templateRevision();
        String other=run(service.configuration(version).id(),"NO_DECLARADO"); String cross=crosses.create(other,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        assertThatThrownBy(() -> reports.generate(revision,analysis,List.of(cross),"","admin")).isInstanceOf(IllegalArgumentException.class);
        String compatible=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        assertThatThrownBy(() -> reports.generate(revision,analysis,List.of(compatible,compatible),"","admin")).isInstanceOf(IllegalArgumentException.class);
        String root=reports.createTemplate(new bo.edu.umss.mercado.reporting.ReportService.Template("Solo contexto","",List.of("CONTEXTO"),false),"admin");
        assertThatThrownBy(() -> reports.generate(reports.latest(root).id(),analysis,List.of(compatible),"","admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports.generate(reports.latest(root).id(),analysis,List.of(),"Comentario oculto","admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isZero();
    }
    @Test void templateValidationAndStaleRevisionNeverOverwriteHistory() {
        assertThatThrownBy(() -> reports.createTemplate(new bo.edu.umss.mercado.reporting.ReportService.Template("Título","",List.of("DESCRIPTIVOS","DESCRIPTIVOS"),true),"admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reports.createTemplate(new bo.edu.umss.mercado.reporting.ReportService.Template("Título","",List.of("EJECUTAR_SQL"),true),"admin")).isInstanceOf(IllegalArgumentException.class);
        String revision=templateRevision(); var before=reports.revision(revision);
        reports.revise(before.templateId(),1,template("Otra revisión"),"admin");
        assertThatThrownBy(() -> reports.revise(before.templateId(),1,template("Desactualizada"),"admin")).isInstanceOf(ResponseStatusException.class);
        assertThat(reports.revision(revision)).isEqualTo(before);
        assertThat(reports.latest(before.templateId()).revision()).isEqualTo(2);
    }
    @Test void reportRejectsAlteredContentTemplateAndSource() {
        String cfg=config(), analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin"), revision=templateRevision();
        String id=reports.generate(revision,analysis,List.of(),"","admin");
        jdbc.update("UPDATE generated_report SET content_hash = 'alterado' WHERE id = ?",id); assertThatThrownBy(() -> reports.get(id)).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE report_template_revision SET definition_hash = 'alterado' WHERE id = ?",revision); assertThatThrownBy(() -> reports.generate(revision,analysis,List.of(),"","admin")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM generated_report",Integer.class)).isEqualTo(1);
    }
    @Test void reportLogicalHashIsReproducibleForExactInputs() {
        String cfg=config(),analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin"),revision=templateRevision();
        String a=reports.generate(revision,analysis,List.of(),"Igual","admin"),b=reports.generate(revision,analysis,List.of(),"Igual","otro-admin");
        assertThat(a).isNotEqualTo(b); assertThat(reports.get(a).get("logical_hash")).isEqualTo(reports.get(b).get("logical_hash"));
    }
    @Test void concurrentTemplateEditsCreateOnlyOneSuccessor() throws Exception {
        String revision=templateRevision(),root=reports.revision(revision).templateId();
        try (var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var futures=List.of(1,2).stream().map(n -> executor.submit(() -> { start.await(); try { return reports.revise(root,1,template("Revisión concurrente"),"admin"); } catch (ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return null; } })).toList();
            start.countDown(); assertThat(futures.stream().map(f -> { try { return f.get(15,java.util.concurrent.TimeUnit.SECONDS); } catch(Exception ex) { throw new RuntimeException(ex); } }).filter(Objects::nonNull)).hasSize(1);
        }
        assertThat(reports.latest(root).revision()).isEqualTo(2);
    }
    @Test void reportRoutesRequireAdminCsrfAndEscapeText() throws Exception {
        String cfg=config(); response("NO","0"); String analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin"),revision=templateRevision();
        String id=reports.generate(revision,analysis,List.of(),"<img src=x onerror=alert(1)>\nInterpretación","admin");
        for (String path:List.of("/admin/reportes","/admin/reportes/plantillas/nueva","/admin/reportes/nuevo","/admin/reportes/"+id,"/admin/reportes/"+id+"/contenido.json")) {
            mvc.perform(get(path).with(anonymous())).andExpect(status().is3xxRedirection()); mvc.perform(get(path).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/reportes").param("analysisId",analysis).param("templateRevisionId",revision)).andExpect(status().isForbidden());
        mvc.perform(post("/admin/reportes/plantillas")).andExpect(status().isForbidden());
        var html=mvc.perform(get("/admin/reportes/"+id)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("&lt;script&gt;","&lt;img").doesNotContain("<img src=x","<script>unsafe()","privado@example.test","Organización privada ficticia");
        mvc.perform(get("/admin/reportes/"+id+"/nuevo")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;img")));
        mvc.perform(get("/admin/reportes/"+id+"/contenido.json")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void reportUiCreatesTemplateAndReportAndKeepsSectionOrder() throws Exception {
        mvc.perform(post("/admin/reportes/plantillas").with(csrf()).param("expectedRevision","0").param("title","Plantilla manual").param("section1","COMENTARIOS").param("section2","DESCRIPTIVOS")).andExpect(status().is3xxRedirection());
        var revision=reports.revisions().getFirst(); String cfg=config(),analysis=analyses.create(run(cfg,"NO_DECLARADO"),"admin");
        var made=mvc.perform(post("/admin/reportes").with(csrf()).param("analysisId",analysis).param("templateRevisionId",revision.id()).param("comment","Interpretación visible")).andExpect(status().is3xxRedirection()).andReturn();
        var html=mvc.perform(get(made.getResponse().getRedirectedUrl())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("Comentarios e interpretación")).isLessThan(html.indexOf("Resultados descriptivos"));
        assertThat(html).doesNotContain("data-report-variable", "echarts-6.0.0.min.js");
        mvc.perform(get("/admin/reportes/nuevo").param("analysisId",analysis)).andExpect(status().isOk());
        mvc.perform(get("/admin/reportes/plantillas/"+revision.templateId()+"/editar")).andExpect(status().isOk());
    }
    @Test void reportBrowserFixturesContainFrozenTablesChartsAndEditors() throws Exception {
        String source=crossSource(); response("SI","0","A","B"); source=run(service.configuration(version).id(),"NO_DECLARADO");
        String analysis=analyses.create(source,"admin"),revision=templateRevision(); String cross=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        String id=reports.generate(revision,analysis,List.of(cross),"Comentario ficticio\nSin datos personales","admin");
        Path dir=Path.of(".codex-tmp/report-browser"); Files.createDirectories(dir);
        Files.writeString(dir.resolve("report.html"),mvc.perform(get("/admin/reportes/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("report.json"),mvc.perform(get("/admin/reportes/"+id+"/contenido.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("editor.html"),mvc.perform(get("/admin/reportes/plantillas/"+reports.revision(revision).templateId()+"/editar")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("generator.html"),mvc.perform(get("/admin/reportes/nuevo").param("analysisId",analysis).param("templateRevisionId",revision)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("index.html"),mvc.perform(get("/admin/reportes")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String noChartsTemplate=reports.createTemplate(new bo.edu.umss.mercado.reporting.ReportService.Template("Reporte sin gráficos","",List.of("DESCRIPTIVOS"),false),"admin");
        String noCharts=reports.generate(reports.latest(noChartsTemplate).id(),analysis,List.of(),"","admin");
        Files.writeString(dir.resolve("no-charts.html"),mvc.perform(get("/admin/reportes/"+noCharts)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("no-charts.json"),mvc.perform(get("/admin/reportes/"+noCharts+"/contenido.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    String crossSource() {
        String next = publications.successor(version,"admin"); var draft = drafts.get(next);
        drafts.question(next,0,draft.sections().getFirst().id(),new Question("grupo","Datos","Grupo <script>bad()</script>","radio",false,List.of(new Question.Option("G","G"),new Question.Option("H","H")),null,null,0,1,"",null,null),true,"admin");
        publications.publish(next,1,"admin"); version = next;
        var metadata = new ArrayList<>(fields()); metadata.add(new GenericPreparationService.Field("grupo","NOMINAL",""));
        String cfg = service.configure(version,0,metadata,"admin");
        for (String code : List.of("SI","NO","NO_SABE")) {
            var values = new LinkedMultiValueMap<String,String>(); values.set("consent","SI"); values.set("control",code); values.set("grupo",code.equals("NO")?"H":"G");
            responses.savePublished(version,UUID.randomUUID().toString(),validator.validate(publications.get(version).content().definition(),values));
        }
        return run(cfg,"NO_DECLARADO");
    }
    @Test void frozenCrossSurvivesLiveChangesAndRejectsHashTampering() {
        String source = crossSource(); var spec = new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false);
        String id = crosses.create(source,spec,"admin"), original = CanonicalJson.json(crosses.get(id)); response("NO","0");
        assertThat(CanonicalJson.json(crosses.get(id))).isEqualTo(original);
        jdbc.update("UPDATE survey_general_cross SET spec_hash = 'alterado' WHERE id = ?",id);
        assertThatThrownBy(() -> crosses.get(id)).isInstanceOf(IllegalStateException.class);
    }
    @Test void crossRoutesProtectAccessAndCsrfAndRejectInvalidPairs() throws Exception {
        String source = crossSource();
        mvc.perform(post("/admin/cruces-generales").param("preparationId",source).param("rowQuestion","control").param("columnQuestion","grupo")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/cruces-generales").with(csrf()).param("preparationId",source).param("rowQuestion","control").param("columnQuestion","control")).andExpect(status().isBadRequest());
        var created = mvc.perform(post("/admin/cruces-generales").with(csrf()).param("preparationId",source).param("rowQuestion","control").param("columnQuestion","grupo")).andExpect(status().is3xxRedirection()).andReturn();
        String path = created.getResponse().getRedirectedUrl();
        for (String route : List.of("/admin/cruces-generales",path,path+"/resultado.json")) { mvc.perform(get(route).with(anonymous())).andExpect(status().is3xxRedirection()); mvc.perform(get(route).with(user("viewer").roles("USER"))).andExpect(status().isForbidden()); }
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;"))).andExpect(content().string(org.hamcrest.Matchers.containsString("Prueba no ejecutada")));
        mvc.perform(get(path+"/resultado.json")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void crossBrowserFixturesUseActualFrozenTableAndHeatmap() throws Exception {
        String source = crossSource(); String id = crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        Path dir = Path.of(".codex-tmp/cross-browser"); Files.createDirectories(dir);
        Files.writeString(dir.resolve("selection.html"),mvc.perform(get("/admin/cruces-generales").param("preparationId",source)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("cross.html"),mvc.perform(get("/admin/cruces-generales/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("cross.json"),mvc.perform(get("/admin/cruces-generales/"+id+"/resultado.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @SuppressWarnings("unchecked") Map<String, Object> result(String id) { return (Map<String, Object>) analyses.get(id).get("result"); }
    Map<String, Object> variable(Map<String, Object> result, String id) { return rows(result, "variables").stream().filter(v -> v.get("question_id").equals(id)).findFirst().orElseThrow(); }
    @Test void generalDescriptivesHaveKnownNumbersAndDistinctMultipleBases() {
        String cfg = config(); response("SI", "0", "A", "B"); response("SI", "10", "A"); response("NO", null); response("NO_SABE", "20");
        var result = result(analyses.create(run(cfg, "NO_DECLARADO"), "admin"));
        var months = variable(result, "meses"); assertThat(months).containsEntry("answered", 3).containsEntry("missing", 1).containsEntry("response_percent", 75.0);
        Map<?, ?> numeric = (Map<?, ?>) months.get("numeric");
        assertThat(numeric.get("mean")).isEqualTo(10.0); assertThat(numeric.get("median")).isEqualTo(10.0); assertThat(numeric.get("min")).isEqualTo(0.0); assertThat(numeric.get("sample_standard_deviation")).isEqualTo(10.0);
        var multiple = variable(result, "opciones"); assertThat(multiple).containsEntry("answered", 2).containsEntry("not_applicable", 2).containsEntry("mentions", 3);
        var a = rows(multiple, "distribution").getFirst(); assertThat(a).containsEntry("count", 2).containsEntry("respondent_base", 2).containsEntry("respondent_percent", 100.0).containsEntry("mention_base", 3);
        assertThat(((Number) a.get("mention_percent")).doubleValue()).isCloseTo(66.66666667, within(.000001));
        assertThat(rows(variable(result, "control"), "distribution").getLast()).containsEntry("code", "NO_SABE").containsEntry("count", 1).containsEntry("respondent_percent", 25.0);
    }
    @Test void emptyAndSingleValueAnalysesPreserveAbsenceAndZero() {
        String cfg = config(); var empty = result(analyses.create(run(cfg, "NO_DECLARADO"), "admin"));
        assertThat(variable(empty, "control").get("response_percent")).isNull(); assertThat(rows(variable(empty, "control"), "distribution").getFirst().get("respondent_percent")).isNull();
        response("NO", "0"); var one = variable(result(analyses.create(run(cfg, "NO_DECLARADO"), "admin")), "meses");
        var numeric = (Map<?, ?>) one.get("numeric"); assertThat(numeric.get("mean")).isEqualTo(0.0); assertThat(numeric.get("sample_standard_deviation")).isNull();
    }
    @Test void generalAnalysisUsesFrozenSourceDespiteLaterLiveChangesAndRejectsTampering() {
        String cfg = config(), a = response("NO", "0"), source = run(cfg, "NO_DECLARADO"), analysis = analyses.create(source, "admin");
        String original = CanonicalJson.json(analyses.get(analysis)); wrangling.classify(a, "REAL", "Cambio posterior", "admin"); response("NO", "90");
        assertThat(CanonicalJson.json(analyses.get(analysis))).isEqualTo(original);
        jdbc.update("UPDATE survey_general_analysis SET result_hash = 'alterado' WHERE id = ?", analysis);
        assertThatThrownBy(() -> analyses.get(analysis)).isInstanceOf(IllegalStateException.class);
    }
    @Test void generalAnalysisRoutesArePrivateAndCsrfProtected() throws Exception {
        String cfg = config(), source = run(cfg, "NO_DECLARADO");
        mvc.perform(post("/admin/analisis-general").param("preparationId", source)).andExpect(status().isForbidden());
        var created = mvc.perform(post("/admin/analisis-general").with(csrf()).param("preparationId", source)).andExpect(status().is3xxRedirection()).andReturn();
        String path = created.getResponse().getRedirectedUrl();
        for (String route : List.of("/admin/analisis-general", path, path + "/resultado.json")) {
            mvc.perform(get(route).with(anonymous())).andExpect(status().is3xxRedirection()); mvc.perform(get(route).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")));
        mvc.perform(get(path + "/resultado.json")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void generalAnalysisBrowserFixturesContainTablesAndFrozenJson() throws Exception {
        String cfg = config(); response("SI", "0", "A", "B"); response("NO_SABE", "10");
        String id = analyses.create(run(cfg, "NO_DECLARADO"), "admin"); Path dir = Path.of(".codex-tmp/analysis-browser"); Files.createDirectories(dir);
        Files.writeString(dir.resolve("analysis.html"), mvc.perform(get("/admin/analisis-general/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("analysis.json"), mvc.perform(get("/admin/analisis-general/" + id + "/resultado.json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void rejectsTextContactsUnknownScalesUnitsAndForeignQuestion() {
        var bad = new ArrayList<>(fields()); bad.set(3, new GenericPreparationService.Field("correo", "NOMINAL", ""));
        assertThatThrownBy(() -> service.configure(version, 0, bad, "admin")).isInstanceOf(IllegalArgumentException.class);
        bad.set(3, fields().get(3)); bad.set(4, new GenericPreparationService.Field("organizacion", "NOMINAL", ""));
        assertThatThrownBy(() -> service.configure(version, 0, bad, "admin")).isInstanceOf(IllegalArgumentException.class);
        bad.set(4, fields().get(4)); bad.set(2, new GenericPreparationService.Field("meses", "MAGNITUD_ENTERA", ""));
        assertThatThrownBy(() -> service.configure(version, 0, bad, "admin")).isInstanceOf(IllegalArgumentException.class);
        bad.set(2, fields().get(2)); bad.set(0, new GenericPreparationService.Field("control", "ORDINAL", ""));
        assertThatThrownBy(() -> service.configure(version, 0, bad, "admin")).isInstanceOf(IllegalArgumentException.class);
        bad.set(0, new GenericPreparationService.Field("ajena", "NOMINAL", ""));
        assertThatThrownBy(() -> service.configure(version, 0, bad, "admin")).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_config", Integer.class)).isZero();
    }
    @Test void preparationPreservesStatesZeroNominalUnknownAndSeparateMentions() {
        String cfg = config(); String a = response("SI", "0", "B", "A"); response("NO_SABE", null); response("SI", null);
        var data = logical(run(cfg, "NO_DECLARADO")); assertThat(rows(data, "matrix")).hasSize(3);
        var row = rows(data, "matrix").stream().filter(r -> r.get("response_id").equals(a)).findFirst().orElseThrow();
        assertThat(((Map<?, ?>) row.get("values")).get("meses")).isEqualTo(0);
        assertThat(((Map<?, ?>) row.get("values")).get("opciones")).isEqualTo(List.of("A", "B"));
        assertThat(rows(data, "selections")).hasSize(2);
        assertThat(rows(data, "coverage").stream().filter(q -> q.get("question_id").equals("opciones")).findFirst().orElseThrow())
                .containsEntry("total_submissions", 3).containsEntry("with_value", 1).containsEntry("without_answer", 1).containsEntry("not_applicable", 1).containsEntry("applicable_base", 2);
        assertThat(CanonicalJson.json(data)).contains("NO_SABE").doesNotContain("privado@example.test", "Organización privada ficticia");
        assertThat(captures.input(a).answers()).containsKey("correo");
    }
    @Test void originSelectionHasExactRevisionAndExcludesOtherOrigins() {
        String cfg = config(), real = response("NO", "0"), synthetic = response("NO", "1"); response("NO", null);
        wrangling.classify(real, "REAL", "Clasificación ficticia", "admin"); wrangling.classify(synthetic, "SINTETICO", "Prueba", "admin");
        var data = logical(run(cfg, "REAL")); assertThat(rows(data, "members")).hasSize(3); assertThat(rows(data, "matrix")).hasSize(1);
        var included = rows(data, "members").stream().filter(m -> m.get("response_id").equals(real)).findFirst().orElseThrow();
        assertThat(included.get("origin_revision")).isInstanceOf(Map.class); assertThat(included).containsEntry("included", true);
        assertThat(rows(data, "members").stream().filter(m -> !m.get("response_id").equals(real))).allMatch(m -> m.get("exclusion_reason").equals("ORIGEN_DISTINTO"));
    }
    @Test void savedBundleDoesNotChangeWithNewOriginConfigurationOrSubmissions() {
        String cfg = config(), a = response("NO", "0"), id = run(cfg, "NO_DECLARADO"); String before = CanonicalJson.json(service.bundle(id));
        wrangling.classify(a, "REAL", "Revisión posterior", "admin"); var next = new ArrayList<>(fields()); next.set(2, new GenericPreparationService.Field("meses", "EXCLUIR", ""));
        service.configure(version, 1, next, "admin"); response("SI", "5", "A");
        assertThat(CanonicalJson.json(service.bundle(id))).isEqualTo(before);
        assertThat(service.configuration(version).revision()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_config", Integer.class)).isEqualTo(2);
    }
    @Test void identicalInputsAndRulesGiveIdenticalLogicalHashesWithoutDeduplication() {
        String cfg = config(); response("SI", "0", "A"); response("SI", "0", "A");
        var first = service.bundle(run(cfg, "NO_DECLARADO")); var second = service.bundle(run(cfg, "NO_DECLARADO"));
        assertThat(first.get("logical_hash")).isEqualTo(second.get("logical_hash")); assertThat(first.get("run_id")).isNotEqualTo(second.get("run_id"));
        assertThat(rows((Map<String, Object>) first.get("logical"), "matrix")).hasSize(2);
    }
    @Test void cannotPrepareWithAnotherVersionConfigurationAndV1KeepsItsOwnDefinition() {
        String cfg = config(); response("NO", "3"); var v1 = publications.get(version);
        String next = publications.successor(version, "admin"); drafts.updateHeader(next, 0, "Otra estructura V2", "", "admin"); publications.publish(next, 1, "admin");
        publications.activate(next, publications.get(next).revision(), "admin");
        assertThatThrownBy(() -> service.prepare(next, cfg, "NO_DECLARADO", "BORRADOR", null, null, "admin")).isInstanceOf(IllegalArgumentException.class);
        var data = logical(run(cfg, "NO_DECLARADO")); assertThat(data).containsEntry("version_id", version).containsEntry("publication_hash", v1.hash()).containsEntry("instrument_version", "v1");
        assertThat(rows(data, "matrix")).hasSize(1);
    }
    @Test void closedVersionsCanBePreparedAndReadWithoutChangingTheirHash() {
        String cfg = config(); response("NO", "0"); var before = publications.get(version);
        publications.close(version, before.revision(), "admin"); assertThat(rows(logical(run(cfg, "NO_DECLARADO")), "matrix")).hasSize(1);
        assertThat(publications.get(version).hash()).isEqualTo(before.hash());
    }
    void time(String id, String instant) {
        var row = jdbc.queryForMap("SELECT capture_json FROM response_capture WHERE response_id = ?", id);
        var original = CanonicalJson.read((String) row.get("capture_json"), CaptureStore.Payload.class);
        var changed = new CaptureStore.Payload(id, instant, original.form());
        jdbc.update("UPDATE survey_response SET submitted_at = ? WHERE id = ?", OffsetDateTime.parse(instant), id);
        jdbc.update("UPDATE response_capture SET capture_json = ?, capture_hash = ? WHERE response_id = ?", CanonicalJson.json(changed), CanonicalJson.hash(changed), id);
    }
    @Test void datesAreInclusiveInLaPazAndDoNotUseCurrentUtcDay() {
        String cfg = config(), before = response("NO", null), start = response("NO", "0"), end = response("NO", "1"), after = response("NO", null);
        time(before, "2026-01-01T03:59:59Z"); time(start, "2026-01-01T04:00:00Z"); time(end, "2026-01-02T03:59:59Z"); time(after, "2026-01-02T04:00:00Z");
        String id = service.prepare(version, cfg, "NO_DECLARADO", "BORRADOR", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), "admin");
        assertThat(rows(logical(id), "members").stream().map(m -> m.get("response_id"))).containsExactlyInAnyOrder(start, end);
        assertThatThrownBy(() -> service.prepare(version, cfg, "NO_DECLARADO", "BORRADOR", LocalDate.of(2026, 1, 2), LocalDate.of(2026, 1, 1), "admin")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void officialRequiresRealDataAndExplicitStudyAndInstrumentApproval() {
        String cfg = config(), a = response("NO", "1"); wrangling.classify(a, "REAL", "Dato ficticio para prueba", "admin");
        assertThatThrownBy(() -> service.prepare(version, cfg, "SINTETICO", "OFICIAL", null, null, "admin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.prepare(version, cfg, "REAL", "OFICIAL", null, null, "admin")).isInstanceOf(IllegalArgumentException.class);
        jdbc.update("UPDATE survey_study SET status = 'APROBADA' WHERE id = 'umss-mercado-2026-borrador'");
        jdbc.update("UPDATE instrument_snapshot SET status = 'APROBADA' WHERE survey_slug = ?", publications.get(version).slug());
        var official = logical(service.prepare(version, cfg, "REAL", "OFICIAL", null, null, "admin"));
        assertThat(official).containsEntry("mode", "OFICIAL").containsEntry("instrument_status", "APROBADA");
        assertThat(((Map<?, ?>) official.get("study")).get("status")).isEqualTo("APROBADA");
    }
    @Test void emptyDraftHasExplicitZeroCoverageButEmptyOfficialIsRejected() {
        String cfg = config(); var data = logical(run(cfg, "NO_DECLARADO")); assertThat(rows(data, "matrix")).isEmpty();
        assertThat(rows(data, "coverage")).allMatch(q -> q.get("applicable_base").equals(0));
        assertThatThrownBy(() -> service.prepare(version, cfg, "REAL", "OFICIAL", null, null, "admin")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void corruptedConfigAndBundleCannotBeProcessedOrDownloaded() throws Exception {
        String cfg = config(); response("NO", "1"); String id = run(cfg, "NO_DECLARADO");
        jdbc.update("UPDATE survey_preparation_run SET bundle_hash = 'alterado' WHERE id = ?", id);
        assertThatThrownBy(() -> service.bundle(id)).isInstanceOf(IllegalStateException.class);
        jdbc.update("UPDATE survey_preparation_config SET config_hash = 'alterado' WHERE id = ?", cfg);
        assertThatThrownBy(() -> run(cfg, "NO_DECLARADO")).isInstanceOf(IllegalStateException.class);
    }
    @Test void alteredCapturePreventsPartialRunPublication() {
        String cfg = config(), a = response("NO", "1"); jdbc.update("UPDATE response_capture SET capture_hash = 'alterado' WHERE response_id = ?", a);
        assertThatThrownBy(() -> run(cfg, "NO_DECLARADO")).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_run", Integer.class)).isZero();
    }
    @Test void configurationCannotBeOverwrittenByStaleRevision() {
        String cfg = config(); assertThatThrownBy(() -> service.configure(version, 0, fields(), "other")).isInstanceOf(ResponseStatusException.class);
        assertThat(service.configuration(version).id()).isEqualTo(cfg);
    }
    @Test void parallelConfigurationCreatesOnlyOneRevision() throws Exception {
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = List.of(1, 2).stream().map(n -> executor.submit(() -> { start.await(); try { return config(); } catch (ResponseStatusException ex) { assertThat(ex.getStatusCode().value()).isEqualTo(409); return null; } })).toList();
            start.countDown(); assertThat(futures.stream().map(f -> { try { return f.get(15, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ex) { throw new RuntimeException(ex); } }).filter(Objects::nonNull)).hasSize(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_config", Integer.class)).isEqualTo(1);
    }
    @Test void administrativePagesAndDownloadsRequireAdminAndCsrf() throws Exception {
        String cfg = config(), id = run(cfg, "NO_DECLARADO");
        for (String path : List.of("/admin/preparacion", "/admin/preparacion/conjuntos/" + id, "/admin/preparacion/conjuntos/" + id + "/conjunto.json", "/admin/preparacion/conjuntos/" + id + "/matriz.csv")) {
            mvc.perform(get(path).with(anonymous())).andExpect(status().is3xxRedirection());
            mvc.perform(get(path).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
        }
        mvc.perform(post("/admin/preparacion/" + version + "/conjuntos").param("configId", cfg).param("origin", "NO_DECLARADO")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/preparacion/" + version + "/configuracion")).andExpect(status().isForbidden());
    }
    @Test void uiSavesConfigurationAndUsesPinnedConfigOnPrepare() throws Exception {
        var values = new LinkedMultiValueMap<String, String>(); values.set("revision", "0"); fields().forEach(f -> {values.set("treatment." + f.questionId(), f.treatment()); values.set("unit." + f.questionId(), f.unit());});
        mvc.perform(post("/admin/preparacion/" + version + "/configuracion").with(csrf()).params(values)).andExpect(status().is3xxRedirection());
        String cfg = service.configuration(version).id(); response("NO", "0");
        var result = mvc.perform(post("/admin/preparacion/" + version + "/conjuntos").with(csrf()).param("configId", cfg).param("origin", "NO_DECLARADO")).andExpect(status().is3xxRedirection()).andReturn();
        mvc.perform(get(result.getResponse().getRedirectedUrl())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("0")));
    }
    @Test void invalidFormInputsReturnUsefulErrorsAndNoWrites() throws Exception {
        mvc.perform(post("/admin/preparacion/" + version + "/configuracion").with(csrf()).param("revision", "0")).andExpect(status().isBadRequest());
        String cfg = config(); mvc.perform(post("/admin/preparacion/" + version + "/conjuntos").with(csrf()).param("configId", cfg).param("origin", "REAL").param("from", "invalida")).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM survey_preparation_run", Integer.class)).isZero();
    }
    @Test void renderedPagesEscapeUserLabelsAndJsonCsvOmitPrivateValues() throws Exception {
        String cfg = config(); response("SI", "0", "A"); String id = run(cfg, "NO_DECLARADO");
        var page = mvc.perform(get("/admin/preparacion").param("versionId", version)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(page).contains("&lt;script&gt;").doesNotContain("<script>evil()");
        var json = mvc.perform(get("/admin/preparacion/conjuntos/" + id + "/conjunto.json")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsString();
        var csv = mvc.perform(get("/admin/preparacion/conjuntos/" + id + "/matriz.csv")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json).doesNotContain("privado@example.test", "Organización privada ficticia"); assertThat(csv).contains("CON_VALOR", "\"0\"").doesNotContain("privado@example.test", "organizacion", "correo");
    }
    @Test void reportDownloadsArePrivateFrozenAndRejectAlteredSources() throws Exception {
        String source=crossSource(), analysis=analyses.create(source,"admin"),revision=templateRevision();
        String cross=crosses.create(source,new bo.edu.umss.mercado.statistics.GenericCrossService.Spec("control","grupo",.05,false,"","",false),"admin");
        String id=reports.generate(revision,analysis,List.of(cross),"=HYPERLINK(\"bad\")","admin");
        var before=new LinkedHashMap<String,byte[]>();
        for(String format:List.of("csv","xlsx")) {
            String path="/admin/reportes/"+id+"/resultados."+format;
            mvc.perform(get(path).with(anonymous())).andExpect(status().is3xxRedirection());
            mvc.perform(get(path).with(user("viewer").roles("USER"))).andExpect(status().isForbidden());
            before.put(format,mvc.perform(get(path)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment"))).andReturn().getResponse().getContentAsByteArray());
        }
        String csvText=new String(before.get("csv"),java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csvText).contains("logical_hash","cutoff_utc","NO_DECLARADO","observed","respondent_base","SIN_VALOR","'=HYPERLINK")
                .doesNotContain("privado@example.test","Organización privada ficticia");
        response("NO","120");
        for(String format:before.keySet()) assertThat(mvc.perform(get("/admin/reportes/"+id+"/resultados."+format)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).isEqualTo(before.get(format));
        jdbc.update("UPDATE generated_report SET content_hash='alterado' WHERE id=?",id);
        for(String format:before.keySet()) assertThatThrownBy(()->mvc.perform(get("/admin/reportes/"+id+"/resultados."+format)))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }
    @Test void syntheticBrowserFixturesShowActualConfigurationAndSavedDataset() throws Exception {
        String cfg = config(); response("SI", "0", "A", "B"); response("NO_SABE", null); String id = run(cfg, "NO_DECLARADO");
        Path dir = Path.of(".codex-tmp/generic-browser"); Files.createDirectories(dir);
        Files.writeString(dir.resolve("configuration.html"), mvc.perform(get("/admin/preparacion").param("versionId", version)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Files.writeString(dir.resolve("dataset.html"), mvc.perform(get("/admin/preparacion/conjuntos/" + id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}

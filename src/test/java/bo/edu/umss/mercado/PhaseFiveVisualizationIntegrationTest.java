package bo.edu.umss.mercado;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.response.SurveyResponseRepository;
import bo.edu.umss.mercado.security.AdministratorService;
import bo.edu.umss.mercado.statistics.AnalysisPlan;
import bo.edu.umss.mercado.statistics.AnalysisService;
import bo.edu.umss.mercado.survey.Question;
import bo.edu.umss.mercado.survey.SurveyCatalog;
import bo.edu.umss.mercado.survey.SurveyDefinition;
import bo.edu.umss.mercado.survey.SurveyValidator;
import bo.edu.umss.mercado.wrangling.WranglingService;
import bo.edu.umss.mercado.visualization.DashboardDataService;
import bo.edu.umss.mercado.visualization.DashboardProjection;
import bo.edu.umss.mercado.visualization.DashboardSelection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Consulta visual protegida desde análisis y conjuntos congelados; fixtures sintéticas. */
@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:phase-five-visualization;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseFiveVisualizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired SurveyCatalog catalog;
    @Autowired SurveyValidator validator;
    @Autowired SurveyResponseRepository responses;
    @Autowired WranglingService wrangling;
    @Autowired AnalysisService analyses;
    @Autowired AdministratorService administrators;
    @Autowired DashboardDataService dashboard;
    @Autowired DashboardProjection projection;

    @BeforeEach
    void clearSyntheticData() {
        jdbc.update("DELETE FROM statistical_analysis");
        jdbc.update("DELETE FROM wrangling_member");
        jdbc.update("DELETE FROM wrangling_run");
        jdbc.update("DELETE FROM wrangling_text_coding");
        jdbc.update("DELETE FROM wrangling_correction");
        jdbc.update("DELETE FROM wrangling_origin_classification");
        jdbc.update("DELETE FROM survey_question_state");
        jdbc.update("DELETE FROM survey_answer");
        jdbc.update("DELETE FROM survey_response");
        jdbc.update("DELETE FROM instrument_snapshot WHERE instrument_version LIKE 'test-phase5-%'");
        jdbc.update("DELETE FROM administrator");
        administrators.register("test-admin", "test-secret-no-real");
    }

    @AfterEach
    void releaseForeignKeysForOtherSuitesOnTheSharedPostgresDatabase() {
        jdbc.update("DELETE FROM statistical_analysis");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/estadisticas/123/visualizacion", "/admin/estadisticas/123/visualizacion.json"})
    void visualizationPageAndDataRequireAuthentication(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/admin/estadisticas/123/visualizacion", "/admin/estadisticas/123/visualizacion.json"})
    @WithMockUser(roles = "USER")
    void anAuthenticatedNonAdministratorCannotReadTheDashboard(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void anUnknownAnalysisIsNotRebuiltFromCurrentResponses() throws Exception {
        save("titulados", Map.of());
        mvc.perform(get("/admin/estadisticas/" + UUID.randomUUID() + "/visualizacion.json"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isZero();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void theDashboardPageAndJsonAreNonCacheableAndDoNotExposePrivateTextOrSubmissionIds() throws Exception {
        String marker = "DATOS-PRIVADOS-" + UUID.randomUUID();
        String response = save("empleadores", Map.of("email_contacto", List.of(marker + "@example.test"),
                "nombre_organizacion", List.of(marker), "areas_emergentes", List.of(marker)));
        String graduate = save("titulados", Map.of());
        String id = analyse();
        for (String suffix : List.of("visualizacion", "visualizacion.json")) {
            MvcResult result = mvc.perform(get(path(id, suffix))).andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
            assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                    .doesNotContain(marker, response, graduate, "test-secret-no-real", "\"response_id\"");
        }
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void laterCorrectionsAndNewResponsesCannotChangeAnExistingVisualSelection() throws Exception {
        String response = save("titulados", Map.of("edad", List.of("30")));
        String id = analyse();
        String before = json(id);
        wrangling.correct(response, "edad", List.of("45"), "Corrección posterior sintética", "reviewer");
        save("titulados", Map.of("edad", List.of("60")));
        assertThat(json(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aTamperedAnalysisCannotBeShownAsVerifiedVisualization() {
        save("titulados", Map.of());
        String id = analyse();
        jdbc.update("UPDATE statistical_analysis SET bundle_json = '{}' WHERE id = ?", id);
        assertThatThrownBy(() -> mvc.perform(get(path(id, "visualizacion.json"))))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aTamperedPreparedSourceCannotBeUsedToCalculateAFilteredDashboard() {
        save("titulados", Map.of());
        String id = analyse();
        String run = (String) object(analyses.detail(id), "manifest").get("source_run_id");
        jdbc.update("UPDATE wrangling_run SET bundle_json = '{}' WHERE id = ?", run);
        assertThatThrownBy(() -> mvc.perform(get(path(id, "visualizacion.json")).param("employment", "EMPLEADO")))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"survey", "study", "version", "employment", "organizationType", "compareBy"})
    @WithMockUser(roles = "ADMIN")
    void filtersRejectValuesOutsideTheFrozenAllowedCatalogs(String parameter) throws Exception {
        save("titulados", Map.of());
        save("empleadores", Map.of());
        String id = analyse();
        mvc.perform(get(path(id, "visualizacion.json")).param(parameter, "VALOR_NO_OFRECIDO"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM statistical_analysis", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"fecha-invalida", "2026-02-30"})
    @WithMockUser(roles = "ADMIN")
    void invalidCalendarDatesAreRejected(String date) throws Exception {
        save("titulados", Map.of());
        String id = analyse();
        mvc.perform(get(path(id, "visualizacion.json")).param("from", date))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void aReversedResponsePeriodIsRejectedRatherThanReturningAnApparentlyEmptyStudy() throws Exception {
        save("titulados", Map.of());
        mvc.perform(get(path(analyse(), "visualizacion.json")).param("from", "2026-10-20").param("to", "2026-10-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void noFilterUsesTheFrozenAnalysisAndRepeatedSelectionsHaveIdenticalHashes() throws Exception {
        save("titulados", Map.of());
        save("empleadores", Map.of());
        String id = analyse();
        Map<String, Object> first = data(id, Map.of());
        assertThat(first.get("result")).isEqualTo(analyses.detail(id).get("result"));
        assertThat(data(id, Map.of())).isEqualTo(first);
        assertThat(object(first, "context")).containsEntry("n_base", 2).containsEntry("n_selected", 2)
                .containsEntry("origin", "NO_DECLARADO").containsEntry("unit", "ENVIO")
                .containsEntry("global_familywise_control", false);
        Map<String, Object> visual = projection.project(first);
        assertThat(records(visual, "sections").stream().map(s -> s.get("id")))
                .containsExactly("V01", "V02", "V03", "V04", "V05", "V06", "V07");
        assertThat(visual).containsEntry("visual_config_version", "VISUALIZACION_V1");
        if (Boolean.getBoolean("phase5.browserArtifacts")) exportSyntheticBrowserArtifacts();
    }

    @Test
    void anEmploymentFilterChangesTheBaseAndEveryStatisticUsesOnlyItsOwnSubset() {
        save("titulados", Map.of("edad", List.of("30"), "genero", List.of("HOMBRE")));
        save("titulados", Map.of("edad", List.of("40"), "genero", List.of("MUJER")));
        save("titulados", Map.of("edad", List.of("80"), "estado_laboral", List.of("DESEMPLEADO"), "sector", List.of("NO_APLICA")));
        save("empleadores", Map.of());
        String id = analyse();
        Map<String, Object> filtered = data(id, Map.of("employment", "EMPLEADO"));
        assertThat(object(filtered, "context")).containsEntry("n_base", 4).containsEntry("n_selected", 2);
        assertThat(object(object(filtered, "context"), "applied_selection")).containsEntry("survey", "titulados");
        Map<String, Object> graduate = records(object(filtered, "result"), "strata").getFirst();
        assertThat(graduate).containsEntry("survey_slug", "titulados");
        assertCount(graduate, "n_base", 2);
        Map<String, Object> age = find(records(graduate, "numeric"), "variable", "edad");
        assertCount(age, "n", 2);
        assertThat(((Number) age.get("mean")).doubleValue()).isEqualTo(35);
        for (Map<String, Object> frequency : records(graduate, "frequencies"))
            assertCount(object(frequency, "coverage"), "n_base", 2);
        assertThat(object(filtered, "context").get("selection_hash"))
                .isNotEqualTo(object(data(id, Map.of()), "context").get("selection_hash"));
        assertThat(data(id, Map.of("employment", "EMPLEADO"))).isEqualTo(filtered);
    }

    @Test
    void anEmptyPeriodSelectionReportsNoDataInsteadOfZeroPercentages() {
        save("titulados", Map.of());
        Map<String, Object> selected = data(analyse(), Map.of("from", "2099-01-01"));
        assertThat(object(selected, "context")).containsEntry("n_selected", 0).containsEntry("date_available", true);
        assertThat(records(object(selected, "result"), "strata")).isEmpty();
        assertThat(records(projection.project(selected), "sections")).hasSize(7);
        assertThat(CanonicalJson.json(projection.project(selected))).doesNotContain("NaN", "Infinity");
    }

    @Test
    void anUnobservedGraduationYearInsideTheFrozenRangeProducesAnEmptySelection() {
        save("titulados", Map.of("anio_egreso", List.of("2020")));
        Map<String, Object> selected = data(analyse(), Map.of("year", "1999"));
        assertThat(object(selected, "context")).containsEntry("n_base", 1).containsEntry("n_selected", 0);
        assertThat(object(object(selected, "context"), "applied_selection")).containsEntry("year", "1999");
        assertThat(records(object(selected, "result"), "strata")).isEmpty();
    }

    @Test
    void historicalBundlesWithoutReceptionDatesRemainReadableAndCannotInferADateFilter() {
        save("titulados", Map.of());
        String run = wrangling.create("NO_DECLARADO", "BORRADOR", null, "reviewer");
        rewritePreparedFixture(run, bundle -> records(bundle, "members").forEach(member -> member.remove("submitted_at")));
        String sourceBefore = wrangling.bundle(run);
        String id = analyses.create(run, AnalysisPlan.defaults(), "reviewer");
        Map<String, Object> ordinary = data(id, Map.of());
        assertThat(object(ordinary, "context")).containsEntry("date_available", false).containsEntry("n_selected", 1);
        assertThatThrownBy(() -> data(id, Map.of("from", "2020-01-01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no conserva las fechas");
        assertThat(wrangling.bundle(run)).isEqualTo(sourceBefore);
    }

    @Test
    void theExactMultipleSelectionReferenceKeepsParticipantAndMentionBasesInChartAndTable() {
        List<String> a = List.of("APLICACION_PRACTICA");
        List<String> ab = List.of("APLICACION_PRACTICA", "PASANTIAS");
        List<String> ac = List.of("APLICACION_PRACTICA", "HORARIOS");
        List<String> bc = List.of("PASANTIAS", "HORARIOS");
        save("titulados", Map.of("aspectos_utiles", a));
        save("titulados", Map.of("aspectos_utiles", ab));
        save("titulados", Map.of("aspectos_utiles", ac));
        save("titulados", Map.of("aspectos_utiles", bc));
        Map<String, Object> chart = chart(projection.project(data(analyse(), Map.of())), "/multiple/aspectos_utiles");
        Map<String, Object> participants = find(records(chart, "modes"), "id", "participants");
        List<Double> values = decimals(participants.get("values"));
        assertThat(values.subList(0, 3)).containsExactly(75d, 50d, 50d);
        assertThat(values.stream().mapToDouble(Double::doubleValue).sum()).isEqualTo(175);
        List<List<Object>> rows = rows(object(chart, "table"));
        assertThat(((Number) rows.getFirst().get(2)).intValue()).isEqualTo(4);
        assertThat(((Number) rows.getFirst().get(4)).intValue()).isEqualTo(7);
        assertThat(((Number) rows.getFirst().get(5)).doubleValue()).isCloseTo(300d / 7, within(1e-12));
        assertThat(chart.get("reason")).asString().contains("más del 100 %");
    }

    @Test
    void theEmploymentReferenceHasSixOfEightValidDeclarationsAndRetainsTenBaseRows() {
        for (int i = 0; i < 4; i++) save("titulados", Map.of());
        for (int i = 0; i < 2; i++) save("titulados", Map.of("estado_laboral", List.of("EMPRENDIMIENTO")));
        for (int i = 0; i < 2; i++) save("titulados", Map.of("estado_laboral", List.of("DESEMPLEADO"), "sector", List.of("NO_APLICA")));
        List<String> absent = List.of(save("titulados", Map.of()), save("titulados", Map.of()));
        String run = wrangling.create("NO_DECLARADO", "BORRADOR", null, "reviewer");
        // Fixture analítica explícita: dos estados laborales sin respuesta, fuera del recorrido público obligatorio.
        rewritePreparedFixture(run, bundle -> {
            records(bundle, "graduates").stream().filter(row -> absent.contains(row.get("response_id"))).forEach(row -> {
                row.put("estado_laboral", null); row.put("estado_laboral__estado", "SIN_RESPUESTA");
            });
            records(bundle, "coverage").stream().filter(row -> absent.contains(row.get("response_id")) && "estado_laboral".equals(row.get("question_id")))
                    .forEach(row -> { row.put("con_value_before_quality", false); row.put("final_state", "SIN_RESPUESTA"); });
        });
        String id = analyses.create(run, AnalysisPlan.defaults(), "reviewer");
        Map<String, Object> card = chart(projection.project(data(id, Map.of())), "/OCUPACION_DECLARADA");
        assertThat(object(card, "metrics")).containsEntry("value", 75d);
        assertCount(object(card, "metrics"), "numerator", 6);
        assertCount(object(card, "metrics"), "denominator", 8);
        assertCount(object(card, "metrics"), "n_base", 10);
        assertThat(card.get("title")).isEqualTo("Ocupación declarada");
    }

    @Test
    void noSabeIsOutsideTheSubstantiveDistributionAndRadarButInsideTheCompleteTable() {
        for (int i = 0; i < 6; i++) save("empleadores", Map.of("criterio_01", List.of("TOTALMENTE_ACUERDO")));
        for (int i = 0; i < 2; i++) save("empleadores", Map.of("criterio_01", List.of("PARCIALMENTE_ACUERDO")));
        save("empleadores", Map.of("criterio_01", List.of("TOTALMENTE_DESACUERDO")));
        for (int i = 0; i < 2; i++) save("empleadores", Map.of("criterio_01", List.of("NO_SABE")));
        Map<String, Object> visual = projection.project(data(analyse(), Map.of()));
        Map<String, Object> distribution = chart(visual, "/frequency/criterio_01");
        assertThat(distribution.get("type")).isEqualTo("stacked");
        assertThat(records(distribution, "series")).hasSize(3);
        List<List<Object>> complete = rows(object(distribution, "table"));
        List<Object> noSabe = complete.stream().filter(row -> "No sabe".equals(row.getFirst())).findFirst().orElseThrow();
        assertThat(((Number) noSabe.get(1)).intValue()).isEqualTo(2);
        assertThat(((Number) noSabe.get(2)).intValue()).isEqualTo(11);
        assertThat(noSabe.get(4)).isNull();
        Map<String, Object> radar = chart(visual, "/R01");
        assertThat(radar).containsEntry("available", true);
        assertThat(((Number) values(records(radar, "series").getFirst()).getFirst()).doubleValue())
                .isCloseTo(800d / 9, within(1e-12));
        List<Object> firstAxis = rows(object(radar, "table")).getFirst();
        assertThat(((Number) firstAxis.get(2)).intValue()).isEqualTo(8);
        assertThat(((Number) firstAxis.get(3)).intValue()).isEqualTo(9);
        assertThat(((Number) firstAxis.get(4)).intValue()).isEqualTo(2);
        assertThat(((Number) firstAxis.get(5)).intValue()).isEqualTo(11);
    }

    @Test
    void anAxisWithOnlyNoSabeDisablesItsRadarPanelWithoutFillingZero() {
        save("empleadores", Map.of("criterio_03", List.of("NO_SABE")));
        Map<String, Object> visual = projection.project(data(analyse(), Map.of()));
        Map<String, Object> first = chart(visual, "/R01");
        assertThat(first).containsEntry("available", false).containsEntry("status", "SIN_DATOS");
        assertThat(records(first, "series")).isEmpty();
        assertThat(rows(object(first, "table")).get(2).get(6)).isNull();
        assertThat(chart(visual, "/R02")).containsEntry("available", true);
        Map<String, Object> distribution = chart(visual, "/frequency/criterio_03");
        assertThat(distribution).containsEntry("available", false);
        assertThat(distribution.get("reason")).asString().contains("Todas las respuestas válidas son No sabe");
    }

    @Test
    void zeroAgreementWithPositiveBasesIsACompleteValidRadarAndEveryCriterionAppearsOnce() {
        Map<String, List<String>> disagreement = new LinkedHashMap<>();
        for (int i = 1; i <= 10; i++) disagreement.put("criterio_%02d".formatted(i), List.of("TOTALMENTE_DESACUERDO"));
        save("empleadores", disagreement);
        Map<String, Object> visual = projection.project(data(analyse(), Map.of()));
        for (String panel : List.of("/R01", "/R02")) {
            Map<String, Object> radar = chart(visual, panel);
            assertThat(radar).containsEntry("available", true);
            assertThat(decimals(values(records(radar, "series").getFirst()))).containsExactly(0d, 0d, 0d, 0d, 0d);
            assertThat(records(radar, "axes")).hasSize(5).allSatisfy(axis -> assertThat(axis).containsEntry("min", 0).containsEntry("max", 100));
        }
        assertThat(allCharts(visual).stream().filter(c -> ((String) c.get("id")).contains("/frequency/criterio_"))).hasSize(10);
    }

    @Test
    void anAsymmetricHeatmapPreservesCategoryOrientationAndBackendModes() {
        int[][] counts = {{18, 12}, {6, 24}};
        String[] funding = {"RECURSOS_PROPIOS", "BECA_ESTATAL"};
        String[] levels = {"MAESTRIA", "ESPECIALIDAD"};
        for (int row = 0; row < 2; row++) for (int col = 0; col < 2; col++) for (int i = 0; i < counts[row][col]; i++)
            save("titulados", Map.of("financiamiento_futuro", List.of(funding[row]), "nivel_interes", List.of(levels[col])));
        String run = wrangling.create("NO_DECLARADO", "BORRADOR", null, "reviewer");
        String id = analyses.create(run, new AnalysisPlan(List.of("B01"), "Envío sintético independiente", "Fixture de referencia sin inferencia poblacional", false, .05), "reviewer");
        Map<String, Object> heatmap = allCharts(projection.project(data(id, Map.of()))).stream()
                .filter(c -> "heatmap".equals(c.get("type"))).findFirst().orElseThrow();
        Map<String, Object> observed = find(records(heatmap, "modes"), "id", "observed");
        List<List<Object>> matrix = matrix(observed.get("values"));
        assertThat(decimals(matrix.get(0)).subList(0, 2)).containsExactly(18d, 12d);
        assertThat(decimals(matrix.get(1)).subList(0, 2)).containsExactly(6d, 24d);
        Map<String, Object> byRow = find(records(heatmap, "modes"), "id", "row");
        assertThat(decimals(matrix(byRow.get("values")).get(0)).subList(0, 2)).containsExactly(60d, 40d);
        assertThat(decimals(matrix(byRow.get("values")).get(1)).subList(0, 2)).containsExactly(20d, 80d);
        assertThat(byRow).containsEntry("max", 100);
        assertThat(rows(object(observed, "table")).get(0).get(1)).isEqualTo(matrix.get(0).get(0));
        assertCount(heatmap, "n_pairs", 60);
        assertThat(heatmap).containsEntry("status", "CALCULADO");
        Map<String, Object> frozenContrast = records(object(analyses.detail(id), "result"), "contrasts").getFirst();
        assertThat(object(heatmap, "metrics").get("p original")).isEqualTo(object(frozenContrast, "table").get("p"));
        assertThat(object(heatmap, "metrics").get("p ajustada")).isEqualTo(frozenContrast.get("p_adjusted"));
        assertThat(object(heatmap, "metrics").get("Familia")).isEqualTo(frozenContrast.get("family_id"));
    }

    @Test
    void aZeroObservedCellAndUndefinedPercentageHaveDifferentValuesAndAnUnexecutedTestHasNoP() {
        for (String funding : List.of("RECURSOS_PROPIOS", "BECA_ESTATAL")) for (String level : List.of("MAESTRIA", "ESPECIALIDAD"))
            save("titulados", Map.of("financiamiento_futuro", List.of(funding), "nivel_interes", List.of(level)));
        String run = wrangling.create("NO_DECLARADO", "BORRADOR", null, "reviewer");
        String id = analyses.create(run, new AnalysisPlan(List.of("B01"), "Envío sintético", "Fixture numérica documentada", false, .05), "reviewer");
        Map<String, Object> heatmap = allCharts(projection.project(data(id, Map.of()))).stream()
                .filter(c -> "heatmap".equals(c.get("type")) && ((String) c.get("id")).endsWith("/B01")).findFirst().orElseThrow();
        List<List<Object>> observed = matrix(find(records(heatmap, "modes"), "id", "observed").get("values"));
        assertThat(observed.stream().flatMap(List::stream).filter(v -> v instanceof Number n && n.intValue() == 0)).isNotEmpty();
        List<List<Object>> rowPercent = matrix(find(records(heatmap, "modes"), "id", "row").get("values"));
        assertThat(rowPercent.stream().flatMap(List::stream)).containsNull();
        assertThat(object(heatmap, "metrics")).containsEntry("p original", null).containsEntry("p ajustada", null);
        assertThat(heatmap).containsEntry("status", "FRECUENCIAS_INSUFICIENTES").containsEntry("available", true);
        assertThat(heatmap.get("reason")).asString().doesNotContain("sin asociación");
        assertThat(heatmap.get("reason")).asString().contains("frecuencias esperadas insuficientes");
    }

    @Test
    void boxplotUsesTheFiveNumberBackendSummaryAndASingleValueIsNotABox() {
        for (int value : List.of(2, 4, 4, 4, 5, 5, 7, 9)) save("titulados", Map.of("meses_desempleado", List.of(Integer.toString(value))));
        Map<String, Object> box = chart(projection.project(data(analyse(), Map.of())), "/numeric/meses_desempleado");
        assertThat(box).containsEntry("available", true);
        assertThat(decimals(matrix(values(records(box, "series").getFirst())).getFirst())).containsExactly(2d, 4d, 4.5d, 5.5d, 9d);
        assertThat(box.get("reason")).asString().contains("mínimo y máximo");
        clearSyntheticData();
        save("titulados", Map.of("meses_desempleado", List.of("0")));
        Map<String, Object> single = chart(projection.project(data(analyse(), Map.of())), "/numeric/meses_desempleado");
        assertThat(single).containsEntry("available", false);
        assertThat(records(single, "series")).isEmpty();
        assertThat(rows(object(single, "table")).getFirst().get(3)).isNull();
        assertThat(((Number) rows(object(single, "table")).getFirst().get(4)).doubleValue()).isZero();
    }

    @Test
    void numericPairsHaveCompatibleUnitsAndNoIdentifiersInTheSeriesOrEquivalentTable() {
        String first = save("titulados", Map.of("meses_vida_profesional", List.of("12"), "meses_desempleado", List.of("0")));
        String second = save("titulados", Map.of("meses_vida_profesional", List.of("36"), "meses_desempleado", List.of("3")));
        Map<String, Object> visual = projection.project(data(analyse(), Map.of()));
        Map<String, Object> scatter = allCharts(visual).stream().filter(c -> "scatter".equals(c.get("type"))).findFirst().orElseThrow();
        assertThat(scatter).containsEntry("x_unit", "meses").containsEntry("y_unit", "meses");
        List<List<Object>> points = matrix(values(records(scatter, "series").getFirst()));
        assertThat(points).hasSize(2).allSatisfy(point -> assertThat(point).hasSize(2));
        assertThat(points.stream().map(PhaseFiveVisualizationIntegrationTest::decimals))
                .containsExactlyInAnyOrder(List.of(12d, 0d), List.of(36d, 3d));
        assertThat(rows(object(scatter, "table"))).isEqualTo(points);
        assertThat(CanonicalJson.json(visual)).doesNotContain(first, second, "response_id");
        Map<String, Object> grouped = chart(visual, "/numeric/meses_desempleado/estado_laboral/EMPLEADO");
        assertCount(grouped, "n", 2);
        assertCount(grouped, "n_base", 2);
        Map<String, Object> paired = chart(visual, "/x_summary");
        assertCount(paired, "n", 2);
        assertCount(paired, "n_base", 2);
    }

    @Test
    void twoEmployerGroupsKeepTheirOwnRadarBasesAndAnIncompleteGroupDisablesComparison() {
        save("empleadores", Map.of("tipo_organizacion", List.of("PUBLICO"), "criterio_01", List.of("TOTALMENTE_ACUERDO")));
        save("empleadores", Map.of("tipo_organizacion", List.of("PRIVADO"), "criterio_01", List.of("PARCIALMENTE_ACUERDO")));
        save("empleadores", Map.of("tipo_organizacion", List.of("PRIVADO"), "criterio_01", List.of("TOTALMENTE_DESACUERDO")));
        String id = analyse();
        Map<String, String> filters = Map.of("compareBy", "tipo_organizacion", "groupA", "PUBLICO", "groupB", "PRIVADO");
        Map<String, Object> compared = data(id, filters);
        assertThat(records(compared, "comparison")).hasSize(2);
        assertCount(records(compared, "comparison").get(0), "n_selected", 1);
        assertCount(records(compared, "comparison").get(1), "n_selected", 2);
        Map<String, Object> radar = chart(projection.project(compared), "/R01");
        assertThat(radar).containsEntry("available", true);
        assertThat(records(radar, "series")).hasSize(2);
        assertThat(((Number) values(records(radar, "series").get(0)).getFirst()).doubleValue()).isEqualTo(100);
        assertThat(((Number) values(records(radar, "series").get(1)).getFirst()).doubleValue()).isEqualTo(50);
        clearSyntheticData();
        save("empleadores", Map.of("tipo_organizacion", List.of("PUBLICO"), "criterio_03", List.of("NO_SABE")));
        save("empleadores", Map.of("tipo_organizacion", List.of("PRIVADO")));
        Map<String, Object> missing = chart(projection.project(data(analyse(), filters)), "/R01");
        assertThat(missing).containsEntry("available", false);
        assertThat(records(missing, "series")).isEmpty();
        assertThat(rows(object(missing, "table"))).hasSize(10);
    }

    @Test
    void syntheticOriginCannotBeHiddenOrChangedByAnAnalyticFilter() {
        String response = save("titulados", Map.of());
        wrangling.classify(response, "SINTETICO", "Fixture demostrativa", "reviewer");
        String run = wrangling.create("SINTETICO", "BORRADOR", null, "reviewer");
        String id = analyses.create(run, AnalysisPlan.defaults(), "reviewer");
        assertThat(object(data(id, Map.of("employment", "EMPLEADO")), "context"))
                .containsEntry("origin", "SINTETICO").containsEntry("mode", "BORRADOR");
    }

    @Test
    void differentVersionsKeepSeparateBasesEvenWhenTheirVariableCodesCoincide() {
        SurveyDefinition current = catalog.get("titulados");
        SurveyDefinition later = new SurveyDefinition(current.slug(), current.title(), current.description(),
                "test-phase5-other-version", current.questions());
        save(current, Map.of("edad", List.of("30")));
        save(later, Map.of("edad", List.of("60")));
        String id = analyse();
        Map<String, Object> complete = data(id, Map.of());
        assertThat(records(object(complete, "result"), "strata")).hasSize(2);
        List<Map<String, Object>> boxes = allCharts(projection.project(complete)).stream()
                .filter(chart -> ((String) chart.get("id")).endsWith("/numeric/edad")).toList();
        assertThat(boxes).hasSize(2).allSatisfy(box -> assertCount(box, "n", 1));
        Map<String, Object> selected = data(id, Map.of("version", later.version()));
        assertThat(object(selected, "context")).containsEntry("n_selected", 1).containsEntry("n_base", 2);
        Map<String, Object> selectedAge = find(records(records(object(selected, "result"), "strata").getFirst(), "numeric"), "variable", "edad");
        assertThat(selectedAge.get("mean")).isEqualTo(60d);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void dynamicHtmlLabelsAreJsonTextAndThePageDoesNotEmbedExecutableSourceMarkup() throws Exception {
        SurveyDefinition current = catalog.get("titulados");
        String markup = "<img src=x onerror=alert(1)><script>FUENTE_HTML</script>";
        List<Question> questions = current.questions().stream().map(question -> {
            if (!question.id().equals("genero")) return question;
            return new Question(question.id(), question.section(), markup, question.type(), question.required(), question.options(),
                    question.dependsOn(), question.dependsValue(), question.minSelections(), question.maxSelections(), question.hint(), question.min(), question.max());
        }).toList();
        save(new SurveyDefinition(current.slug(), current.title(), current.description(), "test-phase5-html-label", questions), Map.of());
        String id = analyse();
        Map<String, Object> visual = projection.project(data(id, Map.of()));
        assertThat(chart(visual, "/frequency/genero").get("title")).isEqualTo(markup);
        MvcResult response = mvc.perform(get(path(id, "visualizacion.json"))).andExpect(status().isOk()).andReturn();
        assertThat(response.getResponse().getContentType()).startsWith("application/json");
        MvcResult page = mvc.perform(get(path(id, "visualizacion"))).andExpect(status().isOk()).andReturn();
        assertThat(page.getResponse().getContentAsString(StandardCharsets.UTF_8)).doesNotContain(markup);
    }

    @Test
    void thematicChartsCountReviewedParticipantsOnceAndKeepRawTextOutOfTheVisualContract() {
        String marker = "TEXTO-ORIGINAL-" + UUID.randomUUID();
        String response = save("empleadores", Map.of("areas_emergentes", List.of(marker)));
        wrangling.codeText(response, "areas_emergentes", List.of("IA", "IA"), "libro-v1", "Fixture temática revisada", "reviewer");
        Map<String, Object> visual = projection.project(data(analyse(), Map.of()));
        Map<String, Object> chart = chart(visual, "/themes/areas_emergentes/libro-v1");
        List<Object> row = rows(object(chart, "table")).getFirst();
        assertThat(row.getFirst()).isEqualTo("IA");
        assertThat(((Number) row.get(1)).intValue()).isEqualTo(1);
        assertThat(((Number) row.get(2)).intValue()).isEqualTo(1);
        assertThat(((Number) row.get(3)).doubleValue()).isEqualTo(100);
        assertThat(CanonicalJson.json(visual)).doesNotContain(marker, response);
    }

    private String analyse() {
        String run = wrangling.create("NO_DECLARADO", "BORRADOR", null, "reviewer");
        return analyses.create(run, AnalysisPlan.defaults(), "reviewer");
    }

    private Map<String, Object> data(String id, Map<String, String> values) {
        return dashboard.data(id, DashboardSelection.from(values));
    }

    private void exportSyntheticBrowserArtifacts() throws Exception {
        for (int i = 0; i < 8; i++) save("titulados", Map.of("meses_vida_profesional", List.of(Integer.toString(12 + i * 6)),
                "meses_desempleado", List.of(Integer.toString(i)), "financiamiento_futuro", List.of(i < 4 ? "RECURSOS_PROPIOS" : "BECA_ESTATAL"),
                "nivel_interes", List.of(i % 2 == 0 ? "MAESTRIA" : "ESPECIALIDAD"),
                "aspectos_utiles", i % 2 == 0 ? List.of("APLICACION_PRACTICA", "PASANTIAS") : List.of("HORARIOS")));
        for (int i = 0; i < 6; i++) save("empleadores", Map.of("tipo_organizacion", List.of(i < 3 ? "PUBLICO" : "PRIVADO"),
                "criterio_01", List.of(i == 5 ? "NO_SABE" : i % 2 == 0 ? "TOTALMENTE_ACUERDO" : "PARCIALMENTE_ACUERDO")));
        String id = analyse();
        Path folder = Path.of(".codex-tmp", "phase5-browser");
        Files.createDirectories(folder);
        MvcResult page = mvc.perform(get(path(id, "visualizacion"))).andExpect(status().isOk()).andReturn();
        Files.writeString(folder.resolve("dashboard.html"), page.getResponse().getContentAsString(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("dashboard.json"), json(id), StandardCharsets.UTF_8);
    }

    private void rewritePreparedFixture(String run, Consumer<Map<String, Object>> change) {
        @SuppressWarnings("unchecked")
        Map<String, Object> bundle = CanonicalJson.read(wrangling.bundle(run), Map.class);
        change.accept(bundle);
        Map<String, Object> manifest = object(bundle, "manifest");
        Map<String, Object> logical = new LinkedHashMap<>();
        for (String field : List.of("rules_version", "origin", "mode", "instruments", "studies")) logical.put(field, manifest.get(field));
        for (String field : List.of("members", "dictionary", "graduates", "employers", "selections", "coding", "issues", "quality", "coverage", "text_reviews"))
            if (bundle.containsKey(field)) logical.put(field, bundle.get(field));
        String hash = CanonicalJson.hash(logical);
        manifest.put("logical_hash", hash);
        jdbc.update("UPDATE wrangling_run SET logical_hash = ?, bundle_hash = ?, bundle_json = ? WHERE id = ?",
                hash, CanonicalJson.hash(bundle), CanonicalJson.json(bundle), run);
    }

    private static Map<String, Object> find(List<Map<String, Object>> values, String key, Object match) {
        return values.stream().filter(value -> java.util.Objects.equals(match, value.get(key))).findFirst().orElseThrow();
    }

    private static Map<String, Object> chart(Map<String, Object> visual, String suffix) {
        return allCharts(visual).stream().filter(chart -> ((String) chart.get("id")).endsWith(suffix)).findFirst().orElseThrow();
    }

    private static List<Map<String, Object>> allCharts(Map<String, Object> visual) {
        return records(visual, "sections").stream().flatMap(section -> records(section, "charts").stream()).toList();
    }

    private static void assertCount(Map<String, Object> values, String key, int expected) {
        assertThat(((Number) values.get(key)).intValue()).as(key).isEqualTo(expected);
    }

    private static List<Double> decimals(Object values) {
        return ((List<?>) values).stream().map(value -> ((Number) value).doubleValue()).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> rows(Map<String, Object> table) {
        return (List<List<Object>>) table.get("rows");
    }

    @SuppressWarnings("unchecked")
    private static List<List<Object>> matrix(Object values) {
        return (List<List<Object>>) values;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> values(Map<String, Object> series) {
        return (List<Object>) series.get("values");
    }

    private String json(String id) throws Exception {
        return mvc.perform(get(path(id, "visualizacion.json"))).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private static String path(String id, String suffix) {
        return "/admin/estadisticas/" + id + "/" + suffix;
    }

    private String save(String slug, Map<String, List<String>> overrides) {
        return save(catalog.get(slug), overrides);
    }

    private String save(SurveyDefinition survey, Map<String, List<String>> overrides) {
        MultiValueMap<String, String> values = new LinkedMultiValueMap<>();
        for (Question question : survey.questions()) {
            if (question.dependsOn() != null && Arrays.stream(question.dependsValue().split("\\|"))
                    .noneMatch(value -> values.getOrDefault(question.dependsOn(), List.of()).contains(value))) continue;
            if (overrides.containsKey(question.id())) {
                values.put(question.id(), overrides.get(question.id()));
                continue;
            }
            if (!question.options().isEmpty()) {
                int selections = question.type().equals("checkbox") ? Math.max(1, question.minSelections()) : 1;
                question.options().stream().limit(selections).forEach(option -> values.add(question.id(), option.value()));
            } else {
                values.set(question.id(), switch (question.type()) {
                    case "number" -> switch (question.id()) {
                        case "anio_egreso" -> "2020";
                        case "edad" -> "30";
                        case "meses_vida_profesional" -> "120";
                        case "meses_desempleado" -> "24";
                        default -> "1";
                    };
                    case "email" -> "test@example.test";
                    case "url" -> "https://example.test/profile";
                    case "tel" -> "+59170000000";
                    default -> "Respuesta de prueba";
                });
            }
        }
        values.set("consent", "SI");
        var validated = validator.validate(survey, values);
        assertThat(validated.errors()).isEmpty();
        return responses.save(survey, UUID.randomUUID().toString(), validated);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> data, String key) {
        return (Map<String, Object>) data.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> records(Map<String, Object> data, String key) {
        return (List<Map<String, Object>>) data.get(key);
    }
}

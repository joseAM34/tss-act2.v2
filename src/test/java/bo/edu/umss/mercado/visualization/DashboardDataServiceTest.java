package bo.edu.umss.mercado.visualization;

import bo.edu.umss.mercado.data.CanonicalJson;
import bo.edu.umss.mercado.statistics.AnalysisPlan;
import bo.edu.umss.mercado.statistics.AnalysisService;
import bo.edu.umss.mercado.statistics.StatisticalEngine;
import bo.edu.umss.mercado.wrangling.WranglingService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardDataServiceTest {
    private final AnalysisService analyses = mock(AnalysisService.class);
    private final WranglingService wrangling = mock(WranglingService.class);
    private final StatisticalEngine engine = spy(new StatisticalEngine());
    private final DashboardDataService service = new DashboardDataService(analyses, wrangling, engine);

    @Test
    void unfilteredViewUsesTheExactSavedResultAndStableSelectionIdentity() {
        Map<String, Object> source = fixture(true);
        Map<String, Object> analysis = register(source);
        Map<String, Object> first = service.data("analysis", DashboardSelection.none());
        Map<String, Object> second = service.data("analysis", DashboardSelection.none());
        assertThat(first.get("result")).isSameAs(analysis.get("result"));
        assertThat(first).isEqualTo(second);
        assertThat(object(first.get("context"))).containsEntry("origin", "NO_DECLARADO").containsEntry("n_base", 4);
        assertThat(object(object(first.get("context")).get("applied_selection"))).doesNotContainKey("empty");
        verify(engine, never()).analyze(any(), any());
    }

    @Test
    void inclusivePeriodUsesBoliviaMidnightsRatherThanUtcCalendarDates() {
        register(fixture(true));
        Map<String, Object> data = service.data("analysis", DashboardSelection.from(Map.of("from", "2026-10-13", "to", "2026-10-13")));
        assertThat(object(data.get("context"))).containsEntry("n_selected", 2).containsEntry("timezone", "America/La_Paz");
        Map<String, Object> stratum = list(object(data.get("result")), "strata").getFirst();
        Map<String, Object> numeric = list(stratum, "numeric").getFirst();
        assertThat(numeric).containsEntry("n", 2).containsEntry("mean", 25.0).containsEntry("min", 20.0).containsEntry("max", 30.0);
    }

    @Test
    void anOldBundleRemainsViewableButCannotInventAReceptionPeriod() {
        register(fixture(false));
        assertThat(object(service.data("analysis", DashboardSelection.none()).get("context"))).containsEntry("date_available", false);
        assertThatThrownBy(() -> service.data("analysis", DashboardSelection.from(Map.of("from", "2026-10-13"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Prepare un conjunto nuevo");
    }

    @Test
    void categoricalSelectionUsesValidStatesAndTheFrozenCatalog() {
        Map<String, Object> source = fixture(true);
        list(source, "graduates").getFirst().put("estado_laboral__estado", "VALOR_INVALIDO");
        register(source);
        Map<String, Object> selected = service.data("analysis", DashboardSelection.from(Map.of("employment", "EMPLEADO")));
        assertThat(object(selected.get("context"))).containsEntry("n_selected", 2);
        assertThatThrownBy(() -> service.data("analysis", DashboardSelection.from(Map.of("employment", "LIBRE"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("catálogo congelado");
    }

    @Test
    void changingTheSourceEvenToAnotherValidBundleIsRejectedByTheRecordedAnalysisHash() {
        Map<String, Object> source = fixture(true);
        register(source);
        list(source, "graduates").getFirst().put("edad", 99);
        when(wrangling.bundle("run")).thenReturn(CanonicalJson.json(source));
        assertThatThrownBy(() -> service.data("analysis", DashboardSelection.none()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("huellas de origen");
    }

    @Test
    void comparingTwoGroupsRetainsTheirSeparateCountsAndFullPlanFamilies() {
        register(fixture(true));
        DashboardSelection selection = DashboardSelection.from(Map.of("compareBy", "estado_laboral", "groupA", "EMPLEADO", "groupB", "DESEMPLEADO"));
        Map<String, Object> data = service.data("analysis", selection);
        List<Map<String, Object>> groups = list(data, "comparison");
        assertThat(groups).hasSize(2);
        assertThat(groups.getFirst()).containsEntry("code", "EMPLEADO").containsEntry("n_selected", 3);
        assertThat(groups.getLast()).containsEntry("code", "DESEMPLEADO").containsEntry("n_selected", 1);
        assertThat(object(groups.getFirst().get("result"))).containsEntry("protocol_version", AnalysisPlan.PROTOCOL_VERSION);
        assertThat(object(data.get("context"))).containsEntry("global_familywise_control", false);
    }

    private Map<String, Object> register(Map<String, Object> source) {
        Map<String, Object> manifest = map("source_run_id", "run", "source_logical_hash", "source-hash",
                "source_bundle_hash", CanonicalJson.hash(source), "logical_hash", "analysis-hash",
                "protocol_version", AnalysisPlan.PROTOCOL_VERSION, "source_origin", "NO_DECLARADO", "source_mode", "BORRADOR",
                "source_cutoff_utc", "2026-10-15T00:00:00Z", "created_at", "2026-10-15T00:00:00Z");
        AnalysisPlan plan = new AnalysisPlan(List.of("B08"), "", "", false, .05);
        Map<String, Object> analysis = map("manifest", manifest, "plan", plan, "result", map("saved", true));
        when(analyses.detail("analysis")).thenReturn(analysis);
        when(wrangling.bundle("run")).thenReturn(CanonicalJson.json(source));
        return analysis;
    }

    private static Map<String, Object> fixture(boolean dates) {
        var rows = new ArrayList<Map<String, Object>>();
        var members = new ArrayList<Map<String, Object>>();
        List<String> submitted = List.of("2026-10-13T03:59:59Z", "2026-10-13T04:00:00Z", "2026-10-14T03:59:59Z", "2026-10-14T04:00:00Z");
        for (int i = 0; i < 4; i++) {
            String id = "response-" + i;
            rows.add(map("response_id", id, "instrument_version", "frozen-v1", "study_id", "study", "origin", "NO_DECLARADO",
                    "edad", 10 + i * 10, "edad__estado", "CON_VALOR", "estado_laboral", i == 3 ? "DESEMPLEADO" : "EMPLEADO", "estado_laboral__estado", "CON_VALOR"));
            Map<String, Object> member = map("response_id", id, "included", true);
            if (dates) member.put("submitted_at", submitted.get(i));
            members.add(member);
        }
        Map<String, Object> age = map("survey_slug", "titulados", "instrument_version", "frozen-v1", "variable", "edad",
                "source_id", "T01", "label", "Edad", "type", "ENTERO", "unit", "AÑOS_COMPLETOS", "privacy", "ANALITICA", "included_in_matrix", true, "catalog", List.of());
        Map<String, Object> employment = map("survey_slug", "titulados", "instrument_version", "frozen-v1", "variable", "estado_laboral",
                "source_id", "T14", "label", "Situación laboral", "type", "NOMINAL", "privacy", "ANALITICA", "included_in_matrix", true,
                "catalog", List.of(map("code", "EMPLEADO", "label", "Trabaja"), map("code", "DESEMPLEADO", "label", "No trabaja")));
        return map("manifest", map("run_id", "run", "logical_hash", "source-hash", "studies", List.of(map("id", "study", "name", "Estudio"))),
                "graduates", rows, "employers", List.of(), "dictionary", List.of(age, employment), "members", members,
                "coding", List.of(), "issues", List.of(), "quality", List.of(), "selections", List.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) { return (Map<String, Object>) value; }
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> value, String key) { return (List<Map<String, Object>>) value.get(key); }
    private static Map<String, Object> map(Object... entries) {
        var result = new LinkedHashMap<String, Object>();
        for (int i = 0; i < entries.length; i += 2) result.put(entries[i].toString(), entries[i + 1]);
        return result;
    }
}

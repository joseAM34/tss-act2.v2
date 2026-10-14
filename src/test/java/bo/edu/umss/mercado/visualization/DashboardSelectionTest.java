package bo.edu.umss.mercado.visualization;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardSelectionTest {
    @Test
    void anEmptySelectionDoesNotInventScope() {
        DashboardSelection selection = DashboardSelection.none();
        assertThat(selection.isEmpty()).isTrue();
        assertThat(selection.survey()).isEmpty();
        assertThat(selection.from()).isEmpty();
    }

    @Test
    void questionnaireFiltersMakeTheirEffectiveScopeExplicit() {
        DashboardSelection selection = DashboardSelection.from(Map.of("employment", " EMPLEADO ", "year", "2020"));
        assertThat(selection.survey()).isEqualTo("titulados");
        assertThat(selection.employment()).isEqualTo("EMPLEADO");
        assertThat(selection.year()).isEqualTo("2020");
        assertThat(DashboardSelection.from(Map.of("organizationSize", "MICRO")).survey()).isEqualTo("empleadores");
    }

    @Test
    void filtersForDifferentQuestionnairesCannotProduceAnAmbiguousSelection() {
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("employment", "EMPLEADO", "organizationType", "PRIVADO")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("selecciones separadas");
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("survey", "empleadores", "year", "2020")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no corresponden");
    }

    @Test
    void datesRequireExistingIsoCalendarDaysAndOrderedBounds() {
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("from", "2026-02-30")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AAAA-MM-DD");
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("to", "+999999999-12-31")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("AAAA-MM-DD");
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("from", "2026-10-14", "to", "2026-10-13")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("posterior");
        assertThat(DashboardSelection.from(Map.of("from", "2026-10-13", "to", "2026-10-13")).from()).isEqualTo("2026-10-13");
    }

    @Test
    void comparisonsRequireTwoDistinctApprovedCategoriesAndTheKnownVariable() {
        DashboardSelection selection = DashboardSelection.from(Map.of("compareBy", "sector_laboral", "groupA", "SALUD", "groupB", "EDUCACION"));
        assertThat(selection.comparisonVariable()).isEqualTo("sector");
        assertThat(selection.survey()).isEqualTo("titulados");
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("compareBy", "correo", "groupA", "a", "groupB", "b")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no está permitida");
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("compareBy", "tipo_organizacion", "groupA", "PRIVADO", "groupB", "PRIVADO")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("distintas");
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("groupA", "PRIVADO")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("variable");
    }

    @Test
    void noFreeColumnNameOrContactFilterIsAccepted() {
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("contacto", "persona@example.test")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no está permitido");
        assertThatThrownBy(() -> DashboardSelection.from(Map.of("year", "2020.5")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entero");
    }
}

package bo.edu.umss.mercado.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OfficialSurveyTemplatesTest {
    private final OfficialSurveyTemplates templates = new OfficialSurveyTemplates();

    @Test
    void sourceStructuresKeepTheirActualFieldsWithoutReconstructingUncollectedQuestions() {
        var graduates = templates.get("titulados-oficial");
        var employers = templates.get("empleadores-oficial");
        assertThat(templates.all()).hasSize(2);
        assertThat(graduates.questions()).hasSize(49);
        assertThat(employers.questions()).hasSize(30);
        assertThat(graduates.questions()).extracting(Question::id)
                .doesNotContain("anio_egreso", "meses_vida_profesional", "meses_desempleado", "competencia_tecnica_01");
        assertThat(employers.questions()).extracting(Question::id)
                .doesNotContain("email_contacto", "otro_cargo");
        assertThat(question(graduates, "edad").type()).isEqualTo("select");
        assertThat(question(graduates, "edad").options()).extracting(Question.Option::label)
                .containsExactly("19 - 22 años", "23 - 26 años", "27 - 30 años", "31 - 34 años", "35 años o más");
        assertThat(question(graduates, "anios_vida_profesional").type()).isEqualTo("text");
        assertThat(question(graduates, "tiempo_desempleado").type()).isEqualTo("text");
        assertThat(question(graduates, "remuneracion_mensual").type()).isEqualTo("select");
        assertThat(question(graduates, "area_organizacion").type()).isEqualTo("checkbox");
        assertThat(question(graduates, "asignaturas_ventaja").maxSelections()).isEqualTo(5);
        assertThat(question(graduates, "asignaturas_poca_utilidad").maxSelections()).isEqualTo(5);
        assertThat(question(graduates, "otra_asignatura_poca_utilidad").type()).isEqualTo("textarea");
        var cargos = question(employers, "cargos_desempenados");
        assertThat(cargos.options()).contains(new Question.Option("DOCENTES", "Docentes"));
        assertThat(cargos.maxSelections()).isEqualTo(cargos.options().size());
        assertThat(employers.reviewNotes().get(cargos.id())).contains("categoría observada", "no acredita");
    }

    @Test
    void allEighteenEmployerCriteriaAreUniqueAndKeepNoSabeWithoutChangingTheFirstTen() {
        var official = templates.get("empleadores-oficial");
        var criteria = official.questions().stream().filter(q -> q.id().startsWith("criterio_")).toList();
        assertThat(criteria).hasSize(18);
        assertThat(criteria).extracting(Question::label).doesNotHaveDuplicates();
        var old = new SurveyCatalog().get("empleadores");
        for (int index = 1; index <= 18; index++) {
            var current = question(official, "criterio_%02d".formatted(index));
            assertThat(current.options()).extracting(Question.Option::value).containsExactly(
                    "TOTALMENTE_ACUERDO", "PARCIALMENTE_ACUERDO", "TOTALMENTE_DESACUERDO", "NO_SABE");
            if (index <= 10) {
                var initial = old.questions().stream().filter(q -> q.id().equals(current.id())).findFirst().orElseThrow();
                assertThat(current.label()).isEqualTo(initial.label());
                assertThat(current.options()).isEqualTo(initial.options());
            }
        }
    }

    @Test
    void reconstructedDefinitionsAreEditableAndDoNotPretendToKnowOriginalRequiredFieldsOrSkips() {
        for (var template : templates.all()) {
            assertThat(template.version()).isEqualTo(template.key().equals("empleadores-oficial")
                    ? "2026-10-fuente-oficial-2" : "2026-10-fuente-oficial-1");
            assertThat(template.reviewNotes()).hasSize(template.questions().size());
            assertThat(template.questions()).extracting(Question::id).doesNotHaveDuplicates();
            for (var question : template.questions()) {
                assertThat(question.required()).isFalse();
                assertThat(question.dependsOn()).isNull();
                assertThat(question.dependsValue()).isNull();
                assertThat(template.reviewNotes().get(question.id())).contains("columna", "Revise obligatoriedad y saltos");
                assertThat(question.min()).isNull();
                assertThat(question.max()).isNull();
                assertThat(question.options()).extracting(Question.Option::value).doesNotHaveDuplicates();
                assertThat(question.options()).allSatisfy(option -> {
                    assertThat(option.value()).matches("[A-Za-z][A-Za-z0-9_]{0,99}");
                    assertThat(option.label()).isNotBlank().hasSizeLessThanOrEqualTo(1000);
                });
                boolean selection = Set.of("select", "radio", "checkbox").contains(question.type());
                assertThat(!question.options().isEmpty()).isEqualTo(selection);
                assertThat(question.minSelections()).isZero();
                if (question.type().equals("checkbox")) {
                    assertThat(question.maxSelections()).isBetween(1, question.options().size());
                } else {
                    assertThat(question.maxSelections()).isEqualTo(1);
                }
            }
        }
        assertThat(templates.get("titulados-oficial").reviewNotes().get("concordancia_formacion"))
                .contains("encabezado exportado está incompleto");
    }

    @Test
    void recipesAreImmutableAndCannotBeConfusedWithAnUnknownSource() {
        assertThatThrownBy(() -> templates.all().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> templates.get("titulados-oficial").questions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> templates.get("titulados-oficial").questions().getFirst().options().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> templates.get("inexistente")).isInstanceOf(ResponseStatusException.class);
        var mutable = new java.util.ArrayList<>(templates.get("titulados-oficial").questions());
        var independent = new OfficialSurveyTemplates.Template("copy", "Copy", "Editable", "1", mutable);
        mutable.clear();
        assertThat(independent.questions()).hasSize(49);
    }

    private static Question question(OfficialSurveyTemplates.Template template, String id) {
        return template.questions().stream().filter(question -> question.id().equals(id)).findFirst().orElseThrow();
    }
}

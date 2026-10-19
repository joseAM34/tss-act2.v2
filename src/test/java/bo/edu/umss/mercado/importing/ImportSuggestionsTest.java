package bo.edu.umss.mercado.importing;

import bo.edu.umss.mercado.survey.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ImportSuggestionsTest {
    Question q(String id,String label,String type,List<Question.Option> options) {return new Question(id,"Datos",label,type,true,options,null,null,0,5,"",null,null);}
    Question choices() {return q("opciones","Selección","checkbox",List.of(new Question.Option("A","Rojo, oscuro"),new Question.Option("B","Azul"),new Question.Option("C","Verde")));}
    SurveyDefinition definition(Question... questions) {return new SurveyDefinition("prueba","Prueba","","v1",List.of(questions));}
    @Test void proposesCodesOrWholeLabelsWithAccentsWithoutApprovingThem() {
        var p=ImportSuggestions.propose(definition(q("profesion","Profesión actual","text",List.of())),List.of(List.of("PROFESIÓN ACTUAL","Consentimiento"),List.of("Texto","Sí")));
        assertThat(p.matched()).isEqualTo(1);assertThat(p.mapping().columns().getFirst().index()).isZero();
        assertThat(p.mapping().consentColumn()).isEqualTo(1);assertThat(p.mapping().consentValue()).isEqualTo("Sí");
        assertThat(p.mapping().compatible()).isFalse();assertThat(p.mapping().consentEvidence()).isEmpty();
    }
    @Test void duplicateHeadersAndCrossFieldCollisionsRemainPending() {
        var p=ImportSuggestions.propose(definition(q("q","Dato","text",List.of())),List.of(List.of("Dato","DATO"),List.of("A","B")));
        assertThat(p.pending()).isEqualTo(1);
        var collision=ImportSuggestions.propose(definition(q("uno","Dato","text",List.of()),q("dos","Dato","text",List.of())),List.of(List.of("Dato"),List.of("A")));
        assertThat(collision.pending()).isEqualTo(2);
    }
    @Test void fullHeaderMatchDoesNotUseSubstringOrInferOtherProviderMetadata() {
        var p=ImportSuggestions.propose(definition(q("edad","Edad","number",List.of())),List.of(List.of("Edad de la organización","Timestamp"),List.of("12","ayer")));
        assertThat(p.pending()).isEqualTo(1);assertThat(p.mapping().dateColumn()).isEqualTo(-1);
    }
    @Test void ambiguousDatesAreKeptUninterpretedAndReported() {
        var p=ImportSuggestions.propose(definition(),List.of(List.of("fecha_externa"),List.of("15/10/26")));
        assertThat(p.mapping().dateColumn()).isEqualTo(-1);assertThat(p.notices()).anyMatch(s->s.contains("fecha externa"));
    }
    @Test void detectsCsvDelimiterAndRejectsEquallyPlausibleInterpretations() {
        assertThat(CsvFile.detectDelimiter("a;b\n1;2\n".getBytes())).isEqualTo("PUNTO_COMA");
        assertThat(CsvFile.detectDelimiter("a\tb\n1\t2\n".getBytes())).isEqualTo("TAB");
        assertThatThrownBy(()->CsvFile.detectDelimiter("a,b;c\n1,2;3\n".getBytes())).hasMessageContaining("ambiguo");
        assertThatThrownBy(()->CsvFile.detectDelimiter("a;b,c,d\n1;x,y,z\n".getBytes())).hasMessageContaining("ambiguo");
    }
    @Test void quotesContainingDelimitersAndOneColumnRemainReadable() {
        assertThat(CsvFile.detectDelimiter("a,b\n\"x;y\",z\n".getBytes())).isEqualTo("COMA");
        assertThat(CsvFile.detectDelimiter("a\nx\n".getBytes())).isEqualTo("COMA");
    }
    @Test void mixedLabelsAndCodesResolveWithoutTurningNoSabeIntoMissing() {
        var q=q("filtro","Filtro","radio",List.of(new Question.Option("SI","Sí"),new Question.Option("NO_SABE","No sabe")));
        assertThat(ImportSuggestions.options(q," sí ","AUTO","AUTO")).containsExactly("SI");
        assertThat(ImportSuggestions.options(q,"No sabe","AUTO","AUTO")).containsExactly("NO_SABE");
    }
    @Test void completeOptionWithCommaIsNotSplit() {
        assertThat(ImportSuggestions.options(choices(),"Rojo, oscuro","AUTO","AUTO")).containsExactly("A");
    }
    @Test void recognizesUniqueMultipleInterpretationsAndRejectsUnknownValues() {
        assertThat(ImportSuggestions.options(choices(),"Azul | Verde","AUTO","AUTO")).containsExactly("B","C");
        assertThat(ImportSuggestions.options(choices(),"B;C","AUTO","AUTO")).containsExactly("B","C");
        assertThatThrownBy(()->ImportSuggestions.options(choices(),"Azul|Desconocido","AUTO","AUTO")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void codeLabelCollisionsAreReportedAndExactModeResolvesThem() {
        var q=q("q","Q","radio",List.of(new Question.Option("A","B"),new Question.Option("B","Otro")));
        assertThatThrownBy(()->ImportSuggestions.options(q,"B","AUTO","AUTO")).hasMessageContaining("ambigua");
        assertThat(ImportSuggestions.options(q,"B","CODIGOS","AUTO")).containsExactly("B");
    }
    @Test void punctuationOnlyCannotBeMistakenForAnOption() {
        var q=q("q","Q","radio",List.of(new Question.Option("A","?")));
        assertThatThrownBy(()->ImportSuggestions.options(q,"!","AUTO","AUTO")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void optionRecognitionPreservesSignsAndOperators() {
        var q=q("q","Q","radio",List.of(new Question.Option("NEGATIVO","-1"),new Question.Option("MENOR","<5"),new Question.Option("CPP","C++")));
        for(String value:List.of("1","5","C"))assertThatThrownBy(()->ImportSuggestions.options(q,value,"AUTO","AUTO")).isInstanceOf(IllegalArgumentException.class);
        assertThat(ImportSuggestions.options(q,"-1","AUTO","AUTO")).containsExactly("NEGATIVO");
    }
    @Test void ambiguousMultipleSeparatorsDoNotChooseAnInterpretation() {
        var q=q("q","Q","checkbox",List.of(new Question.Option("A","a"),new Question.Option("BC","b;c"),new Question.Option("AB","a|b"),new Question.Option("C","c")));
        assertThatThrownBy(()->ImportSuggestions.options(q,"a|b;c","AUTO","AUTO")).hasMessageContaining("única");
    }
    @Test void explicitJsonMultiplePreservesLabelsContainingDelimitersAndRejectsNonTextOrMalformedLists() {
        assertThat(ImportSuggestions.options(choices(),"[\"Rojo, oscuro\",\"Azul\"]","AUTO","JSON")).containsExactly("A","B");
        assertThat(ImportSuggestions.options(choices(),"[]","AUTO","JSON")).isEmpty();
        for(String value:List.of("[1]","[null]","{}","[\"Azul\"", "null"))
            assertThatThrownBy(()->ImportSuggestions.options(choices(),value,"AUTO","JSON")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ImportSuggestions.options(choices(),"[\"Azul\"]","AUTO","AUTO")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void newMixedModeSupportsJsonAndOrdinaryMultiCellsWithoutChangingOldAuto() {
        assertThat(ImportSuggestions.options(choices(),"[\"Azul\",\"Verde\"]","AUTO","MIXTO_V1")).containsExactly("B","C");
        assertThat(ImportSuggestions.options(choices(),"Azul;Verde","AUTO","MIXTO_V1")).containsExactly("B","C");
        assertThat(ImportSuggestions.options(choices(),"Rojo, oscuro","AUTO","MIXTO_V1")).containsExactly("A");
        assertThatThrownBy(()->ImportSuggestions.options(choices(),"[\"Azul\"","AUTO","MIXTO_V1")).isInstanceOf(IllegalArgumentException.class);
        var p=ImportSuggestions.propose(definition(choices()),List.of(List.of("opciones"),List.of("[\"Azul\"]"),List.of("Verde")));
        assertThat(p.mapping().multipleSeparator()).isEqualTo("MIXTO_V1");assertThat(p.mapping().consentColumn()).isEqualTo(-1);
        assertThat(p.mapping().consentEvidence()).isEmpty();assertThat(p.mapping().compatible()).isFalse();
    }
    @Test void recognizesOnlyWholeBracketWrapperNotAPartialMatrixHeading() {
        var question=q("criterio","Comunicación","radio",List.of(new Question.Option("SI","Sí")));
        var p=ImportSuggestions.propose(definition(question),List.of(List.of(" [Comunicación] "),List.of("Sí")));
        assertThat(p.matched()).isEqualTo(1);
        var partial=ImportSuggestions.propose(definition(question),List.of(List.of("Opine sobre [Comunicación]"),List.of("Sí")));
        assertThat(partial.pending()).isEqualTo(1);
        var duplicate=ImportSuggestions.propose(definition(question),List.of(List.of("[Comunicación]","Comunicación"),List.of("Sí","Sí")));
        assertThat(duplicate.pending()).isEqualTo(1);
    }
    @Test void mixedFormsModeAcceptsOneTrailingTerminatorAndNbspWhilePreservingOldAuto() {
        var q=q("cursos","Cursos","checkbox",List.of(new Question.Option("A","Análisis de datos"),new Question.Option("B","Redes y telecomunicaciones")));
        String raw="Análisis\u00a0de datos;Redes y\u00a0telecomunicaciones;";
        assertThat(ImportSuggestions.options(q,raw,"AUTO","MIXTO_V1")).containsExactly("A","B");
        assertThat(ImportSuggestions.options(q,"Análisis de datos;","AUTO","MIXTO_V1")).containsExactly("A");
        assertThat(ImportSuggestions.options(q,"[\"Análisis\u00a0de datos\"]","AUTO","MIXTO_V1")).containsExactly("A");
        assertThatThrownBy(()->ImportSuggestions.options(q,raw,"AUTO","AUTO")).isInstanceOf(IllegalArgumentException.class);
        for(String malformed:List.of("Análisis de datos;;Redes y telecomunicaciones;","Análisis de datos;;"))
            assertThatThrownBy(()->ImportSuggestions.options(q,malformed,"AUTO","MIXTO_V1")).isInstanceOf(IllegalArgumentException.class);
        var proposal=ImportSuggestions.propose(definition(q),List.of(List.of("Cursos"),List.of(raw)));
        assertThat(proposal.mapping().multipleSeparator()).isEqualTo("MIXTO_V1");
    }
    @Test void mixedEmployerListsSplitOnlyCommasOutsideBalancedParentheses() {
        var q=q("areas","Áreas","checkbox",List.of(new Question.Option("A","Desarrollo (web, móvil)"),new Question.Option("B","Redes (instalación, soporte (LAN, WAN))"),new Question.Option("C","Gestión")));
        assertThat(ImportSuggestions.options(q,"Desarrollo (web, móvil), Redes (instalación, soporte (LAN, WAN)), Gestión","AUTO","MIXTO_V1")).containsExactly("A","B","C");
        assertThat(ImportSuggestions.options(q,"Desarrollo (web, móvil)","AUTO","MIXTO_V1")).containsExactly("A");
        assertThatThrownBy(()->ImportSuggestions.options(q,"Desarrollo (web, móvil), Gestión","AUTO","AUTO")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->ImportSuggestions.options(q,"Desarrollo (web, móvil, Gestión","AUTO","MIXTO_V1")).isInstanceOf(IllegalArgumentException.class);
        var proposal=ImportSuggestions.propose(definition(q),List.of(List.of("Áreas"),List.of("Desarrollo (web, móvil), Gestión")));
        assertThat(proposal.mapping().multipleSeparator()).isEqualTo("MIXTO_V1");
    }
    @Test void missingCatalogLabelsAreIdentifiedWithoutSplittingNestedCommasOrRecodingThem() {
        var q=q("areas","Áreas","checkbox",List.of(new Question.Option("A","Desarrollo (web, móvil)"),new Question.Option("B","Redes")));
        String source="Desarrollo (web, móvil), Formación (colegios, institutos)";
        assertThat(ImportSuggestions.unrecognizedOptions(q,source,"AUTO","MIXTO_V1")).containsExactly("Formación (colegios, institutos)");
        assertThatThrownBy(()->ImportSuggestions.options(q,source,"AUTO","MIXTO_V1"))
                .hasMessageContaining("Opciones no incluidas en esta versión").hasMessageContaining("Formación (colegios, institutos)");
        assertThat(ImportSuggestions.unrecognizedOptions(q,"[\"Redes\",\"Formación\"]","ETIQUETAS","MIXTO_V1")).containsExactly("Formación");
        assertThat(ImportSuggestions.unrecognizedOptions(q,"Redes;Formación;","AUTO","MIXTO_V1")).containsExactly("Formación");
    }
    @Test void missingLabelsAreNotProposedForMalformedListsUnknownCodesOrExistingAmbiguousValues() {
        for(String raw:List.of("Azul;;Naranja;","[\"Azul\",1]","[\"Azul\",\"Naranja\"","Naranja","Azul, Naranja (incompleto"))
            assertThat(ImportSuggestions.unrecognizedOptions(choices(),raw,"AUTO","MIXTO_V1")).isEmpty();
        assertThat(ImportSuggestions.unrecognizedOptions(choices(),"B|UNKNOWN","CODIGOS","MIXTO_V1")).isEmpty();
        assertThat(ImportSuggestions.unrecognizedOptions(choices(),"Azul|Naranja","AUTO","AUTO")).isEmpty();
        var ambiguous=q("q","Q","checkbox",List.of(new Question.Option("A","B"),new Question.Option("B","Otro"),new Question.Option("C","Verde")));
        assertThat(ImportSuggestions.unrecognizedOptions(ambiguous,"Verde|B","AUTO","MIXTO_V1")).isEmpty();
    }
    @Test void existingCompleteInterpretationsAndEquallyPlausibleListsDoNotProposeNewCategories() {
        var q=q("q","Q","checkbox",List.of(new Question.Option("A","a"),new Question.Option("BC","b;c"),new Question.Option("C","c")));
        assertThat(ImportSuggestions.options(q,"a|b;c","AUTO","MIXTO_V1")).containsExactly("A","BC");
        assertThat(ImportSuggestions.unrecognizedOptions(q,"a|b;c","AUTO","MIXTO_V1")).isEmpty();
        var ambiguous=q("q","Q","checkbox",List.of(new Question.Option("A","a"),new Question.Option("C","c")));
        assertThat(ImportSuggestions.unrecognizedOptions(ambiguous,"a|b;c","AUTO","MIXTO_V1")).isEmpty();
    }
    @Test void numberedWholeHeadersMatchWithoutSubstringOrAmbiguousNumberRemoval() {
        var question=q("area","Área principal","text",List.of());
        for(String header:List.of("12) Área principal","12. Área principal","12)\u00a0Área principal"))
            assertThat(ImportSuggestions.propose(definition(question),List.of(List.of(header),List.of("Ficticio"))).matched()).isEqualTo(1);
        var partial=ImportSuggestions.propose(definition(question),List.of(List.of("12) Detalle del Área principal"),List.of("Ficticio")));
        assertThat(partial.pending()).isEqualTo(1);
        var duplicate=ImportSuggestions.propose(definition(question),List.of(List.of("1) Área principal","2) Área principal"),List.of("A","B")));
        assertThat(duplicate.pending()).isEqualTo(1);
    }
}

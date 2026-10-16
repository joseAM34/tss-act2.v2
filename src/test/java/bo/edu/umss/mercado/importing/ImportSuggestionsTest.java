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
}

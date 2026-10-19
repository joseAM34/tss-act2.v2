package bo.edu.umss.mercado.statistics;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TextMentionsTest {
    @Test void bareNumbersNeedPrivateContextAndProgramNamesAreNotPersonalNames(){
        String rules=TextMentions.canonicalize("2 años=2 años|2");
        assertThat(TextMentions.count(List.of("2 materias","2 años"),TextDictionaryService.globalRules(rules)).get("matched")).isEqualTo(1);
        assertThat(TextMentions.count(List.of("2 materias","2 años"),rules).get("matched")).isEqualTo(2);
        assertThat(TextFieldPolicy.sensitive("text","INDIQUE EL NOMBRE DEL PROGRAMA")).isFalse();
        assertThat(TextFieldPolicy.sensitive("text","Nombre de la Empresa o Institución")).isTrue();
        assertThat(TextFieldPolicy.sensitive("email","Nombre del programa")).isTrue();
    }
    @Test void importReportExplainsExistingAliasesNewVariantsAndJoinedGroups(){
        String saved="inteligencia artificial=ia|inteligencia artificial\nmodelo=ml|modelo";
        String incoming="IA=IA|Inteligencia Artificial\nrobotica=robotica";
        var report=TextDictionaryService.importReport(saved,TextMentions.canonicalize(saved+"\n"+incoming),incoming);
        assertThat(report).hasSize(2).anySatisfy(r->{assertThat(r.get("action")).isEqualTo("Ya establecido, sin duplicar");assertThat((List<?>)r.get("addedAliases")).isEmpty();}).anySatisfy(r->assertThat(r.get("action")).isEqualTo("Grupo nuevo"));
        String bridge="inteligencia artificial=ia|ml|aprendizaje automatico";
        var joined=TextDictionaryService.importReport(saved,TextMentions.canonicalize(saved+"\n"+bridge),bridge);
        assertThat(joined).singleElement().satisfies(r->{assertThat(r.get("action")).isEqualTo("Grupos unidos");assertThat(r.get("addedAliases")).isEqualTo(List.of("aprendizaje automatico"));assertThat((List<?>)r.get("previousGroups")).hasSize(2);});
    }
    @Test void compoundPriorityAlsoIgnoresLegacySeparateAndBothFlags(){String base="inteligencia artificial=inteligencia artificial\ninteligencia=inteligencia\nartificial=artificial";var defaults=TextMentions.highlights("inteligencia artificial",base);assertThat(defaults).hasSize(1);assertThat(TextMentions.highlights("inteligencia artificial",base+"\n# compuesto: inteligencia artificial=separados")).hasSize(1);assertThat(TextMentions.highlights("inteligencia artificial",base+"\n# compuesto: inteligencia artificial=ambos")).hasSize(1);var conflicts=TextDictionaryService.compoundConflicts(List.of(Map.of("term","ingenieria de sistemas")));assertThat(conflicts.getFirst().get("individuals")).isEqualTo(List.of("ingenieria","sistemas"));}
    @Test void newSpellingVariantsAreComparedWithPreviouslyEstablishedUniversalAliases(){assertThat(TextDictionaryService.relationsWithDictionary(List.of(Map.of("term","informatica forence")),"informatica forense=informatica forense")).anySatisfy(r->{assertThat(r.get("left")).isEqualTo("informatica forense");assertThat(r.get("right")).isEqualTo("informatica forence");});}
    @Test void programmingLanguageSymbolsDoNotCollapseIntoTheSameWord(){String rules=TextMentions.canonicalize("C++=C++\nC#=C#");var rows=(List<Map<String,Object>>)TextMentions.count(List.of("uso C++"),rules).get("distribution");assertThat(rows).anySatisfy(r->{assertThat(r.get("label")).isEqualTo("c++");assertThat(r.get("count")).isEqualTo(1);});assertThat(rows).anySatisfy(r->{assertThat(r.get("label")).isEqualTo("c#");assertThat(r.get("count")).isEqualTo(0);});}
    @Test void canonicalGroupsMergeSharedAliasesAndLongestMatchPreventsFragmentDoubleCounting(){
        String rules=TextMentions.canonicalize("IA=IA|Inteligencia Artificial\nInteligencia Artificial=inteligencia artificial\nDATOS=datos\nANALISIS=análisis\nANALISIS DE DATOS=análisis de datos");
        assertThat(TextMentions.parse(rules)).hasSize(4);assertThat(rules).isEqualTo(rules.toLowerCase(Locale.ROOT));
        var rows=(List<Map<String,Object>>)TextMentions.count(List.of("ANÁLISIS DE DATOS. IA e inteligencia artificial."),rules).get("distribution");
        assertThat(rows).anySatisfy(r->{assertThat(r.get("label")).isEqualTo("analisis de datos");assertThat(r.get("count")).isEqualTo(1);});
        assertThat(rows).filteredOn(r->Set.of("datos","analisis").contains(r.get("label"))).allSatisfy(r->assertThat(r.get("count")).isEqualTo(0));
        assertThat(TextMentions.highlights("análisis de datos",rules)).hasSize(1);
        var extracted=TextDictionaryService.extract(List.of("Inteligencia Artificial, análisis de datos", "inteligencia artificial"));assertThat(extracted.stream().map(r->r.get("term"))).contains("inteligencia artificial","analisis de datos").doesNotContain("artificial","inteligencia","datos","analisis");
    }
    @Test void compoundTermsPreserveInternalConnectorsAndBulkRecognitionIsConservative(){
        var terms=TextDictionaryService.extract(List.of("Inteligencia artificial, informática forense y visión de negocio; herramientas para el análisis", "vision de negocio"));
        assertThat(terms.stream().map(t->t.get("term"))).contains("inteligencia artificial","informatica forense","vision de negocio","herramientas para el analisis").doesNotContain("visión de","de negocio");
        var classified=TextDictionaryService.classify(terms,"IA=Inteligencia artificial");
        assertThat(classified).anySatisfy(t->{assertThat(t.get("term")).isEqualTo("vision de negocio");assertThat(t.get("recognized")).isEqualTo(false);});
        assertThat(classified).anySatisfy(t->{assertThat(t.get("term")).isEqualTo("informatica forense");assertThat(t.get("recognized")).isEqualTo(false);});
        assertThat(classified).anySatisfy(t->{assertThat(t.get("term")).isEqualTo("inteligencia artificial");assertThat(t.get("recognized")).isEqualTo(true);});
    }
    @Test void wikidataRejectsContactQueriesAndArbitraryUrlsBeforeNetworking(){var service=new WikidataTerms();assertThatThrownBy(()->service.search("contacto@example.test")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->service.aliases("https://example.test")).isInstanceOf(IllegalArgumentException.class);}
    @Test void extractionKeepsPhrasesAndCountsEachTermOncePerAnswer(){
        var terms=TextDictionaryService.extract(List.of("Product Owner, Product Owner y la IA","product owner era una función"));
        assertThat(terms).anySatisfy(t->{assertThat(t.get("term")).isEqualTo("product owner");assertThat(t.get("count")).isEqualTo(2);});
        assertThat(terms.stream().map(t->t.get("term"))).doesNotContain("la","era","una");
        assertThat(TextDictionaryService.relations(List.of(Map.of("term","IA"),Map.of("term","Inteligencia Artificial")))).hasSize(1);
    }
    @Test void highlightsPreserveOriginalOffsetsAndExposeBothAliasesWithoutDoubleCounting(){
        String text="<b>IA e INTELIGENCIA ARTIFICIAL</b>";String rules="IA=IA|inteligencia artificial\n# Fuente: https://www.wikidata.org/wiki/Q11660";
        var ranges=TextMentions.highlights(text,rules);assertThat(ranges).hasSize(2);
        assertThat(ranges.stream().map(r->text.substring((Integer)r.get("start"),(Integer)r.get("end")))).containsExactly("IA","INTELIGENCIA ARTIFICIAL");
        assertThat(((List<Map<String,Object>>)TextMentions.count(List.of(text),rules).get("distribution")).getFirst().get("count")).isEqualTo(1);
    }
    @Test void synonymsCountOncePerResponseRespectWordsAccentsAndAllowSeveralTopics(){
        var result=TextMentions.count(List.of("IA, IA e Inteligencia Artificial. Buena comunicación y trabajo en equipo.","inteligencia artificial y COMUNICACION","ingeniería, social y diaria","No usamos IA"),"Inteligencia artificial=IA|inteligencia artificial\nComunicación=comunicación|buena comunicación\nEquipo=trabajo en equipo");
        var rows=(List<Map<String,Object>>)result.get("distribution");
        assertThat(rows.stream().map(r->r.get("count"))).containsExactly(3,2,1);assertThat(result.get("base")).isEqualTo(4);assertThat(result.get("unmatched")).isEqualTo(1);
    }
    @Test void aliasesAreLiteralAndRulesAreBounded(){
        assertThatThrownBy(()->TextMentions.parse("Sin separador")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TextMentions.parse("Tema=\nOtro=IA")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TextMentions.parse("IA=ia\nia=artificial")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->TextMentions.parse("x".repeat(12001))).isInstanceOf(IllegalArgumentException.class);
    }
}

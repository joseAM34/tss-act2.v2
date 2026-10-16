package bo.edu.umss.mercado.statistics;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import static org.assertj.core.api.Assertions.assertThat;

class StatisticalHelpTest {
    String render(long[][] counts, boolean independent, boolean structural) {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/"); resolver.setSuffix(".html"); resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine(); engine.setTemplateResolver(resolver);
        return engine.process("fragments/statistical-help", Set.of("cross"),
                new Context(Locale.forLanguageTag("es"), Map.of("t", StatisticalMath.contingency(counts, independent, structural, .05))));
    }
    @Test void missingEvidenceCannotBecomeAnAssociationConclusion() {
        assertThat(render(new long[][]{{18,12},{6,24}}, false, false))
                .contains("Pendiente: documentar", "La prueba no se ejecutó")
                .doesNotContain("Hay evidencia estadística de asociación", "no se rechaza la hipótesis");
    }
    @Test void emptyAndSparseTablesExplainTheirOwnLimitations() {
        assertThat(render(new long[][]{{0,0},{0,0}}, true, false))
                .contains("no hay pares con valor", "No cumple variación", "sin base suficiente");
        assertThat(render(new long[][]{{1,1},{1,1}}, true, false))
                .contains("No cumple frecuencias", "La prueba no se ejecutó");
    }
    @Test void decisionsUseTheBackendResultWithoutClaimingCausalityOrProvingIndependence() {
        assertThat(render(new long[][]{{18,12},{6,24}}, true, false))
                .contains("Hay evidencia estadística de asociación", "no demuestra causalidad")
                .doesNotContain("La prueba no se ejecutó", "no se rechaza la hipótesis");
        assertThat(render(new long[][]{{20,20},{20,20}}, true, false))
                .contains("no se rechaza la hipótesis", "no demuestra que las variables sean independientes")
                .doesNotContain("Hay evidencia estadística de asociación");
    }
    @Test void structuralZerosRequireReviewEvenWithEnoughCounts() {
        assertThat(render(new long[][]{{20,20},{20,20}}, true, true))
                .contains("combinaciones declaradas imposibles", "La prueba no se ejecutó");
    }
}

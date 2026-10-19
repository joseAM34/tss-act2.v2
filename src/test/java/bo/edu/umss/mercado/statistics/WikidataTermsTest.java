package bo.edu.umss.mercado.statistics;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WikidataTermsTest {
    @Test void requestsAreSpacedAndRemoteRateLimitBlocksFurtherRequestsWithoutSleeping(){var gate=new WikidataTerms.RequestGate();assertThat(gate.delay(1000)).isZero();assertThat(gate.delay(1000)).isEqualTo(1200);gate.throttle(1000,60);assertThatThrownBy(()->gate.delay(2000)).isInstanceOf(WikidataTerms.RateLimited.class).hasMessageContaining("429");assertThat(gate.delay(61000)).isZero();assertThat(WikidataTerms.retrySeconds("120")).isEqualTo(120);assertThat(WikidataTerms.retrySeconds("invalid")).isEqualTo(60);}
    @Test void aliasRequestEncodesSeparatorsBeforeBuildingTheJavaUri(){
        var uri=WikidataTerms.endpoint("action=wbgetentities&props=labels|aliases&languages=es|en&ids=Q11660");
        assertThat(uri.getHost()).isEqualTo("www.wikidata.org");
        assertThat(uri.toASCIIString()).contains("props=labels%7Caliases","languages=es%7Cen").doesNotContain("|");
        assertThat(uri.getQuery()).contains("props=labels|aliases","languages=es|en");
    }
}

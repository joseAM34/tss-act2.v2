package bo.edu.umss.mercado;

import bo.edu.umss.mercado.deployment.ProductionConfiguration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ProductionConfigurationTest {
    private static final Map<String, String> VALID = Map.of(
            "spring.datasource.url", "jdbc:postgresql://db.example.invalid:5432/market?sslmode=require",
            "spring.datasource.username", "deploy-verification",
            "spring.datasource.password", "verification-only-not-a-secret",
            "server.servlet.session.cookie.secure", "true",
            "app.setup.local-enabled", "false",
            "server.address", "0.0.0.0");

    @Test
    void validPostgresqlProductionConfigurationPasses() {
        assertThatCode(() -> ProductionConfiguration.validate(environment(null))).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "setup", "test"})
    void productionRejectsCombiningWithLocalOrTestProfiles(String profile) {
        var environment = environment(null);
        environment.setActiveProfiles("prod", profile);
        assertThatThrownBy(() -> ProductionConfiguration.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password"})
    void productionRejectsMissingDatabaseSettings(String missingSetting) {
        assertThatThrownBy(() -> ProductionConfiguration.validate(environment(missingSetting)))
                .isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password"})
    void productionRejectsWhitespaceOnlyDatabaseSettings(String blankSetting) {
        var environment = environment(null).withProperty(blankSetting, "   ");
        assertThatThrownBy(() -> ProductionConfiguration.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void h2IsRejectedWithoutEchoingTheDatabaseUriOrCredentials() {
        var environment = environment(null).withProperty("spring.datasource.url",
                "jdbc:h2:mem:private-study;PASSWORD=private-value");
        assertThatThrownBy(() -> ProductionConfiguration.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("private-study")
                .hasMessageNotContaining("private-value")
                .hasMessageNotContaining(VALID.get("spring.datasource.password"));
    }

    @Test
    void productionRejectsEnablingInitialSetup() {
        var environment = environment(null).withProperty("app.setup.local-enabled", "true");
        assertThatThrownBy(() -> ProductionConfiguration.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionRejectsInsecureSessionCookies() {
        var environment = environment(null).withProperty("server.servlet.session.cookie.secure", "false");
        assertThatThrownBy(() -> ProductionConfiguration.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void productionRejectsLoopbackOnlyBinding() {
        var environment = environment(null).withProperty("server.address", "127.0.0.1");
        assertThatThrownBy(() -> ProductionConfiguration.validate(environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unsafeConfigurationFailsBeforeAnyDataSourceIsCreated() {
        DataSourceProbe.CREATIONS.set(0);
        var runner = new ApplicationContextRunner()
                .withUserConfiguration(ProductionConfiguration.class, DataSourceProbe.class)
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"));
        for (var entry : VALID.entrySet()) {
            runner = runner.withPropertyValues(entry.getKey() + "=" + entry.getValue());
        }
        runner.withPropertyValues("spring.datasource.url=jdbc:h2:mem:should-not-connect").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).isInstanceOf(IllegalStateException.class);
            assertThat(DataSourceProbe.CREATIONS).hasValue(0);
        });
    }

    private MockEnvironment environment(String omittedSetting) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        VALID.forEach((key, value) -> {
            if (!key.equals(omittedSetting)) environment.withProperty(key, value);
        });
        return environment;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class DataSourceProbe {
        static final AtomicInteger CREATIONS = new AtomicInteger();

        @Bean
        DataSource probeDataSource() {
            CREATIONS.incrementAndGet();
            return mock(DataSource.class);
        }
    }
}

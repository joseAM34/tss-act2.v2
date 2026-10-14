package bo.edu.umss.mercado;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Loads the distributed profile configuration without creating a database connection. */
class DeploymentConfigurationTest {
    private static final Map<String, String> DATABASE = Map.of(
            "DATABASE_URL", "jdbc:postgresql://db.example.invalid:5432/market?sslmode=require",
            "DATABASE_USERNAME", "deploy-verification",
            "DATABASE_PASSWORD", "verification-only-not-a-secret");

    @Test
    void productionResolvesExplicitPostgresqlCredentials() {
        production(null).run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getRequiredProperty("spring.datasource.url")).isEqualTo(DATABASE.get("DATABASE_URL"));
            assertThat(environment.getRequiredProperty("spring.datasource.username")).isEqualTo(DATABASE.get("DATABASE_USERNAME"));
            assertThat(environment.getRequiredProperty("spring.datasource.password")).isEqualTo(DATABASE.get("DATABASE_PASSWORD"));
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"DATABASE_URL", "DATABASE_USERNAME", "DATABASE_PASSWORD"})
    void productionDoesNotFallBackToLocalDatabaseOrCredentials(String missingVariable) {
        String property = switch (missingVariable) {
            case "DATABASE_URL" -> "spring.datasource.url";
            case "DATABASE_USERNAME" -> "spring.datasource.username";
            default -> "spring.datasource.password";
        };
        production(missingVariable).run(context -> assertThatThrownBy(
                () -> context.getEnvironment().getRequiredProperty(property))
                .isInstanceOf(IllegalArgumentException.class));
    }

    @Test
    void productionKeepsHttpsCookiesAndInitialSetupClosed() {
        production(null).withPropertyValues("SESSION_COOKIE_SECURE=false").run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getRequiredProperty("server.servlet.session.cookie.secure", Boolean.class)).isTrue();
            assertThat(environment.getRequiredProperty("server.servlet.session.cookie.http-only", Boolean.class)).isTrue();
            assertThat(environment.getRequiredProperty("server.servlet.session.cookie.same-site")).isEqualTo("lax");
            assertThat(environment.getRequiredProperty("app.setup.local-enabled", Boolean.class)).isFalse();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"dev", "setup"})
    void localSetupProfilesBindLoopbackAndDoNotTrustForwardedClientAddresses(String profile) {
        configuration(profile).run(context -> {
            var environment = context.getEnvironment();
            assertThat(environment.getRequiredProperty("server.address")).isEqualTo("127.0.0.1");
            assertThat(environment.getRequiredProperty("server.forward-headers-strategy")).isEqualToIgnoringCase("none");
            assertThat(environment.getRequiredProperty("app.setup.local-enabled", Boolean.class)).isTrue();
        });
    }

    @Test
    void productionBoundsItsConnectionPoolForSmallHostingInstances() {
        production(null).run(context -> {
            var environment = context.getEnvironment();
            int maximum = environment.getRequiredProperty("spring.datasource.hikari.maximum-pool-size", Integer.class);
            int minimum = environment.getRequiredProperty("spring.datasource.hikari.minimum-idle", Integer.class);
            assertThat(maximum).isBetween(1, 5);
            assertThat(minimum).isBetween(0, maximum);
        });
    }

    private ApplicationContextRunner production(String missingVariable) {
        var runner = configuration("prod");
        for (var entry : DATABASE.entrySet()) {
            if (!entry.getKey().equals(missingVariable)) {
                runner = runner.withPropertyValues(entry.getKey() + "=" + entry.getValue());
            }
        }
        return runner;
    }

    private ApplicationContextRunner configuration(String profile) {
        return new ApplicationContextRunner()
                .withPropertyValues("spring.profiles.active=" + profile)
                .withInitializer(context -> {
                    // Missing-variable checks must not depend on credentials in the machine running the suite.
                    context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    context.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    new ConfigDataApplicationContextInitializer().initialize(context);
                });
    }
}

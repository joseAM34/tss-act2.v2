package bo.edu.umss.mercado.deployment;

import java.util.Arrays;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Comprueba producción antes de crear la conexión o aplicar migraciones. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfiguration {
    @Bean
    static BeanFactoryPostProcessor productionConfigurationCheck(Environment environment) {
        return factory -> validate(environment);
    }

    public static void validate(Environment environment) {
        if (Arrays.stream(environment.getActiveProfiles()).anyMatch(Set.of("dev", "setup", "test")::contains))
            throw new IllegalStateException("El perfil prod no puede combinarse con dev, setup o test.");
        String url = required(environment, "spring.datasource.url");
        if (!url.startsWith("jdbc:postgresql:"))
            throw new IllegalStateException("El despliegue prod requiere una conexión JDBC PostgreSQL.");
        required(environment, "spring.datasource.username");
        required(environment, "spring.datasource.password");
        if (environment.getProperty("app.setup.local-enabled", Boolean.class, false))
            throw new IllegalStateException("La configuración inicial debe permanecer deshabilitada en prod.");
        if (!environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, false))
            throw new IllegalStateException("El perfil prod requiere cookies de sesión seguras y acceso HTTPS.");
        if (!"0.0.0.0".equals(environment.getProperty("server.address")))
            throw new IllegalStateException("El servicio prod debe escuchar en 0.0.0.0 para el proxy de alojamiento.");
    }

    private static String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank())
            throw new IllegalStateException("Falta configurar " + key + " para prod.");
        return value;
    }
}

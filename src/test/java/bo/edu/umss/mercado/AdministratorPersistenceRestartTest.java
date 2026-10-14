package bo.edu.umss.mercado;

import bo.edu.umss.mercado.security.AdministratorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AdministratorPersistenceRestartTest {
    @TempDir Path databaseDirectory;

    @Test
    void administratorAndItsPasswordHashSurviveApplicationShutdownAndRestart() {
        String password = "Clave-persistente-prueba";
        String hash;
        try (ConfigurableApplicationContext first = startApplication()) {
            AdministratorService administrators = first.getBean(AdministratorService.class);
            assertThat(administrators.exists()).isFalse();
            administrators.register("Local.Admin", password);
            hash = first.getBean(JdbcTemplate.class)
                    .queryForObject("SELECT password_hash FROM administrator WHERE id = 1", String.class);
            assertThat(first.getBean(PasswordEncoder.class).matches(password, hash)).isTrue();
        }

        try (ConfigurableApplicationContext restarted = startApplication()) {
            AdministratorService administrators = restarted.getBean(AdministratorService.class);
            assertThat(administrators.exists()).isTrue();
            var user = administrators.loadUserByUsername("LOCAL.ADMIN");
            assertThat(user.getUsername()).isEqualTo("local.admin");
            assertThat(user.getPassword()).isEqualTo(hash);
            assertThat(user.isEnabled()).isTrue();
            assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
            assertThat(restarted.getBean(PasswordEncoder.class).matches(password, user.getPassword())).isTrue();
            assertThat(restarted.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM administrator", Long.class))
                    .isEqualTo(1);
        }
    }

    private ConfigurableApplicationContext startApplication() {
        String databasePath = databaseDirectory.resolve("administrator").toAbsolutePath().toString().replace('\\', '/');
        return new SpringApplicationBuilder(EstudioMercadoApplication.class).run(
                "--spring.profiles.active=test",
                "--spring.datasource.url=jdbc:h2:file:" + databasePath + ";MODE=PostgreSQL",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--app.setup.local-enabled=true",
                "--server.port=0",
                "--spring.main.banner-mode=off"
        );
    }
}

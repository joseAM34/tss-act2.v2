package bo.edu.umss.mercado;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:administrator-setup-disabled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministratorSetupDisabledIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clearAdministrator() {
        jdbc.update("DELETE FROM administrator");
    }

    @Test
    void localClientCannotCreateAdministratorWhenSetupIsDisabled() throws Exception {
        mvc.perform(get("/configuracion-inicial").with(request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        })).andExpect(status().isForbidden());
        mvc.perform(post("/configuracion-inicial").param("username", "admin")
                        .param("password", "Clave-de-prueba-2026").param("confirmation", "Clave-de-prueba-2026")
                        .with(csrf()).with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/login")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM administrator", Long.class)).isZero();
    }
}

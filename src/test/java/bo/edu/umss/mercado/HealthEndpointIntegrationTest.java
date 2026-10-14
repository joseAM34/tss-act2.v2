package bo.edu.umss.mercado;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:deployment-health;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoSpyBean JdbcTemplate jdbc;

    @Test
    void anonymousHealthCheckVerifiesDatabaseWithoutCreatingSessionOrExposingMetadata() throws Exception {
        var result = mvc.perform(get("/healthz"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"status\":\"ok\"}"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isNull();
    }

    @Test
    void unavailableDatabaseReturnsGeneric503WithoutConnectionOrExceptionDetails() throws Exception {
        doThrow(new DataAccessResourceFailureException(
                "jdbc:postgresql://private-host:5432/private-study password=private-value"))
                .when(jdbc).queryForObject("SELECT 1", Integer.class);
        mvc.perform(get("/healthz"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"status\":\"unavailable\"}"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void healthAccessDoesNotAllowAnonymousAdministration() throws Exception {
        mvc.perform(get("/admin")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/datos")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/admin/exportar.csv")).andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void healthPostRemainsForbiddenWithOrWithoutCsrf() throws Exception {
        mvc.perform(post("/healthz")).andExpect(status().isForbidden());
        mvc.perform(post("/healthz").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void initialSetupIsClosedEvenForLoopbackAndForgedForwardedHeaders() throws Exception {
        mvc.perform(get("/configuracion-inicial")
                        .header("X-Forwarded-For", "127.0.0.1")
                        .header("Forwarded", "for=127.0.0.1;proto=https")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/login")).andExpect(status().isOk());
    }
}

package bo.edu.umss.mercado;

import bo.edu.umss.mercado.security.AdministratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DATABASE_URL:jdbc:h2:mem:administrator-setup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DATABASE_USERNAME:sa}",
        "spring.datasource.password=${TEST_DATABASE_PASSWORD:}",
        "app.setup.local-enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdministratorSetupIntegrationTest {
    private static final String PASSWORD = "Clave-de-prueba-2026";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired AdministratorService administrators;
    @Autowired PasswordEncoder encoder;

    @BeforeEach
    void clearAdministrator() {
        jdbc.update("DELETE FROM administrator");
    }

    @Test
    void firstLocalVisitOffersSetupWhileSurveysRemainPublic() throws Exception {
        assertThat(administrators.exists()).isFalse();
        mvc.perform(get("/login").with(request -> {
            request.setRemoteAddr("127.0.0.1");
            return request;
        })).andExpect(redirectedUrl("/configuracion-inicial"));
        mvc.perform(local(get("/configuracion-inicial")))
                .andExpect(status().isOk()).andExpect(view().name("setup"))
                .andExpect(model().attribute("username", "admin"))
                .andExpect(model().attributeExists("errors"));
        mvc.perform(get("/encuestas/titulados")).andExpect(status().isOk());
        mvc.perform(get("/encuestas/empleadores")).andExpect(status().isOk());
        mvc.perform(get("/admin")).andExpect(redirectedUrl("/login"));
        assertThat(count()).isZero();
    }

    @Test
    void setupStoresOneAdministratorWithBcryptAndDatabaseLoginWorks() throws Exception {
        mvc.perform(local(registration("My.Admin", PASSWORD, PASSWORD)).with(csrf()))
                .andExpect(redirectedUrl("/login?created"));
        assertThat(count()).isEqualTo(1);
        Map<String, Object> stored = jdbc.queryForMap("SELECT * FROM administrator");
        assertThat(stored.get("id")).isEqualTo(1);
        assertThat(stored.get("username")).isEqualTo("my.admin");
        assertThat(stored.get("enabled")).isEqualTo(true);
        assertThat(stored.get("created_at")).isNotNull();
        String hash = hash();
        assertThat(hash).startsWith("$2").hasSize(60);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(stored.values().stream().map(String::valueOf)).noneMatch(value -> value.contains(PASSWORD));
        mvc.perform(get("/login")).andExpect(status().isOk());

        MvcResult result = mvc.perform(formLogin("/login").user("MY.ADMIN").password(PASSWORD))
                .andExpect(authenticated().withUsername("my.admin"))
                .andExpect(redirectedUrl("/admin")).andReturn();
        mvc.perform(get("/admin").session((MockHttpSession) result.getRequest().getSession(false)))
                .andExpect(status().isOk());
        mvc.perform(formLogin("/login").user("my.admin").password("incorrecta"))
                .andExpect(unauthenticated()).andExpect(redirectedUrl("/login?error"));
        mvc.perform(formLogin("/login").user("otra-cuenta").password(PASSWORD))
                .andExpect(unauthenticated());
    }

    @Test
    void setupRequiresCsrfAndDoesNotWriteWhenItIsMissing() throws Exception {
        mvc.perform(local(registration("admin", PASSWORD, PASSWORD))).andExpect(status().isForbidden());
        assertThat(count()).isZero();
    }

    @Test
    void completedSetupCannotReplaceAnAccountThroughTheWeb() throws Exception {
        administrators.register("admin", PASSWORD);
        String originalHash = hash();
        mvc.perform(local(get("/configuracion-inicial"))).andExpect(redirectedUrl("/login"));
        mvc.perform(local(registration("otro-admin", "Otra-clave-de-prueba", "Otra-clave-de-prueba")).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(count()).isEqualTo(1);
        assertThat(hash()).isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("SELECT username FROM administrator", String.class)).isEqualTo("admin");
    }

    @ParameterizedTest
    @ValueSource(strings = {"192.0.2.15", "10.0.0.8", "203.0.113.4"})
    void remoteClientsCannotInitializeEvenWhenForwardedHeadersClaimLocalhost(String remoteAddress) throws Exception {
        mvc.perform(get("/configuracion-inicial").header("X-Forwarded-For", "127.0.0.1")
                        .header("Forwarded", "for=127.0.0.1").with(request -> {
                            request.setRemoteAddr(remoteAddress);
                            return request;
                        }))
                .andExpect(status().isForbidden());
        mvc.perform(registration("admin", PASSWORD, PASSWORD).header("X-Forwarded-For", "127.0.0.1")
                        .with(csrf()).with(request -> {
                            request.setRemoteAddr(remoteAddress);
                            return request;
                        }))
                .andExpect(status().isForbidden());
        mvc.perform(get("/login").with(request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        })).andExpect(status().isOk());
        assertThat(count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "::1", "0:0:0:0:0:0:0:1"})
    void bothIpv4AndIpv6LoopbackAllowSetup(String remoteAddress) throws Exception {
        mvc.perform(get("/configuracion-inicial").with(request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        })).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "admin usuario", "admin@ejemplo", "administradorá"})
    void invalidUsernamesAreRejectedOnServer(String username) throws Exception {
        rejectedSetup(username, PASSWORD, PASSWORD, "username");
    }

    @Test
    void oversizedUsernameIsRejectedOnServer() throws Exception {
        rejectedSetup("a".repeat(51), PASSWORD, PASSWORD, "username");
    }

    @Test
    void shortPasswordIsRejectedWithoutBeingRetainedInThePage() throws Exception {
        rejectedSetup("admin", "abc12345678", "abc12345678", "password");
    }

    @Test
    void confirmationMustMatchAndIsNeverRetained() throws Exception {
        rejectedSetup("admin", PASSWORD, "Otra-clave-distinta", "confirmation");
    }

    @Test
    void bcryptLimitIsMeasuredInUtf8BytesRatherThanCharacters() throws Exception {
        String tooManyBytes = "ñ".repeat(37);
        assertThat(tooManyBytes.length()).isLessThan(72);
        assertThat(tooManyBytes.getBytes(StandardCharsets.UTF_8)).hasSize(74);
        rejectedSetup("admin", tooManyBytes, tooManyBytes, "password");
    }

    @Test
    void exactlySeventyTwoUtf8BytesCanBeSavedWithoutTruncation() throws Exception {
        String atLimit = "ñ".repeat(36);
        mvc.perform(local(registration("admin", atLimit, atLimit)).with(csrf()))
                .andExpect(redirectedUrl("/login?created"));
        assertThat(encoder.matches(atLimit, hash())).isTrue();
        assertThat(encoder.matches("ñ".repeat(35) + "x", hash())).isFalse();
    }

    @Test
    void directServiceCallsAlsoValidateCredentialsAndPreventOverwrite() {
        assertThatThrownBy(() -> administrators.register("bad user", PASSWORD)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> administrators.register("admin", "corta")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> administrators.register("admin", "x".repeat(73))).isInstanceOf(IllegalArgumentException.class);
        assertThat(count()).isZero();
        administrators.register("Admin", PASSWORD);
        String originalHash = hash();
        assertThatThrownBy(() -> administrators.register("replacement", "Otra-clave-de-prueba"))
                .isInstanceOf(AdministratorService.SetupAlreadyCompletedException.class);
        assertThat(hash()).isEqualTo(originalHash);
        assertThat(administrators.loadUserByUsername("ADMIN").getUsername()).isEqualTo("admin");
        assertThatThrownBy(() -> administrators.loadUserByUsername("inexistente"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void disabledAccountCannotAuthenticateOrBeReplacedBySetup() throws Exception {
        administrators.register("admin", PASSWORD);
        jdbc.update("UPDATE administrator SET enabled = FALSE WHERE id = 1");
        String originalHash = hash();
        assertThat(administrators.exists()).isTrue();
        assertThat(administrators.loadUserByUsername("admin").isEnabled()).isFalse();
        mvc.perform(formLogin("/login").user("admin").password(PASSWORD)).andExpect(unauthenticated());
        mvc.perform(local(registration("replacement", PASSWORD, PASSWORD)).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(hash()).isEqualTo(originalHash);
    }

    @Test
    void databaseConstraintAllowsOnlyTheSingleAdministrator() {
        administrators.register("admin", PASSWORD);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO administrator (id, username, password_hash, created_at) VALUES (2, ?, ?, CURRENT_TIMESTAMP)",
                "another", hash())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void concurrentFirstRegistrationsProduceOnlyOneAccount() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> registerConcurrently("first-admin", ready, start));
            var second = executor.submit(() -> registerConcurrently("second-admin", ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT username FROM administrator", String.class))
                .isIn("first-admin", "second-admin");
        assertThat(encoder.matches(PASSWORD, hash())).isTrue();
    }

    private int registerConcurrently(String username, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent setup did not start");
        }
        try {
            administrators.register(username, PASSWORD);
            return 1;
        } catch (AdministratorService.SetupAlreadyCompletedException | DuplicateKeyException ex) {
            assertThat(administrators.exists()).isTrue();
            return 0;
        }
    }

    private void rejectedSetup(String username, String password, String confirmation, String field) throws Exception {
        MvcResult result = mvc.perform(local(registration(username, password, confirmation)).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("setup"))
                .andExpect(model().attributeExists("errors")).andReturn();
        Map<?, ?> errors = (Map<?, ?>) result.getModelAndView().getModel().get("errors");
        assertThat(errors.containsKey(field)).isTrue();
        assertThat(result.getModelAndView().getModel()).doesNotContainKeys("password", "confirmation");
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain(password, confirmation);
        assertThat(count()).isZero();
    }

    private static MockHttpServletRequestBuilder registration(String username, String password, String confirmation) {
        return post("/configuracion-inicial").param("username", username)
                .param("password", password).param("confirmation", confirmation);
    }

    private static MockHttpServletRequestBuilder local(MockHttpServletRequestBuilder request) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr("127.0.0.1");
            return servletRequest;
        });
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM administrator", Long.class);
    }

    private String hash() {
        return jdbc.queryForObject("SELECT password_hash FROM administrator", String.class);
    }
}

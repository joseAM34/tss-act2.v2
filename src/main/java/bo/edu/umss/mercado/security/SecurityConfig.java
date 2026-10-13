package bo.edu.umss.mercado.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService administrators(@Value("${app.admin.username}") String username,
                                     @Value("${app.admin.password}") String password,
                                     PasswordEncoder encoder) {
        if (username.isBlank() || password.isBlank() || password.length() < 12) {
            throw new IllegalStateException("Define ADMIN_USERNAME y ADMIN_PASSWORD (mínimo 12 caracteres) antes de iniciar.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(encoder.encode(password)).roles("ADMIN").build());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/login", "/gracias", "/encuestas/**",
                                "/css/**", "/js/**", "/favicon.svg").permitAll()
                        .requestMatchers(HttpMethod.POST, "/encuestas/**").permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/admin", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                                + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'")))
                .build();
    }
}

package bo.edu.umss.mercado.security;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/", "/login", "/configuracion-inicial", "/gracias", "/encuestas/**",
                                "/css/**", "/js/**", "/vendor/**", "/favicon.svg", "/healthz").permitAll()
                        .requestMatchers(HttpMethod.POST, "/encuestas/**", "/configuracion-inicial").permitAll()
                        .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .formLogin(form -> form.loginPage("/login").successHandler((request,response,authentication)->{
                    var session=request.getSession(false);
                    if(session!=null){session.removeAttribute("workspaceResultsCache");session.removeAttribute("workspaceReportPreferences");}
                    response.sendRedirect(request.getContextPath()+"/admin");
                }).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
                                + "form-action 'self'; frame-ancestors 'none'; base-uri 'self'")))
                .build();
    }
}

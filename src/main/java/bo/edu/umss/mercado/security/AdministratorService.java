package bo.edu.umss.mercado.security;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La cuenta administrativa y el hash se conservan en la misma base de datos del estudio. */
@Service
public class AdministratorService implements UserDetailsService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AdministratorService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public boolean exists() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM administrator", Long.class) > 0;
    }

    public Map<String, String> validate(String username, String password, String confirmation) {
        var errors = new LinkedHashMap<String, String>();
        String normalized = normalizeUsername(username);
        if (!normalized.matches("[a-z0-9._-]{3,50}")) {
            errors.put("username", "Use entre 3 y 50 caracteres: letras, números, puntos, guiones o guiones bajos.");
        }
        if (password == null || password.isBlank() || password.length() < 12) {
            errors.put("password", "La contraseña debe tener al menos 12 caracteres.");
        } else if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            errors.put("password", "La contraseña es demasiado larga: use como máximo 72 bytes en UTF-8.");
        }
        if (password == null || !password.equals(confirmation)) {
            errors.put("confirmation", "Las contraseñas deben coincidir.");
        }
        return errors;
    }

    @Transactional
    public void register(String username, String password) {
        if (exists()) throw new SetupAlreadyCompletedException();
        if (!validate(username, password, password).isEmpty()) {
            throw new IllegalArgumentException("Los datos de la cuenta no son válidos.");
        }
        // La PK fija impide que dos solicitudes simultáneas creen administradores distintos.
        jdbc.update("""
                INSERT INTO administrator (id, username, password_hash, enabled, created_at)
                VALUES (1, ?, ?, TRUE, ?)
                """, normalizeUsername(username), encoder.encode(password), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var matches = jdbc.query("""
                SELECT username, password_hash, enabled FROM administrator WHERE username = ?
                """, (rs, row) -> User.withUsername(rs.getString("username"))
                .password(rs.getString("password_hash")).roles("ADMIN")
                .disabled(!rs.getBoolean("enabled")).build(), normalizeUsername(username));
        if (matches.isEmpty()) throw new UsernameNotFoundException("Credenciales no válidas");
        return matches.getFirst();
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    public static class SetupAlreadyCompletedException extends RuntimeException {
        public SetupAlreadyCompletedException() {
            super("La cuenta de administración ya está configurada.");
        }
    }
}

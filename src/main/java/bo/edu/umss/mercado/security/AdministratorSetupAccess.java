package bo.edu.umss.mercado.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdministratorSetupAccess {
    private final boolean localEnabled;

    public AdministratorSetupAccess(@Value("${app.setup.local-enabled:false}") boolean localEnabled) {
        this.localEnabled = localEnabled;
    }

    public boolean allowed(HttpServletRequest request) {
        if (!localEnabled || request.getRemoteAddr() == null) return false;
        try {
            // Se utiliza la conexión directa, sin confiar en cabeceras enviadas por el cliente.
            return InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress();
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    public void requireAllowed(HttpServletRequest request) {
        if (!allowed(request)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}

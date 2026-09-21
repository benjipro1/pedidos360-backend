package cl.benji.pedidos360.bff.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Diferencia clave con el 401: aca la identidad SI es valida (el token paso
 * firma, issuer, audience y expiracion), pero a ese usuario le falta el
 * scope o el rol que la ruta/metodo exige (por ejemplo: tiene
 * SCOPE_pedidos.write pero no ROLE_Admin, e intenta borrar un producto).
 * 401 es "no se quien eres", 403 es "se quien eres, y no puedes hacer
 * esto". Igual que con el 401, se usa el formato de error propio del
 * proyecto en vez del que trae Spring Security por defecto.
 */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        JsonErrorResponseWriter.escribir(response, HttpServletResponse.SC_FORBIDDEN,
                "acceso_denegado", "no tienes permisos para esta operacion");
    }
}

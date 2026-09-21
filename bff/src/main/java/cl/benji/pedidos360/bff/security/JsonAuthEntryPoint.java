package cl.benji.pedidos360.bff.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Que responde el BFF cuando NO hay identidad valida: sin token, token con
 * firma invalida, issuer distinto, audience distinta, o expirado. Spring
 * Security, sin este bean, respondiera un 401 con cuerpo vacio o un mensaje
 * en texto plano (segun el caso); esto lo reemplaza por el mismo formato de
 * error que usan ms-productos y ms-pedidos, para que el frontend maneje
 * errores de forma consistente sin importar que servicio respondio.
 *
 * A proposito NO se expone en el mensaje CUAL validacion fallo
 * especificamente (firma vs issuer vs audience vs expiracion): decirselo a
 * quien hizo la llamada es informacion util para alguien tratando de
 * falsificar un token, y no le sirve de nada a un cliente legitimo (la
 * unica accion posible del lado del cliente es "volver a autenticarse").
 * El detalle real de la excepcion queda disponible en los logs del
 * servidor si hace falta depurar.
 */
public class JsonAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        // RFC 6750: un 401 de una API con bearer tokens debe indicar el
        // esquema de autenticacion esperado. Lo agregamos a mano porque al
        // reemplazar el entry point por defecto del resource server se
        // pierde la cabecera que este ponia.
        response.setHeader("WWW-Authenticate", "Bearer");
        JsonErrorResponseWriter.escribir(response, HttpServletResponse.SC_UNAUTHORIZED,
                "no_autenticado", "se requiere un token de acceso valido");
    }
}

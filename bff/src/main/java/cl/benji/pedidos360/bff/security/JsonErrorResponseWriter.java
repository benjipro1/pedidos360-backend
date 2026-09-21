package cl.benji.pedidos360.bff.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

/**
 * Escribe el cuerpo de error {@code { "status": <int>, "error": "<slug>",
 * "message": "<texto>" } } a mano, sin pasar por Jackson.
 *
 * Por que a mano: el entry point (401) y el access denied handler (403) se
 * ejecutan DENTRO del filtro de seguridad, antes de que Spring MVC entre en
 * juego (no son controladores, son piezas de mas bajo nivel de Spring
 * Security). Para un objeto de 3 campos fijos, escribir el JSON
 * directamente es mas simple y explicito que inyectar un mapeador de JSON
 * solo para esto.
 */
final class JsonErrorResponseWriter {

    private JsonErrorResponseWriter() {
    }

    static void escribir(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String cuerpo = "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}"
                .formatted(status, escapar(error), escapar(message));
        response.getWriter().write(cuerpo);
    }

    private static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

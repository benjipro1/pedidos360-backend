package cl.benji.pedidos360.bff.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.client.RestClient;

/**
 * Helpers compartidos por los controladores proxy (productos y pedidos).
 */
final class ProxyResponses {

    private ProxyResponses() {
    }

    /**
     * Punto 4 del enunciado: si el microservicio responde 404 o 400 (o
     * cualquier otro codigo), el BFF debe devolver ESE mismo codigo, no
     * convertirlo en 500. Por defecto, RestClient lanza una excepcion ante
     * cualquier respuesta 4xx/5xx (para que el codigo que llama la maneje
     * explicitamente); aca hacemos lo contrario a proposito:
     * {@code onStatus(status -> true, noop)} le dice "no lances nada, para
     * NINGUN status", asi que {@code toEntity(...)} siempre devuelve
     * normalmente con el status real de la respuesta, sea 200, 404 o 400.
     * Copiamos manualmente status + Content-Type + cuerpo (en vez de
     * reenviar TODOS los headers tal cual) para no arrastrar headers de
     * transporte (Content-Length, Connection, Transfer-Encoding) que
     * Spring MVC ya recalcula por su cuenta al armar la respuesta real.
     *
     * Si el microservicio esta directamente caido (no hay respuesta HTTP en
     * absoluto), RestClient lanza {@code ResourceAccessException}, que no
     * pasa por aca: la captura
     * {@link cl.benji.pedidos360.bff.exception.BffExceptionHandler}.
     */
    static ResponseEntity<byte[]> reenviar(RestClient.RequestHeadersSpec<?> request) {
        ResponseEntity<byte[]> respuesta = request.retrieve()
                .onStatus(status -> true, (req, res) -> {
                    // No-op a proposito: ver el porque en el Javadoc de arriba.
                })
                .toEntity(byte[].class);

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(respuesta.getStatusCode());
        MediaType contentType = respuesta.getHeaders().getContentType();
        if (contentType != null) {
            builder.contentType(contentType);
        }
        return builder.body(respuesta.getBody());
    }

    /**
     * El BFF extrae del token el claim preferred_username y lo reenvia a
     * los microservicios en la cabecera X-User-Email (ver CLAUDE.md). Se
     * toma SIEMPRE del JWT ya validado, nunca de un header que mande el
     * cliente: si confiaramos en un X-User-Email que llega en la request
     * original, cualquiera con un token valido propio podria mandar el
     * email de OTRO usuario y hacerse pasar por el en ms-pedidos.
     */
    static String usuarioDe(Jwt jwt) {
        return jwt.getClaimAsString("preferred_username");
    }
}

package cl.benji.pedidos360.bff.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Errores que el BFF responde por su cuenta, con el mismo formato
 * {status, error, message} que usan ms-productos y ms-pedidos.
 *
 * Los errores que SI vienen del microservicio (404, 400, etc.) no pasan por
 * aca: {@link cl.benji.pedidos360.bff.controller.ProxyResponses} propaga ese
 * status y ese cuerpo tal cual. Aca quedan los dos casos en que no hay
 * respuesta del microservicio que propagar.
 */
@RestControllerAdvice
public class BffExceptionHandler {

    public record ErrorResponse(int status, String error, String message) {
    }

    /**
     * El microservicio esta caido (no escucha, timeout de conexion, DNS):
     * RestClient lanza esto en vez de devolver una respuesta HTTP. Sin este
     * manejador, Spring Boot responderia su 500 generico, con un formato
     * distinto al del resto de la API.
     */
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleServicioNoDisponible(ResourceAccessException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(HttpStatus.SERVICE_UNAVAILABLE.value(), "servicio_no_disponible",
                        "no fue posible contactar al microservicio"));
    }

    /**
     * La URL trae un path variable que no calza con el tipo esperado (por
     * ejemplo GET /api/productos/abc, donde "abc" no es un Long). Es un
     * error del cliente y la request ni siquiera llega al microservicio;
     * sin este manejador se responderia 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleParametroInvalido(MethodArgumentTypeMismatchException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "parametro_invalido",
                        "el parametro '" + ex.getName() + "' tiene un valor invalido"));
    }
}

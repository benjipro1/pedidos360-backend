package cl.benji.pedidos360.ms_mensajeria.exception;

import java.util.stream.Collectors;

import cl.benji.pedidos360.ms_mensajeria.controller.KafkaAdminController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores del administrador de Kafka con el mismo formato que el resto del
 * backend: { "status": <int>, "error": "<slug>", "message": "<texto>" }.
 */
@Slf4j
@RestControllerAdvice(assignableTypes = KafkaAdminController.class)
public class KafkaAdminExceptionHandler {

    public record ErrorResponse(int status, String error, String message) {
    }

    @ExceptionHandler(KafkaAdminException.class)
    public ResponseEntity<ErrorResponse> handleKafka(KafkaAdminException ex) {
        HttpStatus status = switch (ex.getTipo()) {
            case NO_ENCONTRADO -> HttpStatus.NOT_FOUND;
            case CONFLICTO -> HttpStatus.CONFLICT;
            case SOLICITUD_INVALIDA -> HttpStatus.BAD_REQUEST;
            case NO_DISPONIBLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        if (status.is5xxServerError()) {
            log.error("Error administrando Kafka: {}", ex.getMessage());
        } else {
            log.warn("Operacion de Kafka rechazada ({}): {}", status.value(), ex.getMessage());
        }
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), ex.getTipo().name().toLowerCase(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "validacion", detalle));
    }
}

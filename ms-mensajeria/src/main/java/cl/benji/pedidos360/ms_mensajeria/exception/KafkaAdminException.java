package cl.benji.pedidos360.ms_mensajeria.exception;

import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.GroupNotEmptyException;
import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.TimeoutException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

/**
 * Error de una operacion de administracion de Kafka, ya clasificado para
 * responder con el codigo HTTP correcto (404, 409, 400 o 503).
 */
public class KafkaAdminException extends RuntimeException {

    public enum Tipo { NO_ENCONTRADO, CONFLICTO, SOLICITUD_INVALIDA, NO_DISPONIBLE, ERROR }

    private final Tipo tipo;

    public KafkaAdminException(Tipo tipo, String mensaje) {
        super(mensaje);
        this.tipo = tipo;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public static KafkaAdminException desde(Throwable causa) {
        Tipo tipo = switch (causa) {
            case UnknownTopicOrPartitionException e -> Tipo.NO_ENCONTRADO;
            case GroupIdNotFoundException e -> Tipo.NO_ENCONTRADO;
            case TopicExistsException e -> Tipo.CONFLICTO;
            case GroupNotEmptyException e -> Tipo.CONFLICTO;
            case InvalidPartitionsException e -> Tipo.SOLICITUD_INVALIDA;
            // Incluye InvalidTopicException e InvalidReplicationFactorException (subclases).
            case InvalidConfigurationException e -> Tipo.SOLICITUD_INVALIDA;
            // Incluye PolicyViolationException (subclase).
            case InvalidRequestException e -> Tipo.SOLICITUD_INVALIDA;
            case TimeoutException e -> Tipo.NO_DISPONIBLE;
            case null -> Tipo.ERROR;
            default -> Tipo.ERROR;
        };
        String mensaje = causa == null ? "Error desconocido de Kafka" : causa.getMessage();
        return new KafkaAdminException(tipo, mensaje);
    }
}

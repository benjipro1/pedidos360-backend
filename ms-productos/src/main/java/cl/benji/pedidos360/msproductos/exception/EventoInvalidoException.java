package cl.benji.pedidos360.msproductos.exception;

/**
 * Evento de Kafka que nunca podra procesarse, sin importar cuantas veces se
 * reintente (payload incompleto, producto inexistente). El error handler lo
 * marca como no reintentable y lo envia directo al DLT.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}

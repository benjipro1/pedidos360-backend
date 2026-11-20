package cl.benji.pedidos360.msanalitica.exception;

/**
 * Evento que nunca podra procesarse aunque se reintente (payload
 * incompleto). El error handler lo envia directo al DLT.
 */
public class EventoInvalidoException extends RuntimeException {

    public EventoInvalidoException(String mensaje) {
        super(mensaje);
    }
}

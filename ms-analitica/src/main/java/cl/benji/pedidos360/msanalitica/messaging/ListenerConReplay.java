package cl.benji.pedidos360.msanalitica.messaging;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import cl.benji.pedidos360.msanalitica.exception.EventoInvalidoException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerSeekAware;

/**
 * Base de los listeners de analitica.
 *
 * Replay: cuando Kafka le asigna particiones a un hilo consumidor, este
 * vuelve al primer offset retenido y relee todo. Asi los KPIs (en memoria)
 * se reconstruyen tras un reinicio sin base de datos: es posible porque
 * Kafka conserva los eventos segun la retencion de cada topico, a
 * diferencia de una cola que los borra al consumirlos.
 *
 * Tambien centraliza la validacion del payload.
 */
abstract class ListenerConReplay implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(ListenerConReplay.class);

    private final boolean replayAlIniciar;
    private final Validator validator;
    private final ReplayTracker replayTracker;
    private final String groupId;

    protected ListenerConReplay(boolean replayAlIniciar, Validator validator,
                                ReplayTracker replayTracker, String groupId) {
        this.replayAlIniciar = replayAlIniciar;
        this.validator = validator;
        this.replayTracker = replayTracker;
        this.groupId = groupId;
    }

    /**
     * Las posiciones que recibe este metodo son los offsets ya confirmados
     * por el group: se guardan como limite de la relectura antes de volver
     * al inicio.
     */
    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> asignadas, ConsumerSeekCallback callback) {
        if (replayAlIniciar && !asignadas.isEmpty()) {
            asignadas.forEach((particion, offset) -> replayTracker.registrarLimite(groupId, particion, offset));
            log.info("Replay: releyendo desde el inicio las particiones {} (ya confirmadas hasta {})",
                    asignadas.keySet(), asignadas.values());
            callback.seekToBeginning(asignadas.keySet());
        }
    }

    protected <T> void validar(T evento) {
        if (evento == null) {
            throw new EventoInvalidoException("Mensaje sin contenido (payload nulo)");
        }
        Set<ConstraintViolation<T>> errores = validator.validate(evento);
        if (!errores.isEmpty()) {
            String detalle = errores.stream()
                    .map(e -> e.getPropertyPath() + " " + e.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new EventoInvalidoException("Evento invalido: " + detalle);
        }
    }
}

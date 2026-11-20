package cl.benji.pedidos360.msanalitica.messaging;

import cl.benji.pedidos360.msanalitica.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msanalitica.service.AnaliticaService;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group "analitica-inventario" sobre inventario.movimientos:
 * calcula los productos mas vendidos.
 *
 * Es un group distinto de "productos-stock" (ms-productos), asi que ambos
 * reciben TODOS los movimientos (broadcast entre grupos), cada uno con sus
 * propios offsets y su propio lag.
 */
@Component
public class MovimientoInventarioListener extends ListenerConReplay {

    private static final Logger log = LoggerFactory.getLogger(MovimientoInventarioListener.class);

    private final AnaliticaService analiticaService;

    public MovimientoInventarioListener(AnaliticaService analiticaService, Validator validator,
                                        ReplayTracker replayTracker,
                                        @Value("${kafka.grupos.inventario}") String groupId,
                                        @Value("${analitica.replay-al-iniciar:true}") boolean replayAlIniciar) {
        super(replayAlIniciar, validator, replayTracker, groupId);
        this.analiticaService = analiticaService;
    }

    @KafkaListener(
            id = "analitica-movimientos",
            topics = "${kafka.topicos.movimientos}",
            groupId = "${kafka.grupos.inventario}",
            concurrency = "${kafka.consumidores.concurrencia:3}",
            containerFactory = "movimientosListenerFactory")
    public void recibir(ConsumerRecord<String, MovimientoInventarioEvento> registro) {
        MovimientoInventarioEvento movimiento = registro.value();
        MDC.put("kafka", registro.topic() + "-" + registro.partition() + "@" + registro.offset());
        try {
            validar(movimiento);
            log.info("Movimiento {}-{}@{} (key={}): {} producto {} x{} pedido {} eventId={}",
                    registro.topic(), registro.partition(), registro.offset(), registro.key(),
                    movimiento.tipo(), movimiento.productoId(), movimiento.cantidad(),
                    movimiento.pedidoId(), movimiento.eventId());
            analiticaService.registrarMovimiento(movimiento);
        } finally {
            MDC.clear();
        }
    }
}

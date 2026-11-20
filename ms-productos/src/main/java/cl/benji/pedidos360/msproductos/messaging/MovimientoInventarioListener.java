package cl.benji.pedidos360.msproductos.messaging;

import java.util.Set;
import java.util.stream.Collectors;

import cl.benji.pedidos360.msproductos.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msproductos.exception.EventoInvalidoException;
import cl.benji.pedidos360.msproductos.service.StockService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Adaptador de mensajeria: recibe movimientos de inventario.movimientos,
 * valida el payload, deja el contexto en el log y delega en
 * {@link StockService}, que contiene la logica de dominio.
 *
 * Consumer group "productos-stock" con concurrencia 3 (un hilo por
 * particion). Si se levanta otra instancia de ms-productos con el mismo
 * group, Kafka reparte las 3 particiones entre ambas (balanceo de carga) y,
 * si una cae, la otra toma sus particiones (resiliencia).
 */
@Component
public class MovimientoInventarioListener {

    private static final Logger log = LoggerFactory.getLogger(MovimientoInventarioListener.class);

    private final StockService stockService;
    private final Validator validator;

    public MovimientoInventarioListener(StockService stockService, Validator validator) {
        this.stockService = stockService;
        this.validator = validator;
    }

    @KafkaListener(
            id = "stock-movimientos",
            topics = "${kafka.topicos.movimientos}",
            groupId = "${kafka.grupos.stock}",
            concurrency = "${kafka.consumidores.concurrencia:3}",
            containerFactory = "movimientosListenerFactory")
    public void recibir(ConsumerRecord<String, MovimientoInventarioEvento> registro) {
        MovimientoInventarioEvento movimiento = registro.value();
        MDC.put("kafka", registro.topic() + "-" + registro.partition() + "@" + registro.offset());
        try {
            validar(movimiento);
            MDC.put("eventId", String.valueOf(movimiento.eventId()));
            log.info("Movimiento recibido de {}-{}@{} (key={}): {} producto {} x{} pedido {} eventId={}",
                    registro.topic(), registro.partition(), registro.offset(), registro.key(),
                    movimiento.tipo(), movimiento.productoId(), movimiento.cantidad(),
                    movimiento.pedidoId(), movimiento.eventId());
            stockService.aplicarMovimiento(movimiento);
        } finally {
            MDC.clear();
        }
    }

    private void validar(MovimientoInventarioEvento movimiento) {
        if (movimiento == null) {
            throw new EventoInvalidoException("Mensaje sin contenido (payload nulo)");
        }
        Set<ConstraintViolation<MovimientoInventarioEvento>> errores = validator.validate(movimiento);
        if (!errores.isEmpty()) {
            String detalle = errores.stream()
                    .map(e -> e.getPropertyPath() + " " + e.getMessage())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new EventoInvalidoException("Movimiento invalido: " + detalle);
        }
    }
}

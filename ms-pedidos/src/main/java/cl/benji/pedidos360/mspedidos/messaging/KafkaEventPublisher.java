package cl.benji.pedidos360.mspedidos.messaging;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.benji.pedidos360.mspedidos.config.KafkaTopicsProperties;
import cl.benji.pedidos360.mspedidos.dto.evento.ItemPedidoEvento;
import cl.benji.pedidos360.mspedidos.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.mspedidos.dto.evento.PedidoEvento;
import cl.benji.pedidos360.mspedidos.dto.evento.TipoEventoPedido;
import cl.benji.pedidos360.mspedidos.dto.evento.TipoMovimiento;
import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;
import cl.benji.pedidos360.mspedidos.entity.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica en Kafka el flujo de eventos de pedidos. Complementa a
 * {@link PedidoEventPublisher} (RabbitMQ) sin reemplazarlo:
 *
 * - RabbitMQ: tareas puntuales (aviso, documento, auditoria); cada mensaje
 *   se consume una vez y sale de la cola.
 * - Kafka: log de eventos retenido por dias, que varios consumer groups
 *   leen de forma independiente (stock y analitica) y que se puede volver a
 *   leer desde el inicio (replay).
 *
 * Topicos:
 * - pedidos.eventos (key pedidoId): un evento por creacion o cambio de estado.
 * - inventario.movimientos (key productoId): un movimiento por linea.
 *
 * Regla de stock: un pedido compromete stock mientras NO este CANCELADO.
 * Al pasar a CANCELADO se publican devoluciones, y si un pedido cancelado
 * se reactiva se vuelven a publicar salidas.
 *
 * Igual que con RabbitMQ, la publicacion es "best effort": el pedido ya esta
 * guardado y una falla de Kafka nunca lo revierte ni devuelve un 500. El
 * envio es asincrono; max.block.ms acotado evita que un broker caido deje
 * colgada la peticion HTTP.
 */
@Component
public class KafkaEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final KafkaTopicsProperties topicos;

    public KafkaEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, KafkaTopicsProperties topicos) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicos = topicos;
    }

    public void publicarCreacion(Pedido pedido) {
        publicarEventoPedido(pedido, TipoEventoPedido.PEDIDO_CREADO, null);
        publicarMovimientos(pedido, TipoMovimiento.SALIDA_VENTA);
    }

    public void publicarCambioEstado(Pedido pedido, EstadoPedido estadoAnterior) {
        publicarEventoPedido(pedido, TipoEventoPedido.PEDIDO_ESTADO_CAMBIADO, estadoAnterior);

        boolean eraCancelado = estadoAnterior == EstadoPedido.CANCELADO;
        boolean esCancelado = pedido.getEstado() == EstadoPedido.CANCELADO;
        if (!eraCancelado && esCancelado) {
            publicarMovimientos(pedido, TipoMovimiento.DEVOLUCION_CANCELACION);
        } else if (eraCancelado && !esCancelado) {
            publicarMovimientos(pedido, TipoMovimiento.SALIDA_VENTA);
        }
    }

    private void publicarEventoPedido(Pedido pedido, TipoEventoPedido tipo, EstadoPedido estadoAnterior) {
        List<ItemPedidoEvento> items = pedido.getDetalles().stream()
                .map(d -> new ItemPedidoEvento(d.getProductoId(), d.getNombreProducto(),
                        d.getCantidad(), d.getPrecioUnitario()))
                .toList();

        PedidoEvento evento = new PedidoEvento(
                UUID.randomUUID(),
                tipo,
                PedidoEvento.VERSION_ACTUAL,
                Instant.now(),
                pedido.getId(),
                pedido.getUsuarioEmail(),
                pedido.getEstado(),
                estadoAnterior,
                pedido.getTotal(),
                pedido.getFechaCreacion(),
                items
        );
        enviar(topicos.getPedidos(), String.valueOf(pedido.getId()), evento,
                "pedido " + pedido.getId() + " " + tipo + " eventId=" + evento.eventId());
    }

    private void publicarMovimientos(Pedido pedido, TipoMovimiento tipo) {
        pedido.getDetalles().forEach(detalle -> {
            MovimientoInventarioEvento movimiento = new MovimientoInventarioEvento(
                    UUID.randomUUID(),
                    tipo,
                    MovimientoInventarioEvento.VERSION_ACTUAL,
                    Instant.now(),
                    detalle.getProductoId(),
                    detalle.getNombreProducto(),
                    detalle.getCantidad(),
                    pedido.getId()
            );
            enviar(topicos.getMovimientos(), String.valueOf(detalle.getProductoId()), movimiento,
                    "movimiento " + tipo + " producto " + detalle.getProductoId()
                            + " x" + detalle.getCantidad() + " (pedido " + pedido.getId()
                            + ") eventId=" + movimiento.eventId());
        });
    }

    private void enviar(String topico, String key, Object evento, String descripcion) {
        try {
            kafkaTemplate.send(topico, key, evento).whenComplete((resultado, ex) -> {
                if (ex != null) {
                    log.error("Kafka: no se pudo publicar {} en {} (key={})", descripcion, topico, key, ex);
                } else {
                    var metadata = resultado.getRecordMetadata();
                    log.info("Kafka: publicado {} en {}-{} offset {} (key={})", descripcion,
                            metadata.topic(), metadata.partition(), metadata.offset(), key);
                }
            });
        } catch (Exception ex) {
            // send() puede fallar de forma sincronica (por ejemplo, sin
            // metadata del topico tras max.block.ms). Se registra y se sigue.
            log.error("Kafka: no se pudo publicar {} en {} (key={})", descripcion, topico, key, ex);
        }
    }
}

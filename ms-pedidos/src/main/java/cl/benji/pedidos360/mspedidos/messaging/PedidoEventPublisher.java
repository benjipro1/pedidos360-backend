package cl.benji.pedidos360.mspedidos.messaging;

import cl.benji.pedidos360.mspedidos.config.RabbitMqProducerProperties;
import cl.benji.pedidos360.mspedidos.dto.PedidoEventoDTO;
import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;
import cl.benji.pedidos360.mspedidos.entity.Pedido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publica eventos de dominio de Pedido hacia RabbitMQ. No conoce colas:
 * solo exchanges y routing keys, que son el contrato publico del mensaje.
 *
 * - pedido.creado  -> direct exchange (dispara notificaciones) + topic (auditoria)
 * - pedido.pagado  -> direct exchange (dispara generacion de documento) + topic (auditoria)
 * - cualquier otro cambio de estado -> solo topic exchange (auditoria), con
 *   routing key "pedido.<estado-en-minuscula>", capturado por el patron pedido.#
 */
@Component
public class PedidoEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMqProducerProperties props;

    public PedidoEventPublisher(RabbitTemplate rabbitTemplate, RabbitMqProducerProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publicarCreacion(Pedido pedido) {
        PedidoEventoDTO evento = toEvento(pedido);
        String routingKey = props.getRoutingKeys().getPedidoCreado();

        rabbitTemplate.convertAndSend(props.getExchanges().getDirect(), routingKey, evento);
        rabbitTemplate.convertAndSend(props.getExchanges().getTopic(), routingKey, evento);

        log.info("Evento publicado: pedido {} creado (routingKey={})", pedido.getId(), routingKey);
    }

    public void publicarCambioEstado(Pedido pedido) {
        PedidoEventoDTO evento = toEvento(pedido);
        String routingKeyAuditoria = "pedido." + pedido.getEstado().name().toLowerCase();

        // Auditoria: SIEMPRE se publica, para cualquier estado
        rabbitTemplate.convertAndSend(props.getExchanges().getTopic(), routingKeyAuditoria, evento);
        log.info("Evento publicado: pedido {} cambio a {} (auditoria, routingKey={})",
                pedido.getId(), pedido.getEstado(), routingKeyAuditoria);

        // Negocio: solo PAGADO dispara generacion de documento
        if (pedido.getEstado() == EstadoPedido.PAGADO) {
            String routingKeyPago = props.getRoutingKeys().getPedidoPagado();
            rabbitTemplate.convertAndSend(props.getExchanges().getDirect(), routingKeyPago, evento);
            log.info("Evento publicado: pedido {} pagado (documento, routingKey={})", pedido.getId(), routingKeyPago);
        }
    }

    private PedidoEventoDTO toEvento(Pedido pedido) {
        return new PedidoEventoDTO(
                pedido.getId(),
                pedido.getUsuarioEmail(),
                pedido.getEstado(),
                pedido.getTotal(),
                Instant.now()
        );
    }
}
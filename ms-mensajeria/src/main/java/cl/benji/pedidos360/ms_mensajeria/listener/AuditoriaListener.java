package cl.benji.pedidos360.ms_mensajeria.listener;

import cl.benji.pedidos360.ms_mensajeria.dto.PedidoEventoDTO;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class AuditoriaListener {

    @RabbitListener(queues = "${rabbitmq.queues.auditoria}")
    public void recibir(PedidoEventoDTO evento, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            log.info("AUDITORIA -> pedido {} | usuario {} | estado {} | total {} | fecha {}",
                    evento.getPedidoId(), evento.getUsuarioEmail(), evento.getEstado(),
                    evento.getTotal(), evento.getFechaEvento());

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Error registrando auditoria del pedido {}: {}", evento.getPedidoId(), e.getMessage());
            channel.basicNack(tag, false, false);
        }
    }
}
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
public class NotificacionListener {

    @RabbitListener(queues = "${rabbitmq.queues.notificaciones}")
    public void recibir(PedidoEventoDTO evento, Channel channel,
                         @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            if (evento.getUsuarioEmail() == null || evento.getUsuarioEmail().isBlank()) {
                throw new IllegalArgumentException("No se puede notificar: usuarioEmail vacio");
            }

            log.info("NOTIFICACION -> enviando correo a {} por pedido {} (estado={})",
                    evento.getUsuarioEmail(), evento.getPedidoId(), evento.getEstado());

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("Error procesando notificacion del pedido {}: {}", evento.getPedidoId(), e.getMessage());
            // requeue=false: el mensaje se descarta de esta cola y, como tiene
            // configurado x-dead-letter-exchange, cae en notificaciones.dlq
            channel.basicNack(tag, false, false);
        }
    }
}
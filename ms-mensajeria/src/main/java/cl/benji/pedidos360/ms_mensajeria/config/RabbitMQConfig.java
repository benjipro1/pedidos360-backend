package cl.benji.pedidos360.ms_mensajeria.config;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    private final RabbitMqProperties props;

    // ---------------- Exchanges ----------------

    @Bean
    DirectExchange pedidosDirectExchange() {
        return new DirectExchange(props.getExchanges().getDirect(), true, false);
    }

    @Bean
    TopicExchange pedidosTopicExchange() {
        return new TopicExchange(props.getExchanges().getTopic(), true, false);
    }

    @Bean
    DirectExchange pedidosDlx() {
        return new DirectExchange(props.getExchanges().getDlx(), true, false);
    }

    // ---------------- Cola: notificaciones ----------------

    @Bean
    Queue notificacionesQueue() {
        return QueueBuilder.durable(props.getQueues().getNotificaciones())
            .withArgument("x-dead-letter-exchange", props.getExchanges().getDlx())
            .withArgument("x-dead-letter-routing-key", "notificaciones.dead")
            .build();
    }

    @Bean
    Queue notificacionesDlq() {
        return QueueBuilder.durable(props.getDlq().getNotificaciones()).build();
    }

    @Bean
    Binding bindNotificaciones(Queue notificacionesQueue, DirectExchange pedidosDirectExchange) {
        return BindingBuilder.bind(notificacionesQueue)
            .to(pedidosDirectExchange).with(props.getRoutingKeys().getPedidoCreado());
    }

    @Bean
    Binding bindNotificacionesDlq(Queue notificacionesDlq, DirectExchange pedidosDlx) {
        return BindingBuilder.bind(notificacionesDlq).to(pedidosDlx).with("notificaciones.dead");
    }

    // ---------------- Cola: documentos ----------------

    @Bean
    Queue documentosQueue() {
        return QueueBuilder.durable(props.getQueues().getDocumentos())
            .withArgument("x-dead-letter-exchange", props.getExchanges().getDlx())
            .withArgument("x-dead-letter-routing-key", "documentos.dead")
            .build();
    }

    @Bean
    Queue documentosDlq() {
        return QueueBuilder.durable(props.getDlq().getDocumentos()).build();
    }

    @Bean
    Binding bindDocumentos(Queue documentosQueue, DirectExchange pedidosDirectExchange) {
        return BindingBuilder.bind(documentosQueue)
            .to(pedidosDirectExchange).with(props.getRoutingKeys().getPedidoPagado());
    }

    @Bean
    Binding bindDocumentosDlq(Queue documentosDlq, DirectExchange pedidosDlx) {
        return BindingBuilder.bind(documentosDlq).to(pedidosDlx).with("documentos.dead");
    }

    // ---------------- Cola: auditoria (topic) ----------------

    @Bean
    Queue auditoriaQueue() {
        return QueueBuilder.durable(props.getQueues().getAuditoria())
            .withArgument("x-dead-letter-exchange", props.getExchanges().getDlx())
            .withArgument("x-dead-letter-routing-key", "auditoria.dead")
            .build();
    }

    @Bean
    Queue auditoriaDlq() {
        return QueueBuilder.durable(props.getDlq().getAuditoria()).build();
    }

    @Bean
    Binding bindAuditoria(Queue auditoriaQueue, TopicExchange pedidosTopicExchange) {
        return BindingBuilder.bind(auditoriaQueue)
            .to(pedidosTopicExchange).with(props.getRoutingKeys().getAuditoriaPattern());
    }

    @Bean
    Binding bindAuditoriaDlq(Queue auditoriaDlq, DirectExchange pedidosDlx) {
        return BindingBuilder.bind(auditoriaDlq).to(pedidosDlx).with("auditoria.dead");
    }
}
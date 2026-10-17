package cl.benji.pedidos360.ms_mensajeria.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rabbitmq")
@Getter
@Setter
public class RabbitMqProperties {

    private Exchanges exchanges = new Exchanges();
    private Queues queues = new Queues();
    private Queues dlq = new Queues();
    private RoutingKeys routingKeys = new RoutingKeys();

    @Getter
    @Setter
    public static class Exchanges {
        private String direct;
        private String topic;
        private String dlx;
    }

    @Getter
    @Setter
    public static class Queues {
        private String notificaciones;
        private String documentos;
        private String auditoria;
    }

    @Getter
    @Setter
    public static class RoutingKeys {
        private String pedidoCreado;
        private String pedidoPagado;
        private String auditoriaPattern;
    }
}
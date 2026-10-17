package cl.benji.pedidos360.mspedidos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nombres de exchanges y routing keys usados por ms-pedidos para PUBLICAR
 * eventos. Deben coincidir exactamente con los declarados por ms-mensajeria,
 * que es quien crea la topologia real en el broker.
 */
@Component
@ConfigurationProperties(prefix = "rabbitmq")
public class RabbitMqProducerProperties {

    private Exchanges exchanges = new Exchanges();
    private RoutingKeys routingKeys = new RoutingKeys();

    public Exchanges getExchanges() { return exchanges; }
    public void setExchanges(Exchanges exchanges) { this.exchanges = exchanges; }

    public RoutingKeys getRoutingKeys() { return routingKeys; }
    public void setRoutingKeys(RoutingKeys routingKeys) { this.routingKeys = routingKeys; }

    public static class Exchanges {
        private String direct;
        private String topic;

        public String getDirect() { return direct; }
        public void setDirect(String direct) { this.direct = direct; }

        public String getTopic() { return topic; }
        public void setTopic(String topic) { this.topic = topic; }
    }

    public static class RoutingKeys {
        private String pedidoCreado;
        private String pedidoPagado;

        public String getPedidoCreado() { return pedidoCreado; }
        public void setPedidoCreado(String pedidoCreado) { this.pedidoCreado = pedidoCreado; }

        public String getPedidoPagado() { return pedidoPagado; }
        public void setPedidoPagado(String pedidoPagado) { this.pedidoPagado = pedidoPagado; }
    }
}
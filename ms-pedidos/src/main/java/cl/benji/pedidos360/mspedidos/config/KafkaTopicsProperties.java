package cl.benji.pedidos360.mspedidos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nombres de los topicos donde ms-pedidos PUBLICA. Los topicos los crea
 * ms-mensajeria (administrador de Kafka) o scripts/kafka-crear-topicos.sh;
 * los brokers tienen auto.create.topics.enable=false.
 */
@Component
@ConfigurationProperties(prefix = "kafka.topicos")
public class KafkaTopicsProperties {

    private String pedidos;
    private String movimientos;

    public String getPedidos() { return pedidos; }
    public void setPedidos(String pedidos) { this.pedidos = pedidos; }

    public String getMovimientos() { return movimientos; }
    public void setMovimientos(String movimientos) { this.movimientos = movimientos; }
}

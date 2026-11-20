package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

import java.util.List;

public record ClusterInfo(String clusterId, Integer controllerId, List<Broker> brokers) {

    public record Broker(int id, String host, int puerto, String rack) {
    }
}

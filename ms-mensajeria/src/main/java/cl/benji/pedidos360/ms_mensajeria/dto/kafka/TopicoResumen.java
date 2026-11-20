package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

public record TopicoResumen(String nombre, int particiones, int replicas, boolean interno) {
}

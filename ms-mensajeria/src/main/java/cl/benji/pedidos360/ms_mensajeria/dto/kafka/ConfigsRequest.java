package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

import java.util.List;
import java.util.Map;

/**
 * Modificacion incremental de configs de un topico: "set" agrega o cambia
 * valores y "eliminar" vuelve esas claves al valor por defecto del broker.
 * Ejemplo: {"set":{"retention.ms":"86400000"},"eliminar":["max.message.bytes"]}
 */
public record ConfigsRequest(
        Map<String, String> set,
        List<String> eliminar
) {
}

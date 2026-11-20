package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Nuevo total de particiones. Kafka solo permite AUMENTARLAS: reducirlas
 * romperia el orden por key de los mensajes ya escritos.
 */
public record ParticionesRequest(@Min(1) @Max(50) int total) {
}

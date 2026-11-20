package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Mueve los offsets de un consumer group en un topico. El group debe estar
 * sin consumidores activos (detener el servicio antes).
 * INICIO = reprocesar todo lo retenido; FIN = saltar lo pendiente.
 */
public record ResetOffsetsRequest(@NotBlank String topico, @NotNull Posicion posicion) {

    public enum Posicion { INICIO, FIN }
}

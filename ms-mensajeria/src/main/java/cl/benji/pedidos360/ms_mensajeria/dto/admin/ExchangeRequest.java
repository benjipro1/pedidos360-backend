package cl.benji.pedidos360.ms_mensajeria.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ExchangeRequest(
        @NotBlank String nombre,
        @Pattern(regexp = "direct|topic|fanout|headers", message = "tipo debe ser direct, topic, fanout o headers")
        String tipo,
        boolean durable,
        boolean autoDelete
) {
}
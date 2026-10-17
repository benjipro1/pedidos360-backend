package cl.benji.pedidos360.ms_mensajeria.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record QueueRequest(
        @NotBlank String nombre,
        boolean durable,
        boolean exclusive,
        boolean autoDelete
) {
}
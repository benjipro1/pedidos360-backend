package cl.benji.pedidos360.ms_mensajeria.dto.admin;

import jakarta.validation.constraints.NotBlank;

public record BindingRequest(
        @NotBlank String exchange,
        @NotBlank String cola,
        @NotBlank String routingKey
) {
}
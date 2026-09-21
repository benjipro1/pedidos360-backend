package cl.benji.pedidos360.mspedidos.dto;

import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record EstadoRequest(

        @NotNull(message = "el estado es obligatorio")
        EstadoPedido estado
) {
}

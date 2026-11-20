package cl.benji.pedidos360.msanalitica.dto.evento;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemPedidoEvento(
        @NotNull Long productoId,
        String nombreProducto,
        @NotNull @Positive Integer cantidad,
        @NotNull Integer precioUnitario
) {
}

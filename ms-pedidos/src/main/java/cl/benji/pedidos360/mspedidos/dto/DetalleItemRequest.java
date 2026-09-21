package cl.benji.pedidos360.mspedidos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Un item dentro de un pedido nuevo. No incluye subtotal: el service lo
 * calcula a partir de cantidad y precioUnitario.
 */
public record DetalleItemRequest(

        @NotNull(message = "el productoId es obligatorio")
        Long productoId,

        @NotBlank(message = "el nombre del producto es obligatorio")
        String nombreProducto,

        @NotNull(message = "la cantidad es obligatoria")
        @Min(value = 1, message = "la cantidad debe ser al menos 1")
        Integer cantidad,

        @NotNull(message = "el precio unitario es obligatorio")
        @PositiveOrZero(message = "el precio unitario no puede ser negativo")
        Integer precioUnitario
) {
}

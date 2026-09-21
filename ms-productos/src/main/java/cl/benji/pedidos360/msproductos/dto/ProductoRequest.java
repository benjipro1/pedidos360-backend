package cl.benji.pedidos360.msproductos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * DTO de entrada para crear o actualizar un producto.
 * {@code activo} es opcional: si no se envía, el servicio decide el valor
 * por defecto (true al crear, se mantiene el valor actual al actualizar).
 */
public record ProductoRequest(

        @NotBlank(message = "el nombre es obligatorio")
        @Size(max = 120, message = "el nombre no puede superar los 120 caracteres")
        String nombre,

        @Size(max = 500, message = "la descripcion no puede superar los 500 caracteres")
        String descripcion,

        @NotNull(message = "el precio es obligatorio")
        @PositiveOrZero(message = "el precio no puede ser negativo")
        Integer precio,

        @NotNull(message = "el stock es obligatorio")
        @PositiveOrZero(message = "el stock no puede ser negativo")
        Integer stock,

        Boolean activo
) {
}

package cl.benji.pedidos360.msproductos.dto.evento;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Movimiento de stock recibido desde inventario.movimientos (key =
 * productoId). Es la vista de ms-productos del evento que publica
 * ms-pedidos: mismo JSON, clase propia, sin dependencia entre servicios.
 *
 * Las anotaciones de validacion se revisan en el listener antes de tocar el
 * stock: un payload incompleto va directo al DLT sin reintentos.
 */
public record MovimientoInventarioEvento(
        @NotNull UUID eventId,
        @NotNull TipoMovimiento tipo,
        @Min(1) int version,
        @NotNull Instant fechaEvento,
        @NotNull @Positive Long productoId,
        String nombreProducto,
        @NotNull @Positive Integer cantidad,
        @NotNull @Positive Long pedidoId
) {
}

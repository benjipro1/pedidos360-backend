package cl.benji.pedidos360.msanalitica.dto.evento;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Movimiento de inventario.movimientos visto por la analitica. */
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

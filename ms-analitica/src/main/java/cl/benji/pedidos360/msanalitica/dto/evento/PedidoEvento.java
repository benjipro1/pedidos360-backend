package cl.benji.pedidos360.msanalitica.dto.evento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Evento de pedidos.eventos visto por la analitica (mismo JSON que publica
 * ms-pedidos, clase propia de este servicio).
 */
public record PedidoEvento(
        @NotNull UUID eventId,
        @NotNull TipoEventoPedido tipo,
        @Min(1) int version,
        @NotNull Instant fechaEvento,
        @NotNull Long pedidoId,
        String usuarioEmail,
        @NotNull EstadoPedido estado,
        EstadoPedido estadoAnterior,
        @NotNull @PositiveOrZero Integer total,
        Instant fechaCreacion,
        @NotNull List<@Valid ItemPedidoEvento> items
) {
}

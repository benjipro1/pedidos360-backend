package cl.benji.pedidos360.mspedidos.dto;

import java.time.Instant;
import java.util.List;

import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;

public record PedidoResponse(
        Long id,
        String usuarioEmail,
        Instant fechaCreacion,
        EstadoPedido estado,
        Integer total,
        List<DetallePedidoResponse> detalles
) {
}

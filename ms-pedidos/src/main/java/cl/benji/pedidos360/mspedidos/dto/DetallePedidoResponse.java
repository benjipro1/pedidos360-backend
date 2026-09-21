package cl.benji.pedidos360.mspedidos.dto;

public record DetallePedidoResponse(
        Long id,
        Long productoId,
        String nombreProducto,
        Integer cantidad,
        Integer precioUnitario
) {
}

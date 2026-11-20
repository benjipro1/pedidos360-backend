package cl.benji.pedidos360.mspedidos.dto.evento;

/** Linea de un pedido dentro de un {@link PedidoEvento}. */
public record ItemPedidoEvento(
        Long productoId,
        String nombreProducto,
        Integer cantidad,
        Integer precioUnitario
) {
}

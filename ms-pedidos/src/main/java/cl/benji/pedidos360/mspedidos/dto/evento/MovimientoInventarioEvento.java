package cl.benji.pedidos360.mspedidos.dto.evento;

import java.time.Instant;
import java.util.UUID;

/**
 * Movimiento de stock de UN producto, publicado en inventario.movimientos
 * con key = productoId: todos los movimientos de un producto caen en la
 * misma particion, asi que ms-productos los aplica en orden y sin carreras
 * entre hilos sobre la misma fila.
 *
 * Un pedido con 3 lineas genera 3 movimientos (uno por producto).
 */
public record MovimientoInventarioEvento(
        UUID eventId,
        TipoMovimiento tipo,
        int version,
        Instant fechaEvento,
        Long productoId,
        String nombreProducto,
        Integer cantidad,
        Long pedidoId
) {
    public static final int VERSION_ACTUAL = 1;
}

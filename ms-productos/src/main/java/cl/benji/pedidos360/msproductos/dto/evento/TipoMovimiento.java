package cl.benji.pedidos360.msproductos.dto.evento;

/**
 * Sentido de un movimiento de inventario publicado por ms-pedidos. La
 * cantidad del evento siempre es positiva: el tipo indica si el stock baja
 * o sube.
 */
public enum TipoMovimiento {
    /** Un pedido activo compromete unidades: el stock baja. */
    SALIDA_VENTA,
    /** Un pedido se cancela: se devuelven las unidades que se descontaron. */
    DEVOLUCION_CANCELACION
}

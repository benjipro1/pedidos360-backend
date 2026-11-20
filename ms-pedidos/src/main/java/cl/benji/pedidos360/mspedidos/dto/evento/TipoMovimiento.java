package cl.benji.pedidos360.mspedidos.dto.evento;

/**
 * Sentido de un movimiento de inventario. La cantidad del evento siempre es
 * positiva: el tipo indica si el stock baja o sube.
 */
public enum TipoMovimiento {
    /** Un pedido activo compromete unidades: el stock baja. */
    SALIDA_VENTA,
    /** Un pedido se cancela: se devuelven las unidades que se descontaron. */
    DEVOLUCION_CANCELACION
}

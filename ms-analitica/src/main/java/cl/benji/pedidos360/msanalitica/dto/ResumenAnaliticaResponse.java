package cl.benji.pedidos360.msanalitica.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import cl.benji.pedidos360.msanalitica.dto.evento.EstadoPedido;
import cl.benji.pedidos360.msanalitica.dto.evento.TipoMovimiento;

/**
 * KPIs expuestos en GET /analitica/resumen.
 *
 * @param ventasTotales      suma (CLP) de los pedidos no cancelados
 * @param ticketPromedio     ventasTotales / pedidosActivos
 * @param topProductos       productos con mas unidades netas vendidas
 *                           (salidas menos devoluciones)
 * @param ultimoEvento       instante del evento mas reciente procesado
 * @param generadoEn         instante en que se calculo este resumen
 */
public record ResumenAnaliticaResponse(
        long ventasTotales,
        long totalPedidos,
        long pedidosActivos,
        long ticketPromedio,
        Map<EstadoPedido, Long> pedidosPorEstado,
        List<ProductoVendido> topProductos,
        Map<TipoMovimiento, Long> movimientosPorTipo,
        long eventosPedidoProcesados,
        long movimientosProcesados,
        long duplicadosIgnorados,
        Instant ultimoEvento,
        Instant generadoEn
) {
    public record ProductoVendido(Long productoId, String nombre, long unidades) {
    }
}

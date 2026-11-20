package cl.benji.pedidos360.msanalitica.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.benji.pedidos360.msanalitica.dto.ResumenAnaliticaResponse;
import cl.benji.pedidos360.msanalitica.dto.evento.EstadoPedido;
import cl.benji.pedidos360.msanalitica.dto.evento.ItemPedidoEvento;
import cl.benji.pedidos360.msanalitica.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msanalitica.dto.evento.PedidoEvento;
import cl.benji.pedidos360.msanalitica.dto.evento.TipoEventoPedido;
import cl.benji.pedidos360.msanalitica.dto.evento.TipoMovimiento;
import org.junit.jupiter.api.Test;

class AnaliticaServiceTest {

    private final AnaliticaService servicio = new AnaliticaService();
    private final Instant t0 = Instant.parse("2026-11-20T12:00:00Z");

    @Test
    void calculaVentasTicketYEstadosExcluyendoCancelados() {
        servicio.registrarEventoPedido(pedido(1L, EstadoPedido.PENDIENTE, null, 10_000, t0));
        servicio.registrarEventoPedido(pedido(2L, EstadoPedido.PENDIENTE, null, 20_000, t0));
        servicio.registrarEventoPedido(pedido(2L, EstadoPedido.CANCELADO, EstadoPedido.PENDIENTE, 20_000,
                t0.plusSeconds(5)));
        servicio.registrarEventoPedido(pedido(3L, EstadoPedido.PAGADO, null, 30_000, t0));

        ResumenAnaliticaResponse resumen = servicio.resumen();

        assertThat(resumen.totalPedidos()).isEqualTo(3);
        assertThat(resumen.pedidosActivos()).isEqualTo(2);
        assertThat(resumen.ventasTotales()).isEqualTo(40_000);
        assertThat(resumen.ticketPromedio()).isEqualTo(20_000);
        assertThat(resumen.pedidosPorEstado())
                .containsEntry(EstadoPedido.PENDIENTE, 1L)
                .containsEntry(EstadoPedido.CANCELADO, 1L)
                .containsEntry(EstadoPedido.PAGADO, 1L)
                .containsEntry(EstadoPedido.ENVIADO, 0L);
    }

    @Test
    void releerElMismoEventoNoDuplicaKpis() {
        PedidoEvento evento = pedido(1L, EstadoPedido.PENDIENTE, null, 10_000, t0);
        MovimientoInventarioEvento movimiento = movimiento(TipoMovimiento.SALIDA_VENTA, 7L, 2);

        servicio.registrarEventoPedido(evento);
        servicio.registrarEventoPedido(evento);
        servicio.registrarMovimiento(movimiento);
        servicio.registrarMovimiento(movimiento);

        ResumenAnaliticaResponse resumen = servicio.resumen();
        assertThat(resumen.ventasTotales()).isEqualTo(10_000);
        assertThat(resumen.topProductos()).singleElement()
                .satisfies(p -> assertThat(p.unidades()).isEqualTo(2));
        assertThat(resumen.duplicadosIgnorados()).isEqualTo(2);
    }

    @Test
    void unEventoAntiguoNoRetrocedeElEstado() {
        servicio.registrarEventoPedido(pedido(1L, EstadoPedido.PAGADO, EstadoPedido.PENDIENTE, 10_000,
                t0.plusSeconds(10)));
        servicio.registrarEventoPedido(pedido(1L, EstadoPedido.PENDIENTE, null, 10_000, t0));

        assertThat(servicio.resumen().pedidosPorEstado()).containsEntry(EstadoPedido.PAGADO, 1L);
    }

    @Test
    void topProductosRestaDevoluciones() {
        servicio.registrarMovimiento(movimiento(TipoMovimiento.SALIDA_VENTA, 1L, 5));
        servicio.registrarMovimiento(movimiento(TipoMovimiento.SALIDA_VENTA, 2L, 3));
        servicio.registrarMovimiento(movimiento(TipoMovimiento.DEVOLUCION_CANCELACION, 1L, 4));

        List<ResumenAnaliticaResponse.ProductoVendido> top = servicio.resumen().topProductos();

        assertThat(top).extracting(ResumenAnaliticaResponse.ProductoVendido::productoId).containsExactly(2L, 1L);
        assertThat(top).extracting(ResumenAnaliticaResponse.ProductoVendido::unidades).containsExactly(3L, 1L);
    }

    private PedidoEvento pedido(Long id, EstadoPedido estado, EstadoPedido anterior, int total, Instant fecha) {
        TipoEventoPedido tipo = anterior == null ? TipoEventoPedido.PEDIDO_CREADO : TipoEventoPedido.PEDIDO_ESTADO_CAMBIADO;
        return new PedidoEvento(UUID.randomUUID(), tipo, 1, fecha, id, "cliente@duoc.cl", estado, anterior,
                total, t0, List.of(new ItemPedidoEvento(1L, "Taladro", 1, total)));
    }

    private MovimientoInventarioEvento movimiento(TipoMovimiento tipo, Long productoId, int cantidad) {
        return new MovimientoInventarioEvento(UUID.randomUUID(), tipo, 1, t0, productoId,
                "Producto " + productoId, cantidad, 99L);
    }
}

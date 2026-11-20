package cl.benji.pedidos360.msanalitica.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import cl.benji.pedidos360.msanalitica.dto.ResumenAnaliticaResponse;
import cl.benji.pedidos360.msanalitica.dto.ResumenAnaliticaResponse.ProductoVendido;
import cl.benji.pedidos360.msanalitica.dto.evento.EstadoPedido;
import cl.benji.pedidos360.msanalitica.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msanalitica.dto.evento.PedidoEvento;
import cl.benji.pedidos360.msanalitica.dto.evento.TipoMovimiento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * KPIs de ventas e inventario calculados en tiempo real a partir de los
 * eventos de Kafka. No conoce Kafka: los listeners le entregan eventos ya
 * validados.
 *
 * El estado vive en memoria y se reconstruye al arrancar releyendo los
 * topicos desde el inicio (replay). Por eso cada operacion es idempotente:
 * un eventId ya visto se ignora, y el estado de un pedido se toma del evento
 * mas reciente. Leer el mismo log dos veces produce el mismo resultado.
 *
 * Los metodos son synchronized porque los 3 hilos consumidores (uno por
 * particion) actualizan el mismo estado.
 */
@Service
public class AnaliticaService {

    private static final Logger log = LoggerFactory.getLogger(AnaliticaService.class);
    private static final int TOP_PRODUCTOS = 5;

    private record EstadoActualPedido(EstadoPedido estado, int total, Instant fechaEvento) {
    }

    private static final class UnidadesProducto {
        private String nombre;
        private long unidades;
    }

    private final Set<UUID> eventosProcesados = new HashSet<>();
    private final Map<Long, EstadoActualPedido> pedidos = new HashMap<>();
    private final Map<Long, UnidadesProducto> unidadesPorProducto = new HashMap<>();
    private final Map<TipoMovimiento, Long> movimientosPorTipo = new EnumMap<>(TipoMovimiento.class);

    private long eventosPedido;
    private long eventosMovimiento;
    private long duplicadosIgnorados;
    private Instant ultimoEvento;

    public synchronized void registrarEventoPedido(PedidoEvento evento) {
        if (!eventosProcesados.add(evento.eventId())) {
            duplicadosIgnorados++;
            log.debug("Evento de pedido duplicado ignorado: {}", evento.eventId());
            return;
        }
        eventosPedido++;
        marcarUltimoEvento(evento.fechaEvento());

        EstadoActualPedido actual = pedidos.get(evento.pedidoId());
        if (actual != null && actual.fechaEvento().isAfter(evento.fechaEvento())) {
            // Llego un evento mas antiguo que el ya aplicado: no retrocede el estado.
            return;
        }
        pedidos.put(evento.pedidoId(),
                new EstadoActualPedido(evento.estado(), evento.total(), evento.fechaEvento()));
    }

    public synchronized void registrarMovimiento(MovimientoInventarioEvento movimiento) {
        if (!eventosProcesados.add(movimiento.eventId())) {
            duplicadosIgnorados++;
            log.debug("Movimiento duplicado ignorado: {}", movimiento.eventId());
            return;
        }
        eventosMovimiento++;
        marcarUltimoEvento(movimiento.fechaEvento());
        movimientosPorTipo.merge(movimiento.tipo(), 1L, Long::sum);

        UnidadesProducto producto = unidadesPorProducto.computeIfAbsent(
                movimiento.productoId(), id -> new UnidadesProducto());
        if (movimiento.nombreProducto() != null) {
            producto.nombre = movimiento.nombreProducto();
        }
        long signo = movimiento.tipo() == TipoMovimiento.SALIDA_VENTA ? 1 : -1;
        producto.unidades += signo * movimiento.cantidad();
    }

    public synchronized ResumenAnaliticaResponse resumen() {
        Map<EstadoPedido, Long> porEstado = new EnumMap<>(EstadoPedido.class);
        for (EstadoPedido estado : EstadoPedido.values()) {
            porEstado.put(estado, 0L);
        }
        long ventasTotales = 0;
        long pedidosActivos = 0;
        for (EstadoActualPedido pedido : pedidos.values()) {
            porEstado.merge(pedido.estado(), 1L, Long::sum);
            if (pedido.estado() != EstadoPedido.CANCELADO) {
                ventasTotales += pedido.total();
                pedidosActivos++;
            }
        }
        long ticketPromedio = pedidosActivos == 0 ? 0 : Math.round((double) ventasTotales / pedidosActivos);

        List<ProductoVendido> top = unidadesPorProducto.entrySet().stream()
                .filter(e -> e.getValue().unidades > 0)
                .sorted(Comparator.comparingLong(
                        (Map.Entry<Long, UnidadesProducto> e) -> e.getValue().unidades).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(TOP_PRODUCTOS)
                .map(e -> new ProductoVendido(e.getKey(), e.getValue().nombre, e.getValue().unidades))
                .toList();

        Map<TipoMovimiento, Long> movimientos = new EnumMap<>(TipoMovimiento.class);
        for (TipoMovimiento tipo : TipoMovimiento.values()) {
            movimientos.put(tipo, movimientosPorTipo.getOrDefault(tipo, 0L));
        }

        return new ResumenAnaliticaResponse(
                ventasTotales,
                pedidos.size(),
                pedidosActivos,
                ticketPromedio,
                porEstado,
                top,
                movimientos,
                eventosPedido,
                eventosMovimiento,
                duplicadosIgnorados,
                ultimoEvento,
                Instant.now()
        );
    }

    private void marcarUltimoEvento(Instant fecha) {
        if (fecha != null && (ultimoEvento == null || fecha.isAfter(ultimoEvento))) {
            ultimoEvento = fecha;
        }
    }
}

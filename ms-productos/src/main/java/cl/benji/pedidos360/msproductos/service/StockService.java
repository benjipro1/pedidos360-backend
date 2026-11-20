package cl.benji.pedidos360.msproductos.service;

import cl.benji.pedidos360.msproductos.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msproductos.dto.evento.TipoMovimiento;
import cl.benji.pedidos360.msproductos.entity.MovimientoProcesado;
import cl.benji.pedidos360.msproductos.entity.Producto;
import cl.benji.pedidos360.msproductos.exception.EventoInvalidoException;
import cl.benji.pedidos360.msproductos.repository.MovimientoProcesadoRepository;
import cl.benji.pedidos360.msproductos.repository.ProductoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica de dominio del stock. No sabe nada de Kafka: recibe un movimiento
 * ya validado y lo aplica. Quien lo llama (el listener) se encarga de la
 * mensajeria.
 *
 * Reglas:
 * - Idempotente: un eventId ya procesado se ignora.
 * - SALIDA_VENTA: descuenta la cantidad; si no alcanza, descuenta hasta 0 y
 *   registra una alerta (el pedido ya fue aceptado por ms-pedidos, asi que
 *   no se puede rechazar aqui; la alerta permite reponer o contactar al
 *   cliente).
 * - DEVOLUCION_CANCELACION: devuelve como maximo lo que de verdad se
 *   desconto para ese producto en ese pedido, para no inflar el stock.
 *
 * El registro del movimiento y el cambio de stock van en la misma
 * transaccion: o quedan ambos o ninguno.
 */
@Service
public class StockService {

    private static final Logger log = LoggerFactory.getLogger(StockService.class);

    private final ProductoRepository productoRepository;
    private final MovimientoProcesadoRepository movimientoRepository;

    public StockService(ProductoRepository productoRepository, MovimientoProcesadoRepository movimientoRepository) {
        this.productoRepository = productoRepository;
        this.movimientoRepository = movimientoRepository;
    }

    @Transactional
    public void aplicarMovimiento(MovimientoInventarioEvento movimiento) {
        if (movimientoRepository.existsById(movimiento.eventId())) {
            log.info("Movimiento duplicado ignorado (ya aplicado anteriormente)");
            return;
        }

        Producto producto = productoRepository.buscarParaActualizar(movimiento.productoId())
                .orElseThrow(() -> new EventoInvalidoException(
                        "El producto " + movimiento.productoId() + " no existe"));

        int stockAnterior = producto.getStock();
        int aplicada = switch (movimiento.tipo()) {
            case SALIDA_VENTA -> descontar(producto, movimiento);
            case DEVOLUCION_CANCELACION -> devolver(producto, movimiento);
        };

        productoRepository.save(producto);
        movimientoRepository.save(new MovimientoProcesado(
                movimiento.eventId(), movimiento.tipo(), movimiento.pedidoId(), movimiento.productoId(),
                movimiento.cantidad(), aplicada, producto.getStock()));

        log.info("Stock de producto {} ({}): {} -> {} por {} de {} unidades",
                producto.getId(), producto.getNombre(), stockAnterior, producto.getStock(),
                movimiento.tipo(), aplicada);
    }

    private int descontar(Producto producto, MovimientoInventarioEvento movimiento) {
        int disponible = producto.getStock();
        int aplicada = Math.min(movimiento.cantidad(), disponible);
        if (aplicada < movimiento.cantidad()) {
            log.warn("ALERTA stock insuficiente: producto {} ({}) tenia {} y el pedido {} pidio {}; "
                            + "se descuenta hasta 0 (faltan {} unidades)",
                    producto.getId(), producto.getNombre(), disponible, movimiento.pedidoId(),
                    movimiento.cantidad(), movimiento.cantidad() - aplicada);
        }
        producto.setStock(disponible - aplicada);
        return aplicada;
    }

    private int devolver(Producto producto, MovimientoInventarioEvento movimiento) {
        long descontado = movimientoRepository.sumarCantidadAplicada(
                movimiento.pedidoId(), movimiento.productoId(), TipoMovimiento.SALIDA_VENTA);
        long yaDevuelto = movimientoRepository.sumarCantidadAplicada(
                movimiento.pedidoId(), movimiento.productoId(), TipoMovimiento.DEVOLUCION_CANCELACION);
        int pendiente = (int) Math.max(0, descontado - yaDevuelto);

        int aplicada = Math.min(movimiento.cantidad(), pendiente);
        if (aplicada < movimiento.cantidad()) {
            log.warn("Devolucion parcial: el pedido {} pidio devolver {} unidades del producto {}, "
                            + "pero solo se habian descontado {}; se devuelven {}",
                    movimiento.pedidoId(), movimiento.cantidad(), producto.getId(), pendiente, aplicada);
        }
        producto.setStock(producto.getStock() + aplicada);
        return aplicada;
    }
}

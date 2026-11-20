package cl.benji.pedidos360.msproductos.entity;

import java.time.Instant;
import java.util.UUID;

import cl.benji.pedidos360.msproductos.dto.evento.TipoMovimiento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Registro de cada movimiento de inventario ya aplicado. Cumple dos
 * funciones:
 *
 * 1. Idempotencia: Kafka entrega "al menos una vez", asi que un mismo
 *    evento puede llegar dos veces (reintento, rebalanceo). La PK es el
 *    eventId: si ya existe, el movimiento se ignora y el stock no se
 *    descuenta dos veces.
 * 2. Trazabilidad: guarda cuanto se pidio y cuanto se aplico realmente
 *    (con stock insuficiente se descuenta hasta 0), para que una
 *    cancelacion devuelva solo lo que de verdad se desconto.
 */
@Entity
@Table(name = "movimientos_procesados",
        indexes = @Index(name = "idx_movimientos_pedido_producto", columnList = "pedidoId, productoId"))
public class MovimientoProcesado {

    @Id
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoMovimiento tipo;

    @Column(nullable = false)
    private Long pedidoId;

    @Column(nullable = false)
    private Long productoId;

    @Column(nullable = false)
    private Integer cantidadSolicitada;

    @Column(nullable = false)
    private Integer cantidadAplicada;

    @Column(nullable = false)
    private Integer stockResultante;

    @Column(nullable = false)
    private Instant fechaProcesado;

    protected MovimientoProcesado() {
        // Requerido por JPA
    }

    public MovimientoProcesado(UUID eventId, TipoMovimiento tipo, Long pedidoId, Long productoId,
                               Integer cantidadSolicitada, Integer cantidadAplicada, Integer stockResultante) {
        this.eventId = eventId;
        this.tipo = tipo;
        this.pedidoId = pedidoId;
        this.productoId = productoId;
        this.cantidadSolicitada = cantidadSolicitada;
        this.cantidadAplicada = cantidadAplicada;
        this.stockResultante = stockResultante;
        this.fechaProcesado = Instant.now();
    }

    public UUID getEventId() { return eventId; }
    public TipoMovimiento getTipo() { return tipo; }
    public Long getPedidoId() { return pedidoId; }
    public Long getProductoId() { return productoId; }
    public Integer getCantidadSolicitada() { return cantidadSolicitada; }
    public Integer getCantidadAplicada() { return cantidadAplicada; }
    public Integer getStockResultante() { return stockResultante; }
    public Instant getFechaProcesado() { return fechaProcesado; }
}

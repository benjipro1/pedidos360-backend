package cl.benji.pedidos360.msproductos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import cl.benji.pedidos360.msproductos.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msproductos.dto.evento.TipoMovimiento;
import cl.benji.pedidos360.msproductos.entity.MovimientoProcesado;
import cl.benji.pedidos360.msproductos.entity.Producto;
import cl.benji.pedidos360.msproductos.exception.EventoInvalidoException;
import cl.benji.pedidos360.msproductos.repository.MovimientoProcesadoRepository;
import cl.benji.pedidos360.msproductos.repository.ProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    private static final Long PRODUCTO_ID = 1L;
    private static final Long PEDIDO_ID = 50L;

    @Mock
    private ProductoRepository productoRepository;

    @Mock
    private MovimientoProcesadoRepository movimientoRepository;

    private StockService stockService;
    private Producto taladro;

    @BeforeEach
    void setUp() {
        stockService = new StockService(productoRepository, movimientoRepository);
        taladro = new Producto("Taladro", "Taladro percutor", 34990, 10, true);
    }

    @Test
    void salidaDescuentaStockYRegistraElMovimiento() {
        when(productoRepository.buscarParaActualizar(PRODUCTO_ID)).thenReturn(Optional.of(taladro));

        stockService.aplicarMovimiento(movimiento(TipoMovimiento.SALIDA_VENTA, 3));

        assertThat(taladro.getStock()).isEqualTo(7);
        MovimientoProcesado registrado = movimientoRegistrado();
        assertThat(registrado.getCantidadAplicada()).isEqualTo(3);
        assertThat(registrado.getStockResultante()).isEqualTo(7);
    }

    @Test
    void salidaConStockInsuficienteDescuentaHastaCero() {
        when(productoRepository.buscarParaActualizar(PRODUCTO_ID)).thenReturn(Optional.of(taladro));

        stockService.aplicarMovimiento(movimiento(TipoMovimiento.SALIDA_VENTA, 15));

        assertThat(taladro.getStock()).isZero();
        assertThat(movimientoRegistrado().getCantidadAplicada()).isEqualTo(10);
    }

    @Test
    void devolucionDevuelveSoloLoQueSeDesconto() {
        taladro.setStock(0);
        when(productoRepository.buscarParaActualizar(PRODUCTO_ID)).thenReturn(Optional.of(taladro));
        when(movimientoRepository.sumarCantidadAplicada(PEDIDO_ID, PRODUCTO_ID, TipoMovimiento.SALIDA_VENTA))
                .thenReturn(10L);
        when(movimientoRepository.sumarCantidadAplicada(PEDIDO_ID, PRODUCTO_ID, TipoMovimiento.DEVOLUCION_CANCELACION))
                .thenReturn(0L);

        // El pedido pidio 15, pero con stock insuficiente solo se descontaron 10.
        stockService.aplicarMovimiento(movimiento(TipoMovimiento.DEVOLUCION_CANCELACION, 15));

        assertThat(taladro.getStock()).isEqualTo(10);
        assertThat(movimientoRegistrado().getCantidadAplicada()).isEqualTo(10);
    }

    @Test
    void eventoYaProcesadoSeIgnora() {
        MovimientoInventarioEvento evento = movimiento(TipoMovimiento.SALIDA_VENTA, 3);
        when(movimientoRepository.existsById(evento.eventId())).thenReturn(true);

        stockService.aplicarMovimiento(evento);

        verify(productoRepository, never()).buscarParaActualizar(any());
        verify(movimientoRepository, never()).save(any());
    }

    @Test
    void productoInexistenteEsEventoInvalido() {
        when(productoRepository.buscarParaActualizar(PRODUCTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> stockService.aplicarMovimiento(movimiento(TipoMovimiento.SALIDA_VENTA, 1)))
                .isInstanceOf(EventoInvalidoException.class);
    }

    private MovimientoInventarioEvento movimiento(TipoMovimiento tipo, int cantidad) {
        return new MovimientoInventarioEvento(UUID.randomUUID(), tipo, 1, Instant.now(),
                PRODUCTO_ID, "Taladro", cantidad, PEDIDO_ID);
    }

    private MovimientoProcesado movimientoRegistrado() {
        ArgumentCaptor<MovimientoProcesado> captor = ArgumentCaptor.forClass(MovimientoProcesado.class);
        verify(movimientoRepository).save(captor.capture());
        return captor.getValue();
    }
}

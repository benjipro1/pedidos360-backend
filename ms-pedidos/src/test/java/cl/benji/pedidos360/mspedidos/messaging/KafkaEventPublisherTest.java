package cl.benji.pedidos360.mspedidos.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import cl.benji.pedidos360.mspedidos.config.KafkaTopicsProperties;
import cl.benji.pedidos360.mspedidos.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.mspedidos.dto.evento.PedidoEvento;
import cl.benji.pedidos360.mspedidos.dto.evento.TipoMovimiento;
import cl.benji.pedidos360.mspedidos.entity.DetallePedido;
import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;
import cl.benji.pedidos360.mspedidos.entity.Pedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KafkaEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private KafkaEventPublisher publisher;
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        KafkaTopicsProperties topicos = new KafkaTopicsProperties();
        topicos.setPedidos("pedidos.eventos");
        topicos.setMovimientos("inventario.movimientos");
        publisher = new KafkaEventPublisher(kafkaTemplate, topicos);

        pedido = new Pedido("cliente@duoc.cl");
        pedido.agregarDetalle(new DetallePedido(1L, "Taladro", 2, 34990));
        pedido.agregarDetalle(new DetallePedido(5L, "Martillo", 1, 8990));
        pedido.setTotal(78970);

        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(new CompletableFuture<SendResult<String, Object>>());
    }

    @Test
    void creacionPublicaUnEventoDePedidoYUnaSalidaPorLinea() {
        publisher.publicarCreacion(pedido);

        verify(kafkaTemplate).send(eq("pedidos.eventos"), anyString(), any(PedidoEvento.class));
        List<MovimientoInventarioEvento> movimientos = movimientosPublicados(2);
        assertThat(movimientos).extracting(MovimientoInventarioEvento::tipo)
                .containsOnly(TipoMovimiento.SALIDA_VENTA);
        assertThat(movimientos).extracting(MovimientoInventarioEvento::productoId).containsExactly(1L, 5L);
    }

    @Test
    void cancelarPublicaDevoluciones() {
        pedido.setEstado(EstadoPedido.CANCELADO);

        publisher.publicarCambioEstado(pedido, EstadoPedido.PAGADO);

        assertThat(movimientosPublicados(2)).extracting(MovimientoInventarioEvento::tipo)
                .containsOnly(TipoMovimiento.DEVOLUCION_CANCELACION);
    }

    @Test
    void cambioEntreEstadosActivosNoMueveStock() {
        pedido.setEstado(EstadoPedido.ENVIADO);

        publisher.publicarCambioEstado(pedido, EstadoPedido.PAGADO);

        verify(kafkaTemplate).send(eq("pedidos.eventos"), anyString(), any(PedidoEvento.class));
        verify(kafkaTemplate, never()).send(eq("inventario.movimientos"), anyString(), any());
    }

    @Test
    void unaFallaDeKafkaNoSePropaga() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new org.apache.kafka.common.errors.TimeoutException("sin brokers"));

        assertThatCode(() -> publisher.publicarCreacion(pedido)).doesNotThrowAnyException();
    }

    private List<MovimientoInventarioEvento> movimientosPublicados(int esperados) {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate, times(esperados)).send(eq("inventario.movimientos"), anyString(), captor.capture());
        return captor.getAllValues().stream().map(MovimientoInventarioEvento.class::cast).toList();
    }
}

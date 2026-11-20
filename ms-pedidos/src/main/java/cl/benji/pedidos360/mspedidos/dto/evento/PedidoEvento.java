package cl.benji.pedidos360.mspedidos.dto.evento;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;

/**
 * Evento del ciclo de vida de un pedido, publicado en pedidos.eventos con
 * key = pedidoId (todos los eventos de un pedido caen en la misma particion
 * y se consumen en orden).
 *
 * @param eventId        identificador unico: permite a los consumidores
 *                       descartar duplicados (entrega at-least-once)
 * @param tipo           creacion o cambio de estado
 * @param version        version del esquema del evento, para evolucionarlo
 *                       sin romper a consumidores antiguos
 * @param fechaEvento    instante en que ocurrio el hecho
 * @param estadoAnterior null en la creacion; en un cambio de estado permite
 *                       saber que contador de estados restar
 * @param items          lineas del pedido: la analitica calcula productos
 *                       mas vendidos sin consultar a ms-pedidos
 */
public record PedidoEvento(
        UUID eventId,
        TipoEventoPedido tipo,
        int version,
        Instant fechaEvento,
        Long pedidoId,
        String usuarioEmail,
        EstadoPedido estado,
        EstadoPedido estadoAnterior,
        Integer total,
        Instant fechaCreacion,
        List<ItemPedidoEvento> items
) {
    public static final int VERSION_ACTUAL = 1;
}

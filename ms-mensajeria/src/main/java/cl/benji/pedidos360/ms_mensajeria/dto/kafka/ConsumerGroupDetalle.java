package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

import java.util.List;

/**
 * Estado de un consumer group: miembros, particiones asignadas y lag.
 *
 * lag = offsetFin - offsetConsumidor: mensajes que el group aun no procesa.
 * Un lag que crece de forma sostenida indica consumidores caidos o lentos.
 */
public record ConsumerGroupDetalle(
        String groupId,
        String estado,
        String assignor,
        Integer coordinador,
        List<Miembro> miembros,
        List<OffsetParticion> offsets,
        long lagTotal
) {
    public record Miembro(String clientId, String consumerId, String host, List<String> particiones) {
    }

    /**
     * @param offsetConsumidor ultimo offset confirmado; null si el group aun
     *                         no confirma nada en esa particion (el lag se
     *                         cuenta entonces desde el primer offset retenido)
     * @param asignadaA        client.id del consumidor que la lee; null si el
     *                         group no tiene consumidores activos
     */
    public record OffsetParticion(String topico, int particion, Long offsetConsumidor, long offsetFin,
                                  long lag, String asignadaA) {
    }
}

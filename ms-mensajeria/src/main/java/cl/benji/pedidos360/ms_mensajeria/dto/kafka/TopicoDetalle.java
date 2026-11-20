package cl.benji.pedidos360.ms_mensajeria.dto.kafka;

import java.util.List;
import java.util.Map;

/**
 * @param configs configs explicitas del topico (las que no son default del
 *                broker), mas retention.ms, cleanup.policy y
 *                min.insync.replicas siempre
 */
public record TopicoDetalle(
        String nombre,
        boolean interno,
        List<Particion> particiones,
        Map<String, String> configs,
        long mensajesRetenidos
) {
    /**
     * @param isr         replicas en sincronia (in-sync replicas)
     * @param offsetInicio primer offset aun retenido
     * @param offsetFin    offset del proximo mensaje a escribir
     */
    public record Particion(int particion, Integer lider, List<Integer> replicas, List<Integer> isr,
                            long offsetInicio, long offsetFin) {
    }
}

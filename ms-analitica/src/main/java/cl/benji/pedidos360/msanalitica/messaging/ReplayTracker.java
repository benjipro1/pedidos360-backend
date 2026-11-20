package cl.benji.pedidos360.msanalitica.messaging;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

/**
 * Recuerda, por particion y consumer group, hasta que offset llega la
 * relectura (replay) que hace ms-analitica al arrancar.
 *
 * Los mensajes bajo ese limite ya fueron procesados en una ejecucion
 * anterior: si alguno es invalido, ya se envio al DLT entonces y no debe
 * volver a enviarse en cada reinicio.
 */
@Component
public class ReplayTracker {

    private final Map<String, Long> limites = new ConcurrentHashMap<>();

    void registrarLimite(String groupId, TopicPartition particion, long offsetConfirmado) {
        limites.put(clave(groupId, particion.topic(), particion.partition()), offsetConfirmado);
    }

    public boolean esRelectura(String groupId, ConsumerRecord<?, ?> registro) {
        Long limite = limites.get(clave(groupId, registro.topic(), registro.partition()));
        return limite != null && registro.offset() < limite;
    }

    private static String clave(String groupId, String topico, int particion) {
        return groupId + "|" + topico + "-" + particion;
    }
}

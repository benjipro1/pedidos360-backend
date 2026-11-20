package cl.benji.pedidos360.ms_mensajeria.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * Declaracion de los topicos de Pedidos360 como codigo (equivalente al
 * TopologyInitializer de RabbitMQ). Al arrancar, KafkaAdmin de Spring crea
 * los que faltan y, con spring.kafka.admin.modify-topic-configs=true,
 * alinea las configs de los que ya existen con lo declarado en
 * application.yml (kafka.topicos).
 *
 * Por que cada parametro:
 * - 3 particiones: hasta 3 consumidores en paralelo por group (un hilo por
 *   particion) y el orden se mantiene por key (pedidoId / productoId).
 * - 3 replicas + min.insync.replicas=2: con acks=all un mensaje confirmado
 *   esta en al menos 2 brokers; el cluster sigue aceptando escrituras si
 *   cae 1 broker y no pierde datos.
 * - cleanup.policy=delete + retention.ms: son logs de eventos (no
 *   snapshots), se conservan una ventana de tiempo para replay y auditoria:
 *   7 dias para pedidos (analitica reciente), 30 para inventario (cuadrar
 *   stock de un mes) y 30 para los DLT (tiempo de investigar errores).
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(KafkaTopicsConfig.TopicosProperties.class)
public class KafkaTopicsConfig {

    @ConfigurationProperties(prefix = "kafka")
    public static class TopicosProperties {

        private List<Topico> topicos = new ArrayList<>();

        public List<Topico> getTopicos() { return topicos; }
        public void setTopicos(List<Topico> topicos) { this.topicos = topicos; }

        public static class Topico {
            private String nombre;
            private int particiones = 3;
            private short replicas = 3;
            private Map<String, String> configs = new LinkedHashMap<>();

            public String getNombre() { return nombre; }
            public void setNombre(String nombre) { this.nombre = nombre; }
            public int getParticiones() { return particiones; }
            public void setParticiones(int particiones) { this.particiones = particiones; }
            public short getReplicas() { return replicas; }
            public void setReplicas(short replicas) { this.replicas = replicas; }
            public Map<String, String> getConfigs() { return configs; }
            public void setConfigs(Map<String, String> configs) { this.configs = configs; }
        }
    }

    @Bean
    public KafkaAdmin.NewTopics topicosPedidos360(TopicosProperties propiedades) {
        NewTopic[] topicos = propiedades.getTopicos().stream()
                .map(t -> {
                    log.info("Topico declarado: {} (particiones={}, replicas={}, configs={})",
                            t.getNombre(), t.getParticiones(), t.getReplicas(), t.getConfigs());
                    return TopicBuilder.name(t.getNombre())
                            .partitions(t.getParticiones())
                            .replicas(t.getReplicas())
                            .configs(t.getConfigs())
                            .build();
                })
                .toArray(NewTopic[]::new);
        return new KafkaAdmin.NewTopics(topicos);
    }

    /** Cliente de administracion que usa KafkaAdminService (misma conexion que KafkaAdmin). */
    @Bean(destroyMethod = "close")
    public AdminClient kafkaAdminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }
}

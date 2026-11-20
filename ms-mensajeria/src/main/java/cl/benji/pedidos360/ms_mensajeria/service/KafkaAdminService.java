package cl.benji.pedidos360.ms_mensajeria.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ClusterInfo;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ConfigsRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ConsumerGroupDetalle;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ConsumerGroupResumen;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ResetOffsetsRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.TopicoDetalle;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.TopicoRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.kafka.TopicoResumen;
import cl.benji.pedidos360.ms_mensajeria.exception.KafkaAdminException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.MemberDescription;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.stereotype.Service;

/**
 * Administracion de Kafka sobre el AdminClient oficial: topicos, sus
 * configuraciones y particiones, y consumer groups con sus offsets y lag.
 * Es el equivalente de {@link RabbitAdminService} para la plataforma de
 * streaming.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaAdminService {

    private static final long TIMEOUT_SEGUNDOS = 15;

    /** Configs que siempre se muestran, aunque tengan el valor por defecto. */
    private static final Set<String> CONFIGS_CLAVE = Set.of(
            "retention.ms", "cleanup.policy", "min.insync.replicas", "retention.bytes", "segment.ms");

    private static final Comparator<TopicPartition> ORDEN_PARTICIONES =
            Comparator.comparing(TopicPartition::topic).thenComparingInt(TopicPartition::partition);

    private final AdminClient adminClient;

    // ---------------------------------------------------------------- cluster

    public ClusterInfo describirCluster() {
        DescribeClusterResult resultado = adminClient.describeCluster();
        Node controller = esperar(resultado.controller());
        List<ClusterInfo.Broker> brokers = esperar(resultado.nodes()).stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(n -> new ClusterInfo.Broker(n.id(), n.host(), n.port(), n.rack()))
                .toList();
        return new ClusterInfo(esperar(resultado.clusterId()), controller == null ? null : controller.id(), brokers);
    }

    // ---------------------------------------------------------------- topicos

    public List<TopicoResumen> listarTopicos(boolean incluirInternos) {
        Set<String> nombres = esperar(adminClient.listTopics(new ListTopicsOptions().listInternal(incluirInternos)).names());
        return describir(nombres).values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(d -> new TopicoResumen(d.name(), d.partitions().size(),
                        d.partitions().isEmpty() ? 0 : d.partitions().getFirst().replicas().size(),
                        d.isInternal()))
                .toList();
    }

    public TopicoDetalle describirTopico(String nombre) {
        TopicDescription descripcion = describir(List.of(nombre)).get(nombre);

        List<TopicPartition> particiones = descripcion.partitions().stream()
                .map(p -> new TopicPartition(nombre, p.partition()))
                .toList();
        Map<TopicPartition, Long> inicio = offsets(particiones, OffsetSpec.earliest());
        Map<TopicPartition, Long> fin = offsets(particiones, OffsetSpec.latest());

        List<TopicoDetalle.Particion> detalle = descripcion.partitions().stream()
                .map(p -> {
                    TopicPartition tp = new TopicPartition(nombre, p.partition());
                    return new TopicoDetalle.Particion(
                            p.partition(),
                            p.leader() == null ? null : p.leader().id(),
                            ids(p.replicas()),
                            ids(p.isr()),
                            inicio.getOrDefault(tp, 0L),
                            fin.getOrDefault(tp, 0L));
                })
                .toList();
        long retenidos = detalle.stream().mapToLong(p -> p.offsetFin() - p.offsetInicio()).sum();

        return new TopicoDetalle(nombre, descripcion.isInternal(), detalle, configsDe(nombre), retenidos);
    }

    public void crearTopico(TopicoRequest request) {
        NewTopic topico = new NewTopic(request.nombre(), request.particiones(), request.replicas());
        if (request.configs() != null && !request.configs().isEmpty()) {
            topico.configs(request.configs());
        }
        esperar(adminClient.createTopics(List.of(topico)).all());
        log.info("Topico creado: {} (particiones={}, replicas={}, configs={})",
                request.nombre(), request.particiones(), request.replicas(), request.configs());
    }

    public void eliminarTopico(String nombre) {
        esperar(adminClient.deleteTopics(List.of(nombre)).all());
        log.info("Topico eliminado: {}", nombre);
    }

    public Map<String, String> modificarConfigs(String nombre, ConfigsRequest request) {
        List<AlterConfigOp> operaciones = new ArrayList<>();
        if (request.set() != null) {
            request.set().forEach((clave, valor) ->
                    operaciones.add(new AlterConfigOp(new ConfigEntry(clave, valor), AlterConfigOp.OpType.SET)));
        }
        if (request.eliminar() != null) {
            request.eliminar().forEach(clave ->
                    operaciones.add(new AlterConfigOp(new ConfigEntry(clave, ""), AlterConfigOp.OpType.DELETE)));
        }
        if (operaciones.isEmpty()) {
            throw new KafkaAdminException(KafkaAdminException.Tipo.SOLICITUD_INVALIDA,
                    "Indique al menos una config en 'set' o 'eliminar'");
        }
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        esperar(adminClient.incrementalAlterConfigs(Map.of(recurso, operaciones)).all());
        log.info("Configs del topico {} modificadas: set={} eliminar={}", nombre, request.set(), request.eliminar());
        return configsDe(nombre);
    }

    public void aumentarParticiones(String nombre, int total) {
        esperar(adminClient.createPartitions(Map.of(nombre, NewPartitions.increaseTo(total))).all());
        log.info("Topico {} ahora tiene {} particiones", nombre, total);
    }

    // -------------------------------------------------------- consumer groups

    public List<ConsumerGroupResumen> listarConsumerGroups() {
        return esperar(adminClient.listGroups().all()).stream()
                .filter(g -> "consumer".equals(g.protocol()) || g.protocol() == null || g.protocol().isEmpty())
                .sorted(Comparator.comparing(g -> g.groupId()))
                .map(g -> new ConsumerGroupResumen(
                        g.groupId(),
                        g.groupState().map(Enum::name).orElse("DESCONOCIDO"),
                        g.type().map(Enum::name).orElse("CLASSIC")))
                .toList();
    }

    public ConsumerGroupDetalle describirConsumerGroup(String groupId) {
        ConsumerGroupDescription descripcion = esperar(
                adminClient.describeConsumerGroups(List.of(groupId)).describedGroups().get(groupId));

        Map<TopicPartition, String> asignacion = new HashMap<>();
        List<ConsumerGroupDetalle.Miembro> miembros = new ArrayList<>();
        descripcion.members().stream()
                .sorted(Comparator.comparing(MemberDescription::clientId))
                .forEach(m -> {
                    List<TopicPartition> asignadas = m.assignment().topicPartitions().stream()
                            .sorted(ORDEN_PARTICIONES)
                            .toList();
                    asignadas.forEach(tp -> asignacion.put(tp, m.clientId()));
                    miembros.add(new ConsumerGroupDetalle.Miembro(m.clientId(), m.consumerId(), m.host(),
                            asignadas.stream().map(TopicPartition::toString).toList()));
                });

        Map<TopicPartition, OffsetAndMetadata> confirmados = esperar(
                adminClient.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata());

        Set<TopicPartition> todas = new HashSet<>(confirmados.keySet());
        todas.addAll(asignacion.keySet());
        Map<TopicPartition, Long> fin = offsets(todas, OffsetSpec.latest());
        // Particiones sin offset confirmado: el group (auto.offset.reset=
        // earliest) partiria del primer mensaje retenido, asi que ese es su lag.
        Map<TopicPartition, Long> inicio = offsets(
                todas.stream().filter(tp -> confirmados.get(tp) == null).toList(), OffsetSpec.earliest());

        List<ConsumerGroupDetalle.OffsetParticion> offsets = todas.stream()
                .sorted(ORDEN_PARTICIONES)
                .map(tp -> {
                    OffsetAndMetadata confirmado = confirmados.get(tp);
                    Long offsetConsumidor = confirmado == null ? null : confirmado.offset();
                    long offsetFin = fin.getOrDefault(tp, 0L);
                    long desde = offsetConsumidor != null ? offsetConsumidor : inicio.getOrDefault(tp, 0L);
                    long lag = Math.max(0, offsetFin - desde);
                    return new ConsumerGroupDetalle.OffsetParticion(tp.topic(), tp.partition(),
                            offsetConsumidor, offsetFin, lag, asignacion.get(tp));
                })
                .toList();
        long lagTotal = offsets.stream().mapToLong(ConsumerGroupDetalle.OffsetParticion::lag).sum();

        return new ConsumerGroupDetalle(
                groupId,
                descripcion.groupState() == null ? "DESCONOCIDO" : descripcion.groupState().name(),
                descripcion.partitionAssignor(),
                descripcion.coordinator() == null ? null : descripcion.coordinator().id(),
                miembros,
                offsets,
                lagTotal);
    }

    public ConsumerGroupDetalle resetearOffsets(String groupId, ResetOffsetsRequest request) {
        TopicDescription topico = describir(List.of(request.topico())).get(request.topico());
        List<TopicPartition> particiones = topico.partitions().stream()
                .map(p -> new TopicPartition(request.topico(), p.partition()))
                .toList();
        OffsetSpec spec = request.posicion() == ResetOffsetsRequest.Posicion.INICIO
                ? OffsetSpec.earliest() : OffsetSpec.latest();
        Map<TopicPartition, OffsetAndMetadata> nuevos = offsets(particiones, spec).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> new OffsetAndMetadata(e.getValue())));

        esperar(adminClient.alterConsumerGroupOffsets(groupId, nuevos).all());
        log.info("Offsets del group {} en {} movidos al {}", groupId, request.topico(), request.posicion());
        return describirConsumerGroup(groupId);
    }

    public void eliminarConsumerGroup(String groupId) {
        esperar(adminClient.deleteConsumerGroups(List.of(groupId)).all());
        log.info("Consumer group eliminado: {}", groupId);
    }

    // ---------------------------------------------------------------- apoyo

    private Map<String, TopicDescription> describir(Collection<String> nombres) {
        return esperar(adminClient.describeTopics(nombres).allTopicNames());
    }

    private Map<String, String> configsDe(String nombre) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Config config = esperar(adminClient.describeConfigs(List.of(recurso)).all()).get(recurso);
        return config.entries().stream()
                .filter(e -> e.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG || CONFIGS_CLAVE.contains(e.name()))
                .collect(Collectors.toMap(ConfigEntry::name, e -> String.valueOf(e.value()), (a, b) -> a, TreeMap::new));
    }

    private Map<TopicPartition, Long> offsets(Collection<TopicPartition> particiones, OffsetSpec spec) {
        if (particiones.isEmpty()) {
            return Map.of();
        }
        Map<TopicPartition, OffsetSpec> consulta = particiones.stream()
                .collect(Collectors.toMap(Function.identity(), tp -> spec));
        Map<TopicPartition, ListOffsetsResultInfo> resultado = esperar(adminClient.listOffsets(consulta).all());
        return resultado.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));
    }

    private static List<Integer> ids(List<Node> nodos) {
        return nodos.stream().map(Node::id).toList();
    }

    /**
     * Espera el resultado del AdminClient con un tope de tiempo y traduce la
     * causa real (la ExecutionException solo la envuelve) para que el
     * controller responda con el codigo HTTP que corresponde.
     */
    private static <T> T esperar(KafkaFuture<T> futuro) {
        try {
            return futuro.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw KafkaAdminException.desde(e.getCause());
        } catch (TimeoutException e) {
            throw new KafkaAdminException(KafkaAdminException.Tipo.NO_DISPONIBLE,
                    "Kafka no respondio en " + TIMEOUT_SEGUNDOS + " s");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaAdminException(KafkaAdminException.Tipo.NO_DISPONIBLE, "Operacion interrumpida");
        }
    }
}

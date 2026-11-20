package cl.benji.pedidos360.ms_mensajeria.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import cl.benji.pedidos360.ms_mensajeria.dto.kafka.ConsumerGroupDetalle;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.DescribeConsumerGroupsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.junit.jupiter.api.Test;

class KafkaAdminServiceTest {

    private static final String GRUPO = "analitica-ventas";
    private static final String TOPICO = "pedidos.eventos";
    private static final Node BROKER = new Node(1, "kafka-1", 9092);

    private final AdminClient adminClient = mock(AdminClient.class);
    private final KafkaAdminService servicio = new KafkaAdminService(adminClient);

    /**
     * Caso visto en la nube: el group esta detenido (sin miembros) y solo
     * confirmo offset en la particion 0. Los mensajes que llegaron a las
     * particiones 1 y 2 tambien son lag y deben aparecer en el detalle.
     */
    @Test
    void groupSinMiembrosCuentaLagDeParticionesSinOffsetConfirmado() {
        grupoVacio();
        confirmados(Map.of(new TopicPartition(TOPICO, 0), new OffsetAndMetadata(1)));
        topicoConParticiones(3);
        // p0: fin 2 (1 pendiente); p1: inicio 0, fin 1; p2: inicio 0, fin 0
        offsets(Map.of(0, 0L, 1, 0L, 2, 0L), Map.of(0, 2L, 1, 1L, 2, 0L));

        ConsumerGroupDetalle detalle = servicio.describirConsumerGroup(GRUPO);

        assertThat(detalle.offsets()).hasSize(3);
        assertThat(detalle.offsets()).extracting(ConsumerGroupDetalle.OffsetParticion::lag)
                .containsExactly(1L, 1L, 0L);
        assertThat(detalle.offsets().get(1).offsetConsumidor()).isNull();
        assertThat(detalle.lagTotal()).isEqualTo(2);
    }

    @Test
    void particionSinOffsetConfirmadoCuentaDesdeElPrimerMensajeRetenido() {
        grupoVacio();
        confirmados(Map.of(new TopicPartition(TOPICO, 0), new OffsetAndMetadata(5)));
        topicoConParticiones(2);
        // p1 ya borro por retencion sus primeros 3 mensajes
        offsets(Map.of(0, 0L, 1, 3L), Map.of(0, 5L, 1, 7L));

        ConsumerGroupDetalle detalle = servicio.describirConsumerGroup(GRUPO);

        assertThat(detalle.offsets()).extracting(ConsumerGroupDetalle.OffsetParticion::lag)
                .containsExactly(0L, 4L);
        assertThat(detalle.lagTotal()).isEqualTo(4);
    }

    // ----------------------------------------------------------------- apoyo

    private void grupoVacio() {
        ConsumerGroupDescription descripcion = mock(ConsumerGroupDescription.class);
        when(descripcion.members()).thenReturn(List.of());
        when(descripcion.groupState()).thenReturn(GroupState.EMPTY);
        when(descripcion.partitionAssignor()).thenReturn("");
        when(descripcion.coordinator()).thenReturn(BROKER);
        DescribeConsumerGroupsResult resultado = mock(DescribeConsumerGroupsResult.class);
        when(resultado.describedGroups()).thenReturn(Map.of(GRUPO, KafkaFuture.completedFuture(descripcion)));
        when(adminClient.describeConsumerGroups(anyCollection())).thenReturn(resultado);
    }

    private void confirmados(Map<TopicPartition, OffsetAndMetadata> offsets) {
        ListConsumerGroupOffsetsResult resultado = mock(ListConsumerGroupOffsetsResult.class);
        when(resultado.partitionsToOffsetAndMetadata()).thenReturn(KafkaFuture.completedFuture(offsets));
        when(adminClient.listConsumerGroupOffsets(anyString())).thenReturn(resultado);
    }

    private void topicoConParticiones(int total) {
        List<TopicPartitionInfo> particiones = IntStream.range(0, total)
                .mapToObj(p -> new TopicPartitionInfo(p, BROKER, List.of(BROKER), List.of(BROKER)))
                .toList();
        TopicDescription descripcion = new TopicDescription(TOPICO, false, particiones);
        DescribeTopicsResult resultado = mock(DescribeTopicsResult.class);
        when(resultado.topicNameValues()).thenReturn(Map.of(TOPICO, KafkaFuture.completedFuture(descripcion)));
        when(adminClient.describeTopics(anyCollection())).thenReturn(resultado);
    }

    /** Responde listOffsets segun el tipo de consulta: earliest o latest. */
    private void offsets(Map<Integer, Long> inicio, Map<Integer, Long> fin) {
        when(adminClient.listOffsets(anyMap())).thenAnswer(invocacion -> {
            Map<TopicPartition, OffsetSpec> consulta = invocacion.getArgument(0);
            Map<TopicPartition, ListOffsetsResultInfo> respuesta = consulta.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, e -> {
                        Map<Integer, Long> fuente = e.getValue() instanceof OffsetSpec.EarliestSpec ? inicio : fin;
                        return new ListOffsetsResultInfo(fuente.get(e.getKey().partition()), -1L, Optional.empty());
                    }));
            ListOffsetsResult resultado = mock(ListOffsetsResult.class);
            when(resultado.all()).thenReturn(KafkaFuture.completedFuture(respuesta));
            return resultado;
        });
    }
}

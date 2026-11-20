package cl.benji.pedidos360.msanalitica.messaging;

import cl.benji.pedidos360.msanalitica.dto.evento.PedidoEvento;
import cl.benji.pedidos360.msanalitica.service.AnaliticaService;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer group "analitica-ventas" sobre pedidos.eventos: alimenta los KPIs
 * de ventas (totales, pedidos por estado, ticket promedio).
 */
@Component
public class PedidoEventosListener extends ListenerConReplay {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventosListener.class);

    private final AnaliticaService analiticaService;

    public PedidoEventosListener(AnaliticaService analiticaService, Validator validator,
                                 ReplayTracker replayTracker,
                                 @Value("${kafka.grupos.ventas}") String groupId,
                                 @Value("${analitica.replay-al-iniciar:true}") boolean replayAlIniciar) {
        super(replayAlIniciar, validator, replayTracker, groupId);
        this.analiticaService = analiticaService;
    }

    @KafkaListener(
            id = "analitica-pedidos",
            topics = "${kafka.topicos.pedidos}",
            groupId = "${kafka.grupos.ventas}",
            concurrency = "${kafka.consumidores.concurrencia:3}",
            containerFactory = "pedidosListenerFactory")
    public void recibir(ConsumerRecord<String, PedidoEvento> registro) {
        PedidoEvento evento = registro.value();
        MDC.put("kafka", registro.topic() + "-" + registro.partition() + "@" + registro.offset());
        try {
            validar(evento);
            log.info("Evento de pedido {}-{}@{} (key={}): {} pedido {} estado {} total {} eventId={}",
                    registro.topic(), registro.partition(), registro.offset(), registro.key(),
                    evento.tipo(), evento.pedidoId(), evento.estado(), evento.total(), evento.eventId());
            analiticaService.registrarEventoPedido(evento);
        } finally {
            MDC.clear();
        }
    }
}

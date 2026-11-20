package cl.benji.pedidos360.msanalitica.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import cl.benji.pedidos360.msanalitica.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msanalitica.dto.evento.PedidoEvento;
import cl.benji.pedidos360.msanalitica.exception.EventoInvalidoException;
import cl.benji.pedidos360.msanalitica.messaging.ReplayTracker;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.kafka.autoconfigure.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerAwareRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente Kafka de ms-analitica. Hay un ConsumerFactory por topico porque
 * cada uno trae un DTO distinto (los mensajes no llevan cabeceras de tipo:
 * el consumidor decide a que clase deserializar).
 *
 * Mismo esquema de errores que ms-productos: ErrorHandlingDeserializer,
 * 3 reintentos con espera exponencial y DLT "<topico>.DLT"; los eventos
 * invalidos no se reintentan.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /** Mapper tolerante: ignora campos nuevos que agregue el productor. */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PedidoEvento> pedidosListenerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            KafkaProperties kafkaProperties,
            DefaultErrorHandler kafkaErrorHandler) {
        return crearFactory(configurer, consumerFactory(kafkaProperties, PedidoEvento.class), kafkaErrorHandler);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MovimientoInventarioEvento> movimientosListenerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            KafkaProperties kafkaProperties,
            DefaultErrorHandler kafkaErrorHandler) {
        return crearFactory(configurer, consumerFactory(kafkaProperties, MovimientoInventarioEvento.class),
                kafkaErrorHandler);
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaProperties kafkaProperties, ReplayTracker replayTracker) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
                plantillasDlt(kafkaProperties),
                (registro, ex) -> new TopicPartition(registro.topic() + ".DLT", registro.partition()));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        // Recoverer "consumer aware" para conocer el group del mensaje: si es
        // parte de la relectura del arranque, ya se envio al DLT en una
        // ejecucion anterior y no se duplica.
        ConsumerAwareRecordRecoverer recoverer = (registro, consumidor, ex) -> {
            String groupId = consumidor == null ? null : consumidor.groupMetadata().groupId();
            if (groupId != null && replayTracker.esRelectura(groupId, registro)) {
                log.info("Replay: {}-{}@{} sigue siendo invalido; ya se envio al DLT antes, se omite",
                        registro.topic(), registro.partition(), registro.offset());
                return;
            }
            log.error("Enviando a {}.DLT el mensaje {}-{}@{} (key={}): {}",
                    registro.topic(), registro.topic(), registro.partition(), registro.offset(),
                    registro.key(), causaRaiz(ex).getMessage());
            dlt.accept(registro, consumidor, ex);
        };

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        // Se llama en cada intento fallido. Si la excepcion no es
        // reintentable, al intento 1 le sigue directo el envio al DLT.
        handler.setRetryListeners((registro, ex, intento) ->
                log.warn("Intento {} fallido para {}-{}@{} (key={}): {}", intento,
                        registro.topic(), registro.partition(), registro.offset(),
                        registro.key(), causaRaiz(ex).getMessage()));
        return handler;
    }

    private static <T> ConsumerFactory<String, T> consumerFactory(KafkaProperties kafkaProperties, Class<T> tipo) {
        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(),
                StringDeserializer::new,
                () -> new ErrorHandlingDeserializer<>(new JacksonJsonDeserializer<>(tipo, MAPPER, false)));
    }

    /** Aplica spring.kafka.listener.* via el configurer de Boot (trabaja con tipos Object). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ConcurrentKafkaListenerContainerFactory<String, T> crearFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<String, T> consumerFactory,
            DefaultErrorHandler errorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory = new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure((ConcurrentKafkaListenerContainerFactory) factory, (ConsumerFactory) consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /** DTO -> JSON; bytes originales (fallo de deserializacion) -> tal cual. */
    private static Map<Class<?>, KafkaOperations<?, ?>> plantillasDlt(KafkaProperties kafkaProperties) {
        Map<String, Object> props = kafkaProperties.buildProducerProperties();
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        Map<String, Object> bytesProps = new HashMap<>(props);
        bytesProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        JacksonJsonSerializer<Object> json = new JacksonJsonSerializer<>();
        json.setAddTypeInfo(false);

        Map<Class<?>, KafkaOperations<?, ?>> plantillas = new LinkedHashMap<>();
        plantillas.put(byte[].class, new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(bytesProps)));
        plantillas.put(Object.class, new KafkaTemplate<>(new DefaultKafkaProducerFactory<String, Object>(
                props, StringSerializer::new, () -> json)));
        return plantillas;
    }

    private static Throwable causaRaiz(Throwable ex) {
        Throwable actual = ex;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }
        return actual;
    }
}

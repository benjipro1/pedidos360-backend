package cl.benji.pedidos360.msproductos.config;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import cl.benji.pedidos360.msproductos.dto.evento.MovimientoInventarioEvento;
import cl.benji.pedidos360.msproductos.exception.EventoInvalidoException;
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
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente Kafka de ms-productos (consumer group "productos-stock").
 *
 * Las propiedades comunes (bootstrap servers, client-id, auto-offset-reset,
 * assignor, ack-mode, concurrencia) vienen de application.properties via
 * KafkaProperties. Aqui se arma lo que no se puede expresar como propiedad:
 *
 * 1. Deserializacion tolerante a fallos: ErrorHandlingDeserializer envuelve
 *    al JSON. Un mensaje que no es JSON valido no bota al consumidor en un
 *    bucle infinito ("poison pill"): se entrega al error handler.
 * 2. Error handler: 3 reintentos con espera exponencial (1 s, 2 s, 4 s) para
 *    fallas transitorias (por ejemplo, la base de datos no responde). Los
 *    eventos invalidos y los errores de deserializacion NO se reintentan.
 *    Si se agotan los reintentos, el mensaje va a "<topico>.DLT" en la misma
 *    particion, con cabeceras que explican la causa.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /** Mapper tolerante: ignora campos nuevos que agregue el productor (evolucion del esquema). */
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Bean
    public ConsumerFactory<String, MovimientoInventarioEvento> movimientosConsumerFactory(KafkaProperties kafkaProperties) {
        return new DefaultKafkaConsumerFactory<>(
                kafkaProperties.buildConsumerProperties(),
                StringDeserializer::new,
                () -> new ErrorHandlingDeserializer<>(
                        new JacksonJsonDeserializer<>(MovimientoInventarioEvento.class, MAPPER, false)));
    }

    /**
     * El configurer de Boot aplica spring.kafka.listener.* (ack-mode, etc.)
     * y la concurrencia. Trabaja con tipos Object, de ahi los cast.
     */
    @Bean
    @SuppressWarnings({"unchecked", "rawtypes"})
    public ConcurrentKafkaListenerContainerFactory<String, MovimientoInventarioEvento> movimientosListenerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<String, MovimientoInventarioEvento> movimientosConsumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, MovimientoInventarioEvento> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure((ConcurrentKafkaListenerContainerFactory) factory, (ConsumerFactory) movimientosConsumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaProperties kafkaProperties) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
                plantillasDlt(kafkaProperties),
                (registro, ex) -> new TopicPartition(registro.topic() + ".DLT", registro.partition()));

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler((registro, ex) -> {
            log.error("Enviando a {}.DLT el mensaje {}-{}@{} (key={}): {}",
                    registro.topic(), registro.topic(), registro.partition(), registro.offset(),
                    registro.key(), causaRaiz(ex).getMessage());
            dlt.accept(registro, ex);
        }, backOff);
        handler.addNotRetryableExceptions(EventoInvalidoException.class);
        // Se llama en cada intento fallido. Si la excepcion no es
        // reintentable, al intento 1 le sigue directo el envio al DLT.
        handler.setRetryListeners((registro, ex, intento) ->
                log.warn("Intento {} fallido para {}-{}@{} (key={}): {}", intento,
                        registro.topic(), registro.partition(), registro.offset(),
                        registro.key(), causaRaiz(ex).getMessage()));
        return handler;
    }

    /**
     * El DLT recibe dos clases de valor: el DTO (fallo de negocio, se
     * re-serializa como JSON) o los bytes originales (fallo de
     * deserializacion, se reenvian tal cual llegaron).
     */
    private Map<Class<?>, KafkaOperations<?, ?>> plantillasDlt(KafkaProperties kafkaProperties) {
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

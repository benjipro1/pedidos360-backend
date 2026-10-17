package cl.benji.pedidos360.ms_mensajeria.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Fuerza la conexión a RabbitMQ y la declaración de toda la topología
 * (exchanges, colas, bindings) al arrancar la aplicación, y deja constancia
 * explícita en el log del resultado.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TopologyInitializer implements CommandLineRunner {

    private final AmqpAdmin amqpAdmin;

    @Override
    public void run(String... args) {
        try {
            amqpAdmin.initialize();
            log.info("Topología de RabbitMQ declarada correctamente: exchanges, colas y bindings listos");
        } catch (Exception e) {
            log.error("No se pudo declarar la topología en RabbitMQ: {}", e.getMessage(), e);
        }
    }
}
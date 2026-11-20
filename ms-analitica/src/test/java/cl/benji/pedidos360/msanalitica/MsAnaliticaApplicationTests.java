package cl.benji.pedidos360.msanalitica;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Verifica que el contexto arranca. No necesita un cluster Kafka: los
 * listeners se crean sin arrancar (autoStartup=false) para que la prueba no
 * quede esperando brokers.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
class MsAnaliticaApplicationTests {

	@Test
	void contextLoads() {
	}

}

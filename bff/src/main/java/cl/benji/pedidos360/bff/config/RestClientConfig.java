package cl.benji.pedidos360.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Un RestClient por microservicio, cada uno con su propia base URL
 * (PRODUCTOS_URL / PEDIDOS_URL). CLAUDE.md prohibe explicitamente Spring
 * Cloud Gateway, Eureka y Feign para las llamadas internas: en este
 * proyecto no hay descubrimiento de servicios ni balanceo de carga, cada
 * microservicio vive en una URL fija conocida, asi que RestClient (parte
 * de Spring Web, sin dependencias nuevas) alcanza y sobra.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient productosRestClient(@Value("${app.productos-url}") String productosUrl) {
        return RestClient.builder().baseUrl(productosUrl).build();
    }

    @Bean
    public RestClient pedidosRestClient(@Value("${app.pedidos-url}") String pedidosUrl) {
        return RestClient.builder().baseUrl(pedidosUrl).build();
    }
}

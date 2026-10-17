package cl.benji.pedidos360.ms_mensajeria.config;

import cl.benji.pedidos360.ms_mensajeria.dto.PedidoEventoDTO;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * El emisor (ms-pedidos) agrega un header __TypeId__ con el nombre completo
 * de SU clase Java (cl.benji.pedidos360.mspedidos.dto.PedidoEventoDTO), que
 * no existe en este microservicio. Mapeamos ese nombre explicitamente a
 * nuestra propia copia del DTO para que la deserializacion funcione sin
 * acoplar los dos microservicios por classpath.
 */
@Configuration
public class MessageConverterConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        typeMapper.setIdClassMapping(
                Map.of("cl.benji.pedidos360.mspedidos.dto.PedidoEventoDTO", PedidoEventoDTO.class)
        );

        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
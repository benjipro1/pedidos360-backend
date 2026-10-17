package cl.benji.pedidos360.ms_mensajeria.config;

import cl.benji.pedidos360.ms_mensajeria.dto.PedidoEventoDTO;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class MessageConverterConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        DefaultClassMapper classMapper = new DefaultClassMapper();
        classMapper.setTrustedPackages("cl.benji.pedidos360");
        classMapper.setIdClassMapping(
                Map.of("cl.benji.pedidos360.mspedidos.dto.PedidoEventoDTO", PedidoEventoDTO.class)
        );
        // Si el mensaje no trae __TypeId__ (ej. publicado manualmente desde
        // la Management UI para pruebas), se asume este tipo por defecto.
        classMapper.setDefaultType(PedidoEventoDTO.class);

        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }
}
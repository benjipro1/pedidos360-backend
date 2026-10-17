package cl.benji.pedidos360.ms_mensajeria.service;

import cl.benji.pedidos360.ms_mensajeria.dto.admin.BindingRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.admin.ExchangeRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.admin.QueueRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class RabbitAdminService {

    private final AmqpAdmin amqpAdmin;

    public void crearExchange(ExchangeRequest request) {
        Exchange exchange = switch (request.tipo()) {
            case "direct" -> new DirectExchange(request.nombre(), request.durable(), request.autoDelete());
            case "topic" -> new TopicExchange(request.nombre(), request.durable(), request.autoDelete());
            case "fanout" -> new FanoutExchange(request.nombre(), request.durable(), request.autoDelete());
            default -> new HeadersExchange(request.nombre(), request.durable(), request.autoDelete());
        };
        amqpAdmin.declareExchange(exchange);
        log.info("Exchange creado: {} (tipo={}, durable={})", request.nombre(), request.tipo(), request.durable());
    }

    public void eliminarExchange(String nombre) {
        amqpAdmin.deleteExchange(nombre);
        log.info("Exchange eliminado: {}", nombre);
    }

    public void crearCola(QueueRequest request) {
        Queue queue = new Queue(request.nombre(), request.durable(), request.exclusive(), request.autoDelete());
        amqpAdmin.declareQueue(queue);
        log.info("Cola creada: {} (durable={}, exclusive={}, autoDelete={})",
                request.nombre(), request.durable(), request.exclusive(), request.autoDelete());
    }

    public void eliminarCola(String nombre) {
        amqpAdmin.deleteQueue(nombre);
        log.info("Cola eliminada: {}", nombre);
    }

    public void crearBinding(BindingRequest request) {
        Binding binding = new Binding(
                request.cola(), Binding.DestinationType.QUEUE,
                request.exchange(), request.routingKey(), null
        );
        amqpAdmin.declareBinding(binding);
        log.info("Binding creado: cola={} <- exchange={} (routingKey={})",
                request.cola(), request.exchange(), request.routingKey());
    }

    public void eliminarBinding(BindingRequest request) {
        Binding binding = new Binding(
                request.cola(), Binding.DestinationType.QUEUE,
                request.exchange(), request.routingKey(), null
        );
        amqpAdmin.removeBinding(binding);
        log.info("Binding eliminado: cola={} <- exchange={} (routingKey={})",
                request.cola(), request.exchange(), request.routingKey());
    }
}
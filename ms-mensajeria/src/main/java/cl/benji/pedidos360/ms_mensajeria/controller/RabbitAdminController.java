package cl.benji.pedidos360.ms_mensajeria.controller;

import cl.benji.pedidos360.ms_mensajeria.dto.admin.BindingRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.admin.ExchangeRequest;
import cl.benji.pedidos360.ms_mensajeria.dto.admin.QueueRequest;
import cl.benji.pedidos360.ms_mensajeria.service.RabbitAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/rabbitmq")
@RequiredArgsConstructor
public class RabbitAdminController {

    private final RabbitAdminService rabbitAdminService;

    @PostMapping("/exchanges")
    public ResponseEntity<Void> crearExchange(@Valid @RequestBody ExchangeRequest request) {
        rabbitAdminService.crearExchange(request);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/exchanges/{nombre}")
    public ResponseEntity<Void> eliminarExchange(@PathVariable String nombre) {
        rabbitAdminService.eliminarExchange(nombre);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/queues")
    public ResponseEntity<Void> crearCola(@Valid @RequestBody QueueRequest request) {
        rabbitAdminService.crearCola(request);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/queues/{nombre}")
    public ResponseEntity<Void> eliminarCola(@PathVariable String nombre) {
        rabbitAdminService.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bindings")
    public ResponseEntity<Void> crearBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdminService.crearBinding(request);
        return ResponseEntity.status(201).build();
    }

    @DeleteMapping("/bindings")
    public ResponseEntity<Void> eliminarBinding(@Valid @RequestBody BindingRequest request) {
        rabbitAdminService.eliminarBinding(request);
        return ResponseEntity.noContent().build();
    }
}
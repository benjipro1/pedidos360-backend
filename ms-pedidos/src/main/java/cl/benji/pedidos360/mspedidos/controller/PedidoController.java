package cl.benji.pedidos360.mspedidos.controller;

import java.util.List;

import cl.benji.pedidos360.mspedidos.dto.EstadoRequest;
import cl.benji.pedidos360.mspedidos.dto.PedidoRequest;
import cl.benji.pedidos360.mspedidos.dto.PedidoResponse;
import cl.benji.pedidos360.mspedidos.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * ms-pedidos no valida tokens JWT: confia en que solo el BFF puede
 * alcanzarlo (ver README, seccion "Seguridad: por que ms-pedidos no valida
 * tokens"). El usuario autenticado llega en la cabecera X-User-Email, ya
 * resuelta y verificada por el BFF a partir del claim preferred_username.
 */
@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private static final String HEADER_USUARIO = "X-User-Email";

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public List<PedidoResponse> listar(
            @RequestHeader(value = HEADER_USUARIO, required = false) String usuarioEmail) {
        return pedidoService.listar(usuarioEmail);
    }

    @GetMapping("/{id}")
    public PedidoResponse buscarPorId(@PathVariable Long id) {
        return pedidoService.buscarPorId(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PedidoResponse crear(
            @RequestHeader(value = HEADER_USUARIO, required = false) String usuarioEmail,
            @Valid @RequestBody PedidoRequest request) {
        return pedidoService.crear(usuarioEmail, request);
    }

    @PutMapping("/{id}/estado")
    public PedidoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoRequest request) {
        return pedidoService.cambiarEstado(id, request.estado());
    }
}

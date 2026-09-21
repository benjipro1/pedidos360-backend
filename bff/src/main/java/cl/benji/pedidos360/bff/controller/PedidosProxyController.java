package cl.benji.pedidos360.bff.controller;

import static cl.benji.pedidos360.bff.controller.ProxyResponses.reenviar;
import static cl.benji.pedidos360.bff.controller.ProxyResponses.usuarioDe;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Proxy puro hacia ms-pedidos. A diferencia de productos, aca NO hay
 * @PreAuthorize adicional: CLAUDE.md solo exige ROLE_Admin para
 * escribir/borrar PRODUCTOS, no pedidos (cualquier Cliente con
 * SCOPE_pedidos.write puede crear y gestionar sus propios pedidos).
 *
 * El header X-User-Email es la pieza clave de este controlador: es lo que
 * permite que ms-pedidos asocie cada pedido a su dueño sin tener que
 * validar tokens el mismo (ver README, seccion "por que ms-pedidos no
 * valida tokens").
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidosProxyController {

    private static final String HEADER_USUARIO = "X-User-Email";

    private final RestClient pedidosClient;

    public PedidosProxyController(@Qualifier("pedidosRestClient") RestClient pedidosClient) {
        this.pedidosClient = pedidosClient;
    }

    @GetMapping
    public ResponseEntity<byte[]> listar(@AuthenticationPrincipal Jwt jwt) {
        return reenviar(pedidosClient.get()
                .uri("/pedidos")
                .header(HEADER_USUARIO, usuarioDe(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> buscarPorId(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return reenviar(pedidosClient.get()
                .uri("/pedidos/{id}", id)
                .header(HEADER_USUARIO, usuarioDe(jwt)));
    }

    @PostMapping
    public ResponseEntity<byte[]> crear(@RequestBody byte[] cuerpo, @AuthenticationPrincipal Jwt jwt) {
        return reenviar(pedidosClient.post()
                .uri("/pedidos")
                .header(HEADER_USUARIO, usuarioDe(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo));
    }

    @PutMapping("/{id}/estado")
    public ResponseEntity<byte[]> cambiarEstado(@PathVariable Long id, @RequestBody byte[] cuerpo,
            @AuthenticationPrincipal Jwt jwt) {
        return reenviar(pedidosClient.put()
                .uri("/pedidos/{id}/estado", id)
                .header(HEADER_USUARIO, usuarioDe(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo));
    }
}

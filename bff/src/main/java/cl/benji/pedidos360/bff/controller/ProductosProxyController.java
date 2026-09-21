package cl.benji.pedidos360.bff.controller;

import static cl.benji.pedidos360.bff.controller.ProxyResponses.reenviar;
import static cl.benji.pedidos360.bff.controller.ProxyResponses.usuarioDe;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * Proxy puro hacia ms-productos: el BFF no valida ni interpreta el cuerpo
 * de estas peticiones (eso ya lo hace ms-productos con sus propios DTOs y
 * Bean Validation), solo agrega la identidad del usuario y reenvia bytes
 * tal cual. authorizeHttpRequests (SecurityConfig) ya exige
 * SCOPE_pedidos.read para los GET y SCOPE_pedidos.write para el resto;
 * @PreAuthorize agrega aca la restriccion EXTRA de CLAUDE.md: "escribir o
 * borrar productos requiere ademas ROLE_Admin" (un Cliente puede tener
 * pedidos.write para crear SUS pedidos, pero eso no lo habilita a tocar el
 * catalogo de productos).
 */
@RestController
@RequestMapping("/api/productos")
public class ProductosProxyController {

    private static final String HEADER_USUARIO = "X-User-Email";

    private final RestClient productosClient;

    public ProductosProxyController(@Qualifier("productosRestClient") RestClient productosClient) {
        this.productosClient = productosClient;
    }

    @GetMapping
    public ResponseEntity<byte[]> listar(@AuthenticationPrincipal Jwt jwt) {
        return reenviar(productosClient.get()
                .uri("/productos")
                .header(HEADER_USUARIO, usuarioDe(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> buscarPorId(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return reenviar(productosClient.get()
                .uri("/productos/{id}", id)
                .header(HEADER_USUARIO, usuarioDe(jwt)));
    }

    @PostMapping
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<byte[]> crear(@RequestBody byte[] cuerpo, @AuthenticationPrincipal Jwt jwt) {
        return reenviar(productosClient.post()
                .uri("/productos")
                .header(HEADER_USUARIO, usuarioDe(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<byte[]> actualizar(@PathVariable Long id, @RequestBody byte[] cuerpo,
            @AuthenticationPrincipal Jwt jwt) {
        return reenviar(productosClient.put()
                .uri("/productos/{id}", id)
                .header(HEADER_USUARIO, usuarioDe(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public ResponseEntity<byte[]> eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return reenviar(productosClient.delete()
                .uri("/productos/{id}", id)
                .header(HEADER_USUARIO, usuarioDe(jwt)));
    }
}

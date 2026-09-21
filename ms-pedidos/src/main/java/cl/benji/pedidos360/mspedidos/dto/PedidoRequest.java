package cl.benji.pedidos360.mspedidos.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * DTO de entrada para crear un pedido. usuarioEmail no viene en el cuerpo:
 * lo toma el controlador de la cabecera X-User-Email, que reenvia el BFF.
 */
public record PedidoRequest(

        @NotEmpty(message = "el pedido debe tener al menos un detalle")
        @Valid
        List<DetalleItemRequest> detalles
) {
}

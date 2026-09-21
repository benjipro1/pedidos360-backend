package cl.benji.pedidos360.mspedidos.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import cl.benji.pedidos360.mspedidos.dto.PedidoResponse;
import cl.benji.pedidos360.mspedidos.entity.EstadoPedido;
import cl.benji.pedidos360.mspedidos.service.PedidoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test de la capa web con MockMvc. La capa de servicio se mockea (Boot 4.x:
 * @MockitoBean reemplaza a @MockBean, eliminado en Spring Framework 7).
 */
@WebMvcTest(PedidoController.class)
class PedidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PedidoService pedidoService;

    @Test
    void listarDevuelveLosPedidosDelServicio() throws Exception {
        PedidoResponse pedido = new PedidoResponse(
                1L, "cliente@duoc.cl", Instant.parse("2026-01-01T00:00:00Z"),
                EstadoPedido.PENDIENTE, 15000, List.of());
        when(pedidoService.listar(null)).thenReturn(List.of(pedido));

        mockMvc.perform(get("/pedidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].usuarioEmail").value("cliente@duoc.cl"));
    }

    @Test
    void crearConDatosInvalidosDevuelve400() throws Exception {
        String cuerpoInvalido = """
                {
                  "detalles": []
                }
                """;

        mockMvc.perform(post("/pedidos")
                        .header("X-User-Email", "cliente@duoc.cl")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("validacion"));
    }
}

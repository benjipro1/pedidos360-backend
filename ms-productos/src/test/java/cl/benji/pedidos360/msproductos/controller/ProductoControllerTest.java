package cl.benji.pedidos360.msproductos.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import cl.benji.pedidos360.msproductos.dto.ProductoResponse;
import cl.benji.pedidos360.msproductos.service.ProductoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Test de la capa web con MockMvc. La capa de servicio se mockea para no
 * requerir una base de datos real (Boot 4.x: @MockitoBean reemplaza a
 * @MockBean, eliminado en Spring Framework 7).
 */
@WebMvcTest(ProductoController.class)
class ProductoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductoService productoService;

    @Test
    void listarDevuelveLosProductosDelServicio() throws Exception {
        ProductoResponse producto = new ProductoResponse(1L, "Taladro", "Taladro electrico", 34990, 15, true);
        when(productoService.listar()).thenReturn(List.of(producto));

        mockMvc.perform(get("/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Taladro"));
    }

    @Test
    void crearConDatosInvalidosDevuelve400() throws Exception {
        String cuerpoInvalido = """
                {
                  "nombre": "",
                  "precio": -100,
                  "stock": -5
                }
                """;

        mockMvc.perform(post("/productos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoInvalido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("validacion"));
    }
}

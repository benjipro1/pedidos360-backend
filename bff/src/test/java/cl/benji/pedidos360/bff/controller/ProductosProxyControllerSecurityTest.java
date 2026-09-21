package cl.benji.pedidos360.bff.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.benji.pedidos360.bff.config.RestClientConfig;
import cl.benji.pedidos360.bff.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Estos tests NO usan un JWT real ni pasan por el JwtDecoder de verdad:
 * jwt() (de spring-security-test) inyecta directamente una Authentication
 * ya autenticada con las autoridades que le pidamos, sin verificar firma
 * ni contactar el JWKS. Lo que se prueba aca es exclusivamente la capa de
 * AUTORIZACION (authorizeHttpRequests + @PreAuthorize), que es la parte de
 * SecurityConfig que se puede romper por un typo sin que ningun test de
 * "el token es valido" lo detecte.
 *
 * @Import trae SecurityConfig (que @WebMvcTest no escanea por si solo,
 * porque no es un @Controller) y RestClientConfig (necesario para que
 * ProductosProxyController tenga su RestClient real inyectado; no se
 * mockea a proposito, ver el segundo test).
 */
@WebMvcTest(ProductosProxyController.class)
@Import({SecurityConfig.class, RestClientConfig.class})
class ProductosProxyControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/productos"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Regresion: un Authorization que no parsea como JWT falla DENTRO del
     * filtro de bearer token, que tiene su propio AuthenticationEntryPoint.
     * Si no se declara el nuestro tambien en el DSL de oauth2ResourceServer,
     * este 401 sale con cuerpo vacio en vez del JSON del proyecto.
     */
    @Test
    void tokenMalFormadoDevuelve401ConCuerpoJson() throws Exception {
        mockMvc.perform(get("/api/productos").header("Authorization", "Bearer token.falso.aqui"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("no_autenticado"));
    }

    @Test
    void conScopeReadPasaLaCapaDeSeguridad() throws Exception {
        // No mockeamos el RestClient: dejamos que intente conectarse de
        // verdad a PRODUCTOS_URL (localhost:8081), que no esta levantado en
        // este test. Eso SI puede fallar (y de hecho falla, con 503 gracias
        // a UpstreamExceptionHandler) pero lo que nos interesa verificar
        // aca es otra cosa: que la request llegue hasta el controlador. Si
        // authorizeHttpRequests estuviera mal (por ejemplo, exigiendo
        // SCOPE_pedidos.write en vez de .read para GET), esto habria
        // fallado con 401/403 ANTES de siquiera intentar la conexion.
        MvcResult resultado = mockMvc.perform(get("/api/productos")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_pedidos.read"))))
                .andReturn();

        int status = resultado.getResponse().getStatus();
        assertThat(status).isNotEqualTo(401);
        assertThat(status).isNotEqualTo(403);
    }

    @Test
    void conScopeWriteSinRolAdminDevuelve403() throws Exception {
        // SCOPE_pedidos.write alcanza para la regla de URL de
        // authorizeHttpRequests (todo /api/** que no sea GET), pero
        // DELETE /api/productos/{id} ademas exige
        // @PreAuthorize("hasRole('Admin')") en el controlador. Sin
        // ROLE_Admin, method security debe rechazarla con 403 antes de que
        // el metodo del controlador llegue a ejecutarse (por eso ni
        // siquiera hace falta un RestClient real: nunca se invoca).
        mockMvc.perform(delete("/api/productos/1")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_pedidos.write"))))
                .andExpect(status().isForbidden());
    }
}

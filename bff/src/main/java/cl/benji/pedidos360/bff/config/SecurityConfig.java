package cl.benji.pedidos360.bff.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import cl.benji.pedidos360.bff.security.JsonAccessDeniedHandler;
import cl.benji.pedidos360.bff.security.JsonAuthEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracion de seguridad del BFF. Implementa, en orden, las 8 reglas de
 * la seccion "Reglas de seguridad del BFF" de CLAUDE.md. Cada bloque de
 * este archivo esta comentado indicando a cual regla corresponde.
 *
 * Idea general del BFF en la arquitectura (ver diagrama en el README): el
 * puerto del BFF (8080) es alcanzable directamente desde internet en la
 * EC2, sin pasar por el API Gateway. El Gateway ya valido el token una vez,
 * pero eso NO protege una llamada que se salte el Gateway y le pegue
 * directo a la IP de la instancia. Por eso el BFF vuelve a validar todo
 * desde cero: no confia en que "si llego hasta aca, el Gateway ya lo
 * revisó". Esto es "defensa en profundidad": cada capa asume que la
 * anterior pudo haber sido evitada.
 */
@Configuration
@EnableWebSecurity
// Regla: habilita @PreAuthorize en los controladores (usado para exigir
// ROLE_Admin al escribir/borrar productos, ademas del scope de la ruta).
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.jwks-uri}")
    private String jwksUri;

    @Value("${jwt.audience}")
    private String audience;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Regla 8 (mitad 1): CSRF protege formularios de navegador que
                // envian cookies de sesion automaticamente en cada request. Esta
                // API no usa cookies: cada cliente manda su propio JWT en el
                // header Authorization, que un sitio malicioso no puede leer ni
                // reenviar por si solo. Sin cookies de sesion, no hay CSRF que
                // mitigar; dejarlo activado solo agregaria friccion sin
                // proteger nada real.
                .csrf(csrf -> csrf.disable())
                // Regla 8 (mitad 2): sin sesiones. El servidor no recuerda
                // login alguno entre requests; CADA request se autentica desde
                // cero verificando su propio JWT. Coherente con "el token es
                // la unica prueba de identidad", no una cookie de sesion.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Regla 6: autorizacion por ruta. Spring evalua estos
                // matchers EN ORDEN y usa el primero que calce, asi que las
                // reglas mas especificas van primero.
                .authorizeHttpRequests(auth -> auth
                        // /actuator/health debe responder incluso sin token
                        // (lo consulta el orquestador/balanceador para saber si
                        // la instancia esta viva, antes de que exista ningun
                        // usuario autenticado en la request).
                        .requestMatchers("/actuator/health").permitAll()
                        // "GET /api/** requiere SCOPE_pedidos.read"
                        .requestMatchers(HttpMethod.GET, "/api/**").hasAuthority("SCOPE_pedidos.read")
                        // "el resto de /api/** requiere SCOPE_pedidos.write"
                        // (POST, PUT, DELETE, etc. bajo /api/**). La regla
                        // ADICIONAL "escribir o borrar productos requiere
                        // ademas ROLE_Admin" no se puede expresar aca con un
                        // simple matcher de URL (POST /api/productos necesita
                        // ROLE_Admin, pero POST /api/pedidos no) sin
                        // duplicar rutas; por eso esa parte se aplica con
                        // @PreAuthorize("hasRole('Admin')") directamente en
                        // los metodos de escritura/borrado de
                        // ProductosProxyController, que es donde realmente
                        // se sabe "esto es un producto".
                        .requestMatchers("/api/**").hasAuthority("SCOPE_pedidos.write")
                        // Defensa en profundidad: cualquier ruta futura no
                        // contemplada arriba exige, como minimo, estar
                        // autenticado. Nunca "permitAll" por omision.
                        .anyRequest().authenticated()
                )
                // Regla 7: 401 y 403 con el formato de error propio del
                // proyecto en vez de las paginas/JSON por defecto de Spring
                // Security. Ver el porque de cada uno en su propia clase.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new JsonAuthEntryPoint())
                        .accessDeniedHandler(new JsonAccessDeniedHandler())
                )
                // Activa el resource server: valida el JWT del header
                // Authorization con el decoder y el converter definidos abajo.
                .oauth2ResourceServer(oauth2 -> oauth2
                        // El entry point de exceptionHandling (arriba) NO cubre
                        // los fallos que ocurren DENTRO del filtro de bearer
                        // token: cuando el header Authorization trae algo que
                        // ni siquiera parsea como JWT, ese filtro responde con
                        // su propio entry point y el 401 sale con cuerpo vacio.
                        // Hay que declararlo tambien aca para que TODOS los 401
                        // lleven el JSON del proyecto.
                        .authenticationEntryPoint(new JsonAuthEntryPoint())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder())
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())
                        )
                );

        return http.build();
    }

    /**
     * Reglas 1, 2, 3 y 4: construye el JwtDecoder y le agrega TODAS las
     * validaciones que exige CLAUDE.md.
     *
     * - Regla 1 (firma): {@code NimbusJwtDecoder.withJwkSetUri(jwksUri)}
     *   descarga las claves publicas del tenant y verifica que el token
     *   este firmado con una de ellas. Sin esto, cualquiera podria fabricar
     *   un JWT con el contenido que quiera.
     * - Regla 4 (expiracion): viene incluida en
     *   {@code JwtValidators.createDefaultWithIssuer(...)} sin que
     *   tengamos que escribir nada.
     * - Regla 2 (issuer): tambien la agrega
     *   {@code createDefaultWithIssuer(issuer)}: rechaza tokens firmados
     *   por un tenant de Entra ID DISTINTO al nuestro (aunque la firma en
     *   si sea matematicamente valida, si no vino de NUESTRO
     *   login.microsoftonline.com/<tenant>, se rechaza).
     * - Regla 3 (audience): a proposito NO viene incluida arriba (por eso
     *   se agrega por separado con {@link AudienceValidator}; el porque
     *   esta explicado en esa clase).
     *
     * {@link DelegatingOAuth2TokenValidator} combina ambos validadores: el
     * token debe pasar TODOS, no solo uno.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();

        OAuth2TokenValidator<Jwt> validadorPorDefectoConIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> validadorDeAudience = new AudienceValidator(audience);
        OAuth2TokenValidator<Jwt> validadorCompleto =
                new DelegatingOAuth2TokenValidator<>(validadorPorDefectoConIssuer, validadorDeAudience);

        decoder.setJwtValidator(validadorCompleto);
        return decoder;
    }

    /**
     * Regla 5: mapea DOS claims distintos del mismo token a autoridades de
     * Spring Security, para poder usar ambas cosas en las reglas de
     * autorizacion (authorizeHttpRequests de arriba y @PreAuthorize en los
     * controladores):
     *
     * - claim {@code scp} (o {@code scope}) -> {@code SCOPE_pedidos.read},
     *   {@code SCOPE_pedidos.write}. Esto lo hace
     *   {@link JwtGrantedAuthoritiesConverter} por defecto, sin
     *   configuracion adicional (ya busca "scp" ademas de "scope").
     * - claim {@code roles} -> {@code ROLE_Admin}, {@code ROLE_Cliente}.
     *   Esto NO lo hace Spring Security por defecto (el nombre del claim de
     *   roles y su formato varian segun el proveedor de identidad), asi
     *   que se agrega a mano en {@link #autoridadesDeRoles(Jwt)}.
     *
     * El resultado final de un usuario Admin con scope de lectura y
     * escritura seria, por ejemplo: {@code [SCOPE_pedidos.read,
     * SCOPE_pedidos.write, ROLE_Admin]}.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter conversorDeScopes = new JwtGrantedAuthoritiesConverter();

        Converter<Jwt, Collection<GrantedAuthority>> conversorCombinado = jwt -> {
            List<GrantedAuthority> autoridades = new ArrayList<>(conversorDeScopes.convert(jwt));
            autoridades.addAll(autoridadesDeRoles(jwt));
            return autoridades;
        };

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(conversorCombinado);
        return converter;
    }

    private List<GrantedAuthority> autoridadesDeRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol))
                .toList();
    }
}

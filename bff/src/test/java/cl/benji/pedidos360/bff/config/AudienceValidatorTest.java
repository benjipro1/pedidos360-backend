package cl.benji.pedidos360.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * La validacion de audience es la unica de las cuatro reglas de JWT
 * (firma, issuer, audience, expiracion) que esta escrita a mano en este
 * proyecto: las otras tres las aporta Spring Security. Por eso es la que
 * puede romperse por un cambio nuestro sin que nada mas lo note, y la que
 * conviene cubrir con un test.
 */
class AudienceValidatorTest {

    private static final String AUDIENCIA_ESPERADA = "b5c90e11-373c-4846-a4e0-5440187d52de";

    private final AudienceValidator validator = new AudienceValidator(AUDIENCIA_ESPERADA);

    @Test
    void aceptaTokenConLaAudienciaEsperada() {
        OAuth2TokenValidatorResult resultado = validator.validate(tokenConAudiencias(AUDIENCIA_ESPERADA));

        assertThat(resultado.hasErrors()).isFalse();
    }

    @Test
    void aceptaTokenQueIncluyeLaAudienciaEsperadaEntreVarias() {
        OAuth2TokenValidatorResult resultado =
                validator.validate(tokenConAudiencias("otra-api", AUDIENCIA_ESPERADA));

        assertThat(resultado.hasErrors()).isFalse();
    }

    @Test
    void rechazaTokenEmitidoParaOtraAplicacion() {
        OAuth2TokenValidatorResult resultado = validator.validate(tokenConAudiencias("otra-aplicacion"));

        assertThat(resultado.hasErrors()).isTrue();
    }

    @Test
    void rechazaTokenSinClaimAud() {
        Jwt sinAudiencia = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("scp", "pedidos.read")
                .build();

        OAuth2TokenValidatorResult resultado = validator.validate(sinAudiencia);

        assertThat(resultado.hasErrors()).isTrue();
    }

    private Jwt tokenConAudiencias(String... audiencias) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("aud", List.of(audiencias))
                .build();
    }
}

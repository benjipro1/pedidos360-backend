package cl.benji.pedidos360.bff.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Por que existe esta clase: Spring Security valida por defecto la firma
 * (contra el JWKS), el issuer (si se lo pedimos) y la expiracion, pero NO
 * valida el audience (claim "aud") aunque se lo configuremos como issuer.
 * No es un descuido: Spring Security es generico para cualquier proveedor
 * OAuth2/OIDC, y "cual debe ser el audience valido" es una decision de
 * cada aplicacion (una API puede aceptar tokens emitidos para varios
 * clientes distintos), asi que la libreria no puede adivinarlo. Por eso
 * hay que escribir este validador a mano y agregarlo explicitamente
 * (regla 3 de CLAUDE.md).
 *
 * Sin esto, un token firmado por NUESTRO tenant de Entra ID pero emitido
 * para OTRA aplicacion completamente distinta (otro client_id, con sus
 * propios scopes/permisos) pasaria igual la validacion, porque la firma y
 * el issuer (el tenant) serian validos. El audience es lo que confirma que
 * el token fue emitido especificamente PARA esta API (api://b5c90e11-...).
 */
public class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error ERROR_AUDIENCE_INVALIDA = new OAuth2Error(
            "invalid_token",
            "El token no fue emitido para la audiencia esperada (claim aud)",
            null);

    private final String audienciaEsperada;

    public AudienceValidator(String audienciaEsperada) {
        this.audienciaEsperada = audienciaEsperada;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (jwt.getAudience() != null && jwt.getAudience().contains(audienciaEsperada)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(ERROR_AUDIENCE_INVALIDA);
    }
}

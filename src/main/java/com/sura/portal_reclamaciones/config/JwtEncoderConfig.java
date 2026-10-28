
package com.sura.portal_reclamaciones.config;

import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

/**
 * Configuración encargada de crear el firmador de tokens JWT.
 *
 * Utilizamos la misma clave secreta que SecurityConfig emplea para
 * validar los tokens que llegan en las peticiones al backend.
 */
@Configuration
public class JwtEncoderConfig {

    /**
     * Spring administra este componente y lo entrega al servicio
     * de autenticación cuando necesite emitir un token.
     *
     * La clave se obtiene de la variable JWT_SECRET, configurada
     * mediante app.jwt.secret en application.properties.
     */
    @Bean
    public JwtEncoder jwtEncoder(
            @Value("${app.jwt.secret}") String jwtSecret) {

        // Convertimos la clave codificada en Base64 a sus bytes originales.
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecret);

        // HS256 requiere una clave de al menos 32 bytes (256 bits).
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "La clave JWT debe tener al menos 32 bytes para HS256."
            );
        }

        // Indicamos el algoritmo que utilizará la clave para firmar los JWT.
        SecretKey secretKey = new SecretKeySpec(
                keyBytes,
                "HmacSHA256"
        );

        /*
         * Nimbus utiliza esta clave para firmar los tokens.
         * La misma clave debe estar disponible para el decodificador
         * que verifica la firma en las peticiones posteriores.
         */
        ImmutableSecret<SecurityContext> fuenteClave =
                new ImmutableSecret<>(secretKey);

        return new NimbusJwtEncoder(fuenteClave);
    }
}

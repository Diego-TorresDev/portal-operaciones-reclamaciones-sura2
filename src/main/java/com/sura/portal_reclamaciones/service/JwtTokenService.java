
package com.sura.portal_reclamaciones.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import com.sura.portal_reclamaciones.entity.Usuario;

/**
 * Se encarga de crear el token que recibe un usuario después
 * de iniciar sesión correctamente.
 *
 * La verificación de la contraseña se hará en el servicio de autenticación.
 * Esta clase se concentra en generar el token con los datos necesarios
 * para identificar al usuario y reconocer su rol en las peticiones.
 */
@Service
public class JwtTokenService {

    // Spring proporciona el firmador que configuramos en JwtEncoderConfig.
    private final JwtEncoder jwtEncoder;

    // El token tendrá una duración limitada para reducir el riesgo de uso indebido.
    private static final Duration DURACION_TOKEN = Duration.ofMinutes(15);

    public JwtTokenService(JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    /**
     * Construye y firma un token JWT para el usuario autenticado.
     *
     * Incluimos el nombre de usuario como subject y el rol como scope.
     * Spring Security podrá convertir ese scope en una autoridad
     * que luego utilizaremos para restringir las funciones administrativas.
     */
    public String generarToken(Usuario usuario) {

        Instant ahora = Instant.now();

        // Definimos los datos que viajarán dentro del token.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("portal-reclamaciones-sura")
                .issuedAt(ahora)
                .expiresAt(ahora.plus(DURACION_TOKEN))
                .subject(usuario.getUsername())
                .claim("scope", usuario.getRole().name())
                .claim("display_name", usuario.getDisplayName())
                .build();

        // Indicamos HS256, el mismo algoritmo utilizado por el decodificador.
        JwsHeader encabezado = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        // Firmamos el token y devolvemos su representación compacta.
        return jwtEncoder
                .encode(JwtEncoderParameters.from(encabezado, claims))
                .getTokenValue();
    }
}

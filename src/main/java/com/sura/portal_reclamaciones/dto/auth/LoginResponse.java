
package com.sura.portal_reclamaciones.dto.auth;

/**
 * Respuesta que recibe el cliente cuando el inicio de sesión es correcto.
 *
 * Solo entregamos el token y los datos básicos para mostrar al usuario
 * y adaptar la interfaz a su rol. No devolvemos la entidad de PostgreSQL.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String username,
        String nombre,
        String rol
) {

    /**
     * Construye la respuesta con el formato Bearer que espera
     * el mecanismo de autenticación JWT de Spring Security.
     */
    public static LoginResponse desde(
            String token,
            String username,
            String nombre,
            String rol) {

        // La duración coincide con los 15 minutos definidos en JwtTokenService.
        return new LoginResponse(
                token,
                "Bearer",
                900,
                username,
                nombre,
                rol
        );
    }
}

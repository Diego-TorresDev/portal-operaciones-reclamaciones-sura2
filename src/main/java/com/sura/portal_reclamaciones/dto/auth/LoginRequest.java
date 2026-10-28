
package com.sura.portal_reclamaciones.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Datos que el cliente envía para iniciar sesión.
 *
 * Recibimos únicamente el nombre de usuario y la contraseña;
 * no necesitamos que el cliente envíe el rol ni el estado de la cuenta.
 */
public record LoginRequest(

        // El nombre debe tener contenido y respetar el tamaño de la columna.
        @NotBlank(message = "El nombre de usuario es obligatorio.")
        @Size(max = 80, message = "El nombre de usuario supera el tamaño permitido.")
        String username,

        // Evitamos solicitudes sin contraseña y limitamos el tamaño recibido.
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(max = 200, message = "La contraseña supera el tamaño permitido.")
        String password

) {
}


package com.sura.portal_reclamaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuración para el manejo seguro de contraseñas.
 *
 * Centralizamos el codificador para que el registro de usuarios
 * y el inicio de sesión utilicen el mismo mecanismo de verificación.
 */
@Configuration
public class PasswordConfig {

    /**
     * BCrypt transforma una contraseña en un hash que se puede guardar
     * en PostgreSQL. Al iniciar sesión, permite comprobar la contraseña
     * escrita sin tener que recuperar la contraseña original.
     *
     * Spring administra este objeto y lo entrega a los componentes
     * que necesiten codificar o verificar contraseñas.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

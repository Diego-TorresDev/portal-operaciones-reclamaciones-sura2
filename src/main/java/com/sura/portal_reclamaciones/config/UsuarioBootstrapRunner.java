
package com.sura.portal_reclamaciones.config;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sura.portal_reclamaciones.entity.UserRole;
import com.sura.portal_reclamaciones.entity.Usuario;
import com.sura.portal_reclamaciones.repository.UsuarioRepository;

/**
 * Prepara las cuentas de prueba del portal.
 *
 * En el arranque normal, solo crea las que falten. El cambio de contraseñas
 * existentes exige habilitar una segunda opción de forma deliberada.
 */
@Component
@ConditionalOnProperty(
        name = "app.bootstrap-users.enabled",
        havingValue = "true"
)
public class UsuarioBootstrapRunner implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(UsuarioBootstrapRunner.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String passwordAnalista;
    private final String passwordAdmin;
    private final boolean restablecerPasswords;

    public UsuarioBootstrapRunner(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${APP_ANALISTA_PASSWORD:}") String passwordAnalista,
            @Value("${APP_ADMIN_PASSWORD:}") String passwordAdmin,
            @Value("${app.bootstrap-users.reset-passwords:false}")
            boolean restablecerPasswords) {

        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordAnalista = passwordAnalista;
        this.passwordAdmin = passwordAdmin;
        this.restablecerPasswords = restablecerPasswords;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        /*
         * Solo restablecemos las cuentas si la opción se habilitó
         * expresamente y ambas contraseñas nuevas están configuradas.
         */
        if (restablecerPasswords) {
            if (!esPasswordValida(passwordAnalista)
                    || !esPasswordValida(passwordAdmin)) {
                throw new IllegalStateException(
                        "Configura ambas contraseñas de prueba "
                        + "con al menos 12 caracteres."
                );
            }

            crearOActualizar(
                    "analista1",
                    "Analista de prueba",
                    UserRole.ANALISTA,
                    passwordAnalista
            );

            crearOActualizar(
                    "admin1",
                    "Administrador de prueba",
                    UserRole.ADMIN,
                    passwordAdmin
            );

            log.warn(
                    "Se actualizaron las credenciales de las cuentas de prueba."
            );
            return;
        }

        // El arranque normal nunca reemplaza las contraseñas existentes.
        crearSiFalta(
                "analista1",
                "Analista de prueba",
                UserRole.ANALISTA,
                passwordAnalista
        );

        crearSiFalta(
                "admin1",
                "Administrador de prueba",
                UserRole.ADMIN,
                passwordAdmin
        );
    }

    /**
     * Crea la cuenta o actualiza su contraseña cuando el restablecimiento
     * fue solicitado expresamente desde las variables de entorno.
     */
    private void crearOActualizar(
            String username,
            String nombre,
            UserRole rol,
            String password) {

        Usuario usuario = usuarioRepository
                .findByUsername(username)
                .orElseGet(Usuario::new);

        usuario.setUsername(username);
        usuario.setDisplayName(nombre);
        usuario.setRole(rol);
        usuario.setActive(true);

        // PostgreSQL guarda únicamente el resultado de BCrypt.
        usuario.setPasswordHash(passwordEncoder.encode(password));

        // Una cuenta de prueba restablecida empieza sin bloqueos previos.
        usuario.setFailedLoginAttempts(0);
        usuario.setLockedUntil(null);
        usuario.setPasswordChangedAt(Instant.now());

        usuarioRepository.save(usuario);

        // Por seguridad, nunca registramos la contraseña ni su hash.
        log.info("Credenciales de prueba actualizadas para {}.", username);
    }

    /**
     * En un arranque normal, creamos una cuenta únicamente si no existe.
     */
    private void crearSiFalta(
            String username,
            String nombre,
            UserRole rol,
            String password) {

        if (usuarioRepository.existsByUsername(username)) {
            log.info(
                    "La cuenta {} ya existe. No se hicieron cambios.",
                    username
            );
            return;
        }

        if (!esPasswordValida(password)) {
            throw new IllegalStateException(
                    "Configura la contraseña de " + username
                    + " con al menos 12 caracteres."
            );
        }

        crearOActualizar(username, nombre, rol, password);
        log.info("Se creó la cuenta de prueba {}.", username);
    }

    private boolean esPasswordValida(String password) {
        return password != null && password.length() >= 12;
    }
}

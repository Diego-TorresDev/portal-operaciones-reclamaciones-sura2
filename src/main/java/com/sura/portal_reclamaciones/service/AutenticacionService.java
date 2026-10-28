
package com.sura.portal_reclamaciones.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sura.portal_reclamaciones.entity.AuditLog;
import com.sura.portal_reclamaciones.entity.Usuario;
import com.sura.portal_reclamaciones.repository.AuditLogRepository;
import com.sura.portal_reclamaciones.repository.UsuarioRepository;

/**
 * Contiene las comprobaciones necesarias para iniciar sesión.
 *
 * Aquí verificamos la cuenta y su contraseña. Cuando todo está bien,
 * delegamos la creación del JWT en JwtTokenService.
 */
@Service
public class AutenticacionService {

    // Tras cinco intentos incorrectos se bloquea temporalmente la cuenta.
    private static final int MAX_INTENTOS_FALLIDOS = 5;

    // El bloqueo dura quince minutos desde el quinto intento fallido.
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private final UsuarioRepository usuarioRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    public AutenticacionService(
            UsuarioRepository usuarioRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService) {

        this.usuarioRepository = usuarioRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * Comprueba las credenciales y genera el token solo si el acceso
     * está permitido. Los rechazos se devuelven como resultado para
     * poder guardar los intentos fallidos sin revertir la transacción.
     */
    @Transactional
    public ResultadoLogin autenticar(
            String usernameIngresado,
            String passwordIngresada) {

        // Unificamos el nombre para que no importe cómo se escriben las mayúsculas.
        String username = normalizarUsername(usernameIngresado);
        String actor = nombreParaAuditoria(username);

        // No consultamos la base de datos si los datos vienen incompletos.
        if (username.isBlank()
                || username.length() > 80
                || passwordIngresada == null
                || passwordIngresada.isEmpty()) {

            registrarAuditoria(
                    actor, null, "RECHAZADO",
                    "Intento de inicio de sesión rechazado.",
                    "DATOS_INVALIDOS", null, false
            );

            return ResultadoLogin.rechazado();
        }

        // Buscamos al usuario mediante el repositorio JPA existente.
        Optional<Usuario> encontrado =
                usuarioRepository.findByUsername(username);

        /*
         * Usamos la misma respuesta externa para usuario inexistente
         * y contraseña incorrecta, para no revelar qué cuentas existen.
         */
        if (encontrado.isEmpty()) {
            registrarAuditoria(
                    actor, null, "RECHAZADO",
                    "Intento de inicio de sesión rechazado.",
                    "CREDENCIALES_INVALIDAS", null, false
            );

            return ResultadoLogin.rechazado();
        }

        Usuario usuario = encontrado.get();
        Instant ahora = Instant.now();

        // Las cuentas desactivadas no pueden iniciar sesión.
        if (!usuario.isActive()) {
            registrarAuditoria(
                    username, usuario.getId(), "RECHAZADO",
                    "Intento de inicio de sesión rechazado.",
                    "CUENTA_INACTIVA",
                    usuario.getFailedLoginAttempts(), false
            );

            return ResultadoLogin.rechazado();
        }

        // Si el bloqueo sigue vigente, rechazamos el acceso sin verificar la clave.
        if (usuario.getLockedUntil() != null
                && usuario.getLockedUntil().isAfter(ahora)) {

            registrarAuditoria(
                    username, usuario.getId(), "RECHAZADO",
                    "Intento de inicio de sesión rechazado.",
                    "CUENTA_BLOQUEADA",
                    usuario.getFailedLoginAttempts(), true
            );

            return ResultadoLogin.rechazado();
        }

        /*
         * Si el bloqueo anterior ya venció, limpiamos sus datos.
         * Un nuevo error comenzará un conteo desde cero.
         */
        if (usuario.getLockedUntil() != null) {
            usuario.setLockedUntil(null);
            usuario.setFailedLoginAttempts(0);
        }

        /*
         * BCrypt compara la contraseña ingresada con el hash de PostgreSQL.
         * Nunca guardamos la contraseña original ni la escribimos en la bitácora.
         */
        boolean claveCorrecta = usuario.getPasswordHash() != null
                && passwordEncoder.matches(
                        passwordIngresada,
                        usuario.getPasswordHash()
                );

        if (!claveCorrecta) {
            int intentos = usuario.getFailedLoginAttempts() + 1;
            boolean debeBloquear = intentos >= MAX_INTENTOS_FALLIDOS;

            usuario.setFailedLoginAttempts(intentos);

            if (debeBloquear) {
                usuario.setLockedUntil(ahora.plus(DURACION_BLOQUEO));
            }

            // Persistimos el contador y la fecha de bloqueo, si aplica.
            usuarioRepository.save(usuario);

            registrarAuditoria(
                    username, usuario.getId(), "RECHAZADO",
                    "Intento de inicio de sesión rechazado.",
                    debeBloquear
                            ? "LIMITE_INTENTOS_ALCANZADO"
                            : "CREDENCIALES_INVALIDAS",
                    intentos, debeBloquear
            );

            return ResultadoLogin.rechazado();
        }

        /*
         * Con las credenciales correctas, restablecemos el contador
         * y guardamos la fecha del acceso exitoso.
         */
        usuario.setFailedLoginAttempts(0);
        usuario.setLockedUntil(null);
        usuario.setLastLoginAt(ahora);

        usuarioRepository.save(usuario);

        // El JWT solo se genera después de comprobar la cuenta y la contraseña.
        String token = jwtTokenService.generarToken(usuario);

        registrarAuditoria(
                username, usuario.getId(), "OK",
                "Inicio de sesión exitoso.",
                "ACCESO_CORRECTO", 0, false
        );

        // Devolvemos al futuro controlador los datos necesarios para la respuesta.
        return new ResultadoLogin(
                true,
                token,
                usuario.getUsername(),
                usuario.getDisplayName(),
                usuario.getRole().name()
        );
    }

    // Normalizamos el nombre para respetar la restricción de minúsculas de PostgreSQL.
    private String normalizarUsername(String username) {
        if (username == null) {
            return "";
        }

        return username.trim().toLowerCase(Locale.ROOT);
    }

    // Quitamos caracteres de control y respetamos el límite de la columna de auditoría.
    private String nombreParaAuditoria(String username) {
        if (username == null || username.isBlank()) {
            return "ANONIMO";
        }

        String limpio = username.replaceAll("[\\p{Cntrl}]", "");

        if (limpio.isBlank()) {
            return "ANONIMO";
        }

        return limpio.length() > 80
                ? limpio.substring(0, 80)
                : limpio;
    }

    /**
     * Mantiene un formato único para todos los registros de acceso.
     * Los detalles describen el resultado, pero nunca contienen contraseñas,
     * hashes de contraseña ni tokens JWT.
     */
    private void registrarAuditoria(
            String actor,
            Long actorId,
            String resultado,
            String mensaje,
            String motivo,
            Integer intentosFallidos,
            boolean cuentaBloqueada) {

        AuditLog registro = new AuditLog();

        registro.setActorUsername(nombreParaAuditoria(actor));
        registro.setActorUserId(actorId);
        registro.setActionCode("AUTH_LOGIN");
        registro.setResourceType("USUARIO");
        registro.setResourceId(nombreParaAuditoria(actor));
        registro.setResult(resultado);
        registro.setMessage(mensaje);

        Map<String, Object> detalles = new HashMap<>();
        detalles.put("motivo", motivo);
        detalles.put("cuentaBloqueada", cuentaBloqueada);

        if (intentosFallidos != null) {
            detalles.put("intentosFallidos", intentosFallidos);
        }

        registro.setDetails(detalles);
        auditLogRepository.save(registro);
    }

    /**
     * Resultado que usará el controlador HTTP para responder al cliente.
     * Cuando el acceso falla, no entregamos el token ni datos de la cuenta.
     */
    public record ResultadoLogin(
            boolean autenticado,
            String token,
            String username,
            String nombre,
            String rol) {

        public static ResultadoLogin rechazado() {
            return new ResultadoLogin(false, null, null, null, null);
        }
    }
}

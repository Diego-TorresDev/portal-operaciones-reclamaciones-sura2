
package com.sura.portal_reclamaciones.controller;

import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sura.portal_reclamaciones.dto.auth.LoginRequest;
import com.sura.portal_reclamaciones.dto.auth.LoginResponse;
import com.sura.portal_reclamaciones.service.AutenticacionService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Recibe las solicitudes de inicio de sesión del portal.
 *
 * El controlador se ocupa de la comunicación HTTP. La comprobación
 * de las credenciales y la generación del JWT permanecen en los
 * servicios correspondientes, para no mezclar responsabilidades.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacionService autenticacionService;

    public AuthController(AutenticacionService autenticacionService) {
        this.autenticacionService = autenticacionService;
    }

    /**
     * Recibe usuario y contraseña y responde según el resultado del acceso.
     *
     * Usamos boundedElastic porque el servicio consulta PostgreSQL mediante
     * JPA, que es bloqueante. Así evitamos ejecutar esas consultas en el
     * hilo principal que Netty utiliza para atender solicitudes reactivas.
     */
    @PostMapping("/login")
    public Mono<ResponseEntity<?>> login(
            @Valid @RequestBody LoginRequest solicitud) {

        return Mono.fromCallable(() ->
                        autenticacionService.autenticar(
                                solicitud.username(),
                                solicitud.password()
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultado -> {

                    // No revelamos si falló el usuario, la clave o el estado de la cuenta.
                    if (!resultado.autenticado()) {
                        ResponseEntity<?> respuesta = ResponseEntity
                                .status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of(
                                        "mensaje",
                                        "Usuario o contraseña incorrectos."
                                ));

                        return respuesta;
                    }

                    // Solo un acceso válido recibe el token y los datos básicos del usuario.
                    LoginResponse cuerpo = LoginResponse.desde(
                            resultado.token(),
                            resultado.username(),
                            resultado.nombre(),
                            resultado.rol()
                    );

                    return ResponseEntity.ok(cuerpo);
                });
    }
}


package com.sura.portal_reclamaciones.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sura.portal_reclamaciones.dto.parametros.ActualizarParametroRequest;
import com.sura.portal_reclamaciones.service.ParametroService;
import com.sura.portal_reclamaciones.service.ParametroService.ParametroVista;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Expone las operaciones de consulta y modificación de parámetros.
 *
 * La autorización se aplica en SecurityConfig: cualquier usuario
 * autenticado puede consultar, pero solo ADMIN puede modificar.
 */
@RestController
@RequestMapping("/api/parametros")
public class ParametroController {

    private final ParametroService parametroService;

    public ParametroController(ParametroService parametroService) {
        this.parametroService = parametroService;
    }

    /**
     * Devuelve la configuración vigente, ordenada por clave.
     *
     * Como JPA trabaja de forma bloqueante, ejecutamos la consulta
     * en boundedElastic para no bloquear el hilo de Netty.
     */
    @GetMapping
    public Mono<List<ParametroVista>> listar() {

        return Mono.fromCallable(parametroService::listar)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Actualiza un parámetro y registra el cambio en el historial.
     *
     * El nombre del usuario viene del subject del JWT que Spring ya validó.
     * No confiamos en un username enviado por el cliente en el cuerpo.
     */
    @PutMapping("/{clave}")
    public Mono<ResponseEntity<Object>> actualizar(
            @PathVariable String clave,
            @Valid @RequestBody ActualizarParametroRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {

        return Mono.fromCallable(() ->
                        parametroService.actualizar(
                                clave,
                                solicitud.valor(),
                                solicitud.motivo(),
                                jwt.getSubject()
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultado -> ResponseEntity.ok((Object) resultado))
                .onErrorResume(
                        IllegalArgumentException.class,
                        error -> Mono.just(
                                ResponseEntity.badRequest()
                                        .body(Map.of("mensaje", error.getMessage()))
                        )
                );
    }
}

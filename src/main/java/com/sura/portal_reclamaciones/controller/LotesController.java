
package com.sura.portal_reclamaciones.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sura.portal_reclamaciones.service.LotesService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import org.springframework.web.bind.annotation.PatchMapping;

/**
 * Expone la generación de lotes de pago simulados.
 *
 * Las operaciones de JPA son bloqueantes; se ejecutan en boundedElastic
 * para no bloquear los hilos de Netty.
 */
@RestController
@RequestMapping("/api/lotes")
public class LotesController {

    private final LotesService lotesService;

    public LotesController(LotesService lotesService) {
        this.lotesService = lotesService;
    }

    /**
     * Genera un lote con los casos aprobados que todavía no están
     * incluidos en otro lote.
     *
     * Endpoint: POST /api/lotes
     */
    @PostMapping
    public Mono<ResponseEntity<Object>> generarLote(
            @AuthenticationPrincipal Jwt jwt) {

        return Mono.fromCallable(() ->
                        lotesService.generarLote(jwt.getSubject())
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultado -> {
                    // Fijamos el tipo del cuerpo para evitar inferencias incompatibles.
                    ResponseEntity<Object> respuesta =
                            ResponseEntity
                                    .status(HttpStatus.CREATED)
                                    .body((Object) resultado);

                    return respuesta;
                })
                .onErrorResume(
                        ResponseStatusException.class,
                        error -> {
                            String mensaje = error.getReason() == null
                                    ? "No fue posible generar el lote."
                                    : error.getReason();

                            // La respuesta de error utiliza exactamente el mismo tipo.
                            ResponseEntity<Object> respuesta =
                                    ResponseEntity
                                            .status(error.getStatusCode())
                                            .body((Object) Map.of(
                                                    "mensaje",
                                                    mensaje
                                            ));

                            return Mono.just(respuesta);
                        }
                );
    }

    
/**
 * Confirma el pago simulado de un lote.
 * La autorización ADMIN se aplica en SecurityConfig.
 *
 * PATCH /api/lotes/{id}/pagar
 */
@PatchMapping("/{id}/pagar")
public Mono<ResponseEntity<Object>> confirmarPago(
        @org.springframework.web.bind.annotation.PathVariable Long id,
        @AuthenticationPrincipal Jwt jwt) {

    return Mono.fromCallable(() ->
                    lotesService.confirmarPago(
                            id,
                            jwt.getSubject()
                    )
            )
            .subscribeOn(Schedulers.boundedElastic())
            .map(resultado -> {
                ResponseEntity<Object> respuesta =
                        ResponseEntity.ok((Object) resultado);
                return respuesta;
            })
            .onErrorResume(
                    ResponseStatusException.class,
                    error -> {
                        String mensaje = error.getReason() == null
                                ? "No fue posible confirmar el pago."
                                : error.getReason();

                        ResponseEntity<Object> respuesta =
                                ResponseEntity
                                        .status(error.getStatusCode())
                                        .body((Object) Map.of(
                                                "mensaje",
                                                mensaje
                                        ));

                        return Mono.just(respuesta);
                    }
            );
}

}

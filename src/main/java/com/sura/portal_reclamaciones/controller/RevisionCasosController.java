
package com.sura.portal_reclamaciones.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sura.portal_reclamaciones.service.RevisionCasosService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Expone las operaciones de revisión manual de reclamaciones.
 *
 * El usuario se identifica mediante el JWT validado por Spring Security.
 * Las operaciones JPA bloqueantes se ejecutan en boundedElastic para
 * no bloquear los hilos de Netty.
 */
@RestController
@RequestMapping("/api/casos")
public class RevisionCasosController {

    private final RevisionCasosService revisionCasos;

    public RevisionCasosController(RevisionCasosService revisionCasos) {
        this.revisionCasos = revisionCasos;
    }

    /**
     * Asigna un caso de la bandeja manual al analista autenticado.
     *
     * POST /api/casos/{codigoCaso}/tomar
     */
    @PostMapping("/{codigoCaso}/tomar")
    public Mono<ResponseEntity<Object>> tomarCaso(
            @PathVariable String codigoCaso,
            @AuthenticationPrincipal Jwt jwt) {

        return Mono.fromCallable(() ->
                        revisionCasos.tomarCaso(
                                codigoCaso,
                                jwt.getSubject()
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultado ->
                        ResponseEntity.<Object>ok(resultado)
                )
                .onErrorResume(
                        ResponseStatusException.class,
                        this::respuestaError
                );
    }

    /**
     * Permite al analista asignado aprobar u objetar la reclamación.
     *
     * PATCH /api/casos/{codigoCaso}/estado
     *
     * Ejemplo del cuerpo JSON:
     * {
     *   "accion": "APROBAR",
     *   "observacion": "Documentación revisada y validada correctamente."
     * }
     */
    @PatchMapping("/{codigoCaso}/estado")
    public Mono<ResponseEntity<Object>> decidirCaso(
            @PathVariable String codigoCaso,
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, String> solicitud) {

        return Mono.fromCallable(() ->
                        revisionCasos.decidirCaso(
                                codigoCaso,
                                jwt.getSubject(),
                                solicitud == null
                                        ? null
                                        : solicitud.get("accion"),
                                solicitud == null
                                        ? null
                                        : solicitud.get("observacion")
                        )
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultado ->
                        ResponseEntity.<Object>ok(resultado)
                )
                .onErrorResume(
                        ResponseStatusException.class,
                        this::respuestaError
                );
    }

    /**
     * Devuelve los errores de negocio previstos sin exponer detalles internos.
     */
    private Mono<ResponseEntity<Object>> respuestaError(
            ResponseStatusException error) {

        String mensaje = error.getReason() == null
                ? "No fue posible procesar la solicitud."
                : error.getReason();

        ResponseEntity<Object> respuesta = ResponseEntity
                .status(error.getStatusCode())
                .body((Object) Map.of("mensaje", mensaje));

        return Mono.just(respuesta);
    }
}

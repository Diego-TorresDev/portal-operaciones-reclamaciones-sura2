
package com.sura.portal_reclamaciones.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sura.portal_reclamaciones.service.ConsultaCasosService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Expone las consultas de reclamaciones, su detalle y su historial.
 *
 * Las operaciones de JPA son bloqueantes; se ejecutan en boundedElastic
 * para no bloquear los hilos que Netty utiliza para atender peticiones.
 * Las rutas están protegidas por la configuración global de Spring Security.
 */
@RestController
@RequestMapping("/api/casos")
public class ConsultaCasosController {

    private final ConsultaCasosService consultaCasos;

    public ConsultaCasosController(ConsultaCasosService consultaCasos) {
        this.consultaCasos = consultaCasos;
    }

    /**
     * Consulta los casos con filtros y paginación.
     *
     * Ejemplo:
     * GET /api/casos?estado=EN_BANDEJA&prioridad=ALTA&pagina=0&tamano=20
     */
    @GetMapping
    public Mono<ResponseEntity<Object>> listarCasos(
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String prioridad,
            @RequestParam(required = false) String regla,
            @RequestParam(required = false) String semaforo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {

        return Mono.fromCallable(() ->
                        consultaCasos.listarCasos(
                                estado,
                                decision,
                                prioridad,
                                regla,
                                semaforo,
                                pagina,
                                tamano
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
     * Recupera los datos y la evaluación actual de una reclamación.
     * El servicio devuelve el documento completo únicamente en este detalle.
     *
     * Ejemplo:
     * GET /api/casos/RS-2026-0039
     */
    @GetMapping("/{identificador}")
    public Mono<ResponseEntity<Object>> obtenerDetalle(
            @PathVariable String identificador) {

        return Mono.fromCallable(() ->
                        consultaCasos.obtenerDetalle(identificador)
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
     * Devuelve el historial de cambios de estado en orden cronológico.
     *
     * Ejemplo:
     * GET /api/casos/RS-2026-0039/tracking
     */
    @GetMapping("/{identificador}/tracking")
    public Mono<ResponseEntity<Object>> obtenerTracking(
            @PathVariable String identificador) {

        return Mono.fromCallable(() ->
                        consultaCasos.obtenerTracking(identificador)
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
     * Devuelve los errores de consulta previstos sin exponer detalles internos.
     */
    private Mono<ResponseEntity<Object>> respuestaError(
            ResponseStatusException error) {

        String mensaje = error.getReason() == null
                ? "No fue posible consultar la información solicitada."
                : error.getReason();

        ResponseEntity<Object> respuesta = ResponseEntity
                .status(error.getStatusCode())
                .body((Object) Map.of("mensaje", mensaje));

        return Mono.just(respuesta);
    }
}

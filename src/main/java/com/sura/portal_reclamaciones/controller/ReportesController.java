
package com.sura.portal_reclamaciones.controller;

import java.util.Map;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.sura.portal_reclamaciones.service.ReportesService;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Expone los indicadores operativos y la exportación de reclamaciones.
 *
 * Las rutas quedan protegidas por la configuración global de Spring Security.
 * Las consultas JPA se ejecutan en boundedElastic porque son bloqueantes.
 */
@RestController
@RequestMapping("/api/reportes")
public class ReportesController {

    private final ReportesService reportesService;

    public ReportesController(ReportesService reportesService) {
        this.reportesService = reportesService;
    }

    /**
     * Devuelve los indicadores del tablero operativo.
     *
     * Endpoint: GET /api/reportes/resumen
     */
    @GetMapping("/resumen")
    public Mono<ResponseEntity<Object>> obtenerResumen() {

        return Mono.fromCallable(() ->
                        reportesService.obtenerResumen()
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultado ->
                        ResponseEntity.<Object>ok(resultado)
                )
                .onErrorResume(
                        ResponseStatusException.class,
                        error -> respuestaError(error)
                );
    }

    /**
     * Exporta todos los casos a un CSV UTF-8 con protección contra fórmulas.
     *
     * Endpoint: GET /api/reportes/exportar
     */
    @GetMapping("/exportar")
    public Mono<ResponseEntity<ByteArrayResource>> exportarResultados() {

        return Mono.fromCallable(() ->
                        reportesService.exportarResultadosCsv()
                )
                .subscribeOn(Schedulers.boundedElastic())
                .map(contenido -> {
                    ByteArrayResource archivo =
                            new ByteArrayResource(contenido);

                    // Indicamos al navegador que descargue el archivo CSV.
                    HttpHeaders encabezados = new HttpHeaders();
                    encabezados.setContentDisposition(
                            ContentDisposition.attachment()
                                    .filename("resultados_reclamaciones.csv")
                                    .build()
                    );
                    encabezados.setContentLength(contenido.length);

                    return ResponseEntity.ok()
                            .headers(encabezados)
                            .contentType(
                                    MediaType.parseMediaType(
                                            "text/csv;charset=UTF-8"
                                    )
                            )
                            .body(archivo);
                });
    }

    /**
     * Devuelve errores de negocio previstos sin revelar detalles internos.
     */
    private Mono<ResponseEntity<Object>> respuestaError(
            ResponseStatusException error) {

        String mensaje = error.getReason() == null
                ? "No fue posible generar el reporte solicitado."
                : error.getReason();

        ResponseEntity<Object> respuesta = ResponseEntity
                .status(error.getStatusCode())
                .body((Object) Map.of("mensaje", mensaje));

        return Mono.just(respuesta);
    }
}

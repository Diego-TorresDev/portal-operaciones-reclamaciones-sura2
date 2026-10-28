package com.sura.portal_reclamaciones.controller;

import com.sura.portal_reclamaciones.dto.importacion.ResultadoEvaluacionLote;
import com.sura.portal_reclamaciones.service.MotorReglasService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/casos")
public class EvaluacionController {

    private static final Logger log = LoggerFactory.getLogger(EvaluacionController.class);
    private final MotorReglasService motorReglas;

    public EvaluacionController(MotorReglasService motorReglas) {
        this.motorReglas = motorReglas;
    }

    /** POST /api/casos/evaluar: evalúa los casos elegibles y registra el usuario solicitante. */
    @PostMapping("/evaluar")
    public Mono<ResponseEntity<ResultadoEvaluacionLote>> evaluarCasos(
            @AuthenticationPrincipal Jwt jwt) {

        String usuario = jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()
                ? "SISTEMA" : jwt.getSubject();

        return Mono.fromCallable(() -> motorReglas.evaluarPendientes(usuario))
                .subscribeOn(Schedulers.boundedElastic())
                .map(resultado -> ResponseEntity.ok(resultado))
                .onErrorResume(error -> {
                    // El detalle técnico queda en el log, no en la respuesta HTTP.
                    log.error("Falló la evaluación de las reclamaciones.", error);
                    ResultadoEvaluacionLote respuesta = new ResultadoEvaluacionLote(
                            0, 0, 0, 0, 0,
                            "No se pudo completar la evaluación. Revisa los registros del servidor.",
                            java.util.List.of("La evaluación no terminó correctamente.")
                    );
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(respuesta));
                });
    }
}

package com.sura.portal_reclamaciones.controller;

import com.sura.portal_reclamaciones.dto.importacion.ResultadoImportacionExcel;
import com.sura.portal_reclamaciones.dto.importacion.ResultadoValidacionExcel;
import com.sura.portal_reclamaciones.service.ExcelReclamacionesImportService;
import com.sura.portal_reclamaciones.service.ExcelReclamacionesService;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// Este controlador recibe el Excel y delega el trabajo.
// La validación revisa la estructura; la importación guarda los registros.
@RestController
@RequestMapping("/api/casos/carga")
public class CargaExcelController {

    // La prueba técnica establece un máximo de 5 MB por archivo.
    private static final int TAMANO_MAXIMO_BYTES = 5 * 1024 * 1024;

    private final ExcelReclamacionesService excelService;
    private final ExcelReclamacionesImportService importService;

    // Spring proporciona ambos servicios cuando crea el controlador.
    public CargaExcelController(
            ExcelReclamacionesService excelService,
            ExcelReclamacionesImportService importService
    ) {
        this.excelService = excelService;
        this.importService = importService;
    }

    // -----------------------------------------------------------------
    // POST /api/casos/carga/validar
    // Revisa las hojas y los encabezados sin guardar datos en PostgreSQL.
    // -----------------------------------------------------------------
    @PostMapping(
            path = "/validar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ResponseEntity<ResultadoValidacionExcel>> validarExcel(
            @RequestPart("archivo") FilePart archivo
    ) {
        String nombreArchivo = archivo.filename();

        // No aceptamos extensiones diferentes de .xlsx.
        if (!esArchivoExcelPermitido(nombreArchivo)) {
            return Mono.just(ResponseEntity.badRequest().body(
                    new ResultadoValidacionExcel(
                            false,
                            nombreArchivo,
                            "Solo se permiten archivos con extensión .xlsx.",
                            Map.of(),
                            List.of("La extensión del archivo no está permitida.")
                    )
            ));
        }

        // Si conocemos el tamaño del archivo, rechazamos los que superen el límite.
        long tamanoDeclarado = archivo.headers().getContentLength();

        if (tamanoDeclarado > TAMANO_MAXIMO_BYTES) {
            return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                    new ResultadoValidacionExcel(
                            false,
                            nombreArchivo,
                            "El archivo supera el máximo de 5 MB.",
                            Map.of(),
                            List.of("Reduce el tamaño del archivo e inténtalo de nuevo.")
                    )
            ));
        }

        // Limitamos los bytes recibidos. La lectura de Excel es bloqueante,
        // por eso la ejecutamos fuera del hilo principal de Netty.
        return DataBufferUtils.join(archivo.content(), TAMANO_MAXIMO_BYTES)
                .map(this::convertirBytesYLiberar)
                .flatMap(contenido ->
                        Mono.fromCallable(() ->
                                        excelService.validarEstructura(
                                                nombreArchivo,
                                                contenido
                                        ))
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .<ResponseEntity<ResultadoValidacionExcel>>map(resultado -> {
                    if (resultado.valido()) {
                        return ResponseEntity.ok(resultado);
                    }

                    return ResponseEntity.badRequest().body(resultado);
                })
                // Si el archivo excede el límite durante la recepción,
                // devolvemos 413 en lugar de intentar procesarlo.
                .onErrorResume(DataBufferLimitException.class, error ->
                        Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                                new ResultadoValidacionExcel(
                                        false,
                                        nombreArchivo,
                                        "El archivo supera el máximo de 5 MB.",
                                        Map.of(),
                                        List.of("El tamaño permitido es de 5 MB.")
                                )
                        )))
                .onErrorResume(Exception.class, error ->
                        Mono.just(ResponseEntity.badRequest().body(
                                new ResultadoValidacionExcel(
                                        false,
                                        nombreArchivo,
                                        "No fue posible procesar el archivo.",
                                        Map.of(),
                                        List.of(
                                                "Comprueba que sea un Excel .xlsx válido."
                                        )
                                )
                        )));
    }

    // -----------------------------------------------------------------
    // POST /api/casos/carga
    // Importa los registros usando la identidad del usuario autenticado.
    // -----------------------------------------------------------------
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ResultadoImportacionExcel>> importarExcel(
            @RequestPart("archivo") FilePart archivo,
            @AuthenticationPrincipal Jwt jwt
    ) {
        String nombreArchivo = archivo.filename();

        // Comprobamos el nombre antes de leer el contenido.
        if (!esArchivoExcelPermitido(nombreArchivo)) {
            return Mono.just(ResponseEntity.badRequest().body(
                    new ResultadoImportacionExcel(
                            false,
                            null,
                            "RECHAZADO",
                            0,
                            0,
                            0,
                            0,
                            "Solo se permiten archivos con extensión .xlsx.",
                            List.of("La extensión del archivo no está permitida.")
                    )
            ));
        }

        long tamanoDeclarado = archivo.headers().getContentLength();

        if (tamanoDeclarado > TAMANO_MAXIMO_BYTES) {
            return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                    new ResultadoImportacionExcel(
                            false,
                            null,
                            "RECHAZADO",
                            0,
                            0,
                            0,
                            0,
                            "El archivo supera el máximo de 5 MB.",
                            List.of("Reduce el tamaño del archivo e inténtalo de nuevo.")
                    )
            ));
        }

        // Obtenemos el contenido y dejamos que el servicio se encargue
        // de validar el libro, guardar el lote y procesar sus filas.
        return DataBufferUtils.join(archivo.content(), TAMANO_MAXIMO_BYTES)
                .map(this::convertirBytesYLiberar)
                .flatMap(contenido ->
                        Mono.fromCallable(() ->
                                        importService.importar(
                                                nombreArchivo,
                                                contenido,
                                                jwt == null
                                                        ? "SISTEMA"
                                                        : jwt.getSubject()
                                        ))
                                .subscribeOn(Schedulers.boundedElastic())
                )
                .<ResponseEntity<ResultadoImportacionExcel>>map(resultado -> {
                    if (resultado.exitoso()) {
                        return ResponseEntity.ok(resultado);
                    }

                    return ResponseEntity.badRequest().body(resultado);
                })
                .onErrorResume(DataBufferLimitException.class, error ->
                        Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(
                                new ResultadoImportacionExcel(
                                        false,
                                        null,
                                        "RECHAZADO",
                                        0,
                                        0,
                                        0,
                                        0,
                                        "El archivo supera el máximo de 5 MB.",
                                        List.of("El tamaño permitido es de 5 MB.")
                                )
                        )))
                .onErrorResume(Exception.class, error ->
                        Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                                new ResultadoImportacionExcel(
                                        false,
                                        null,
                                        "FALLIDO",
                                        0,
                                        0,
                                        0,
                                        0,
                                        "No fue posible completar la importación.",
                                        List.of(
                                                "La operación falló. Consulta los registros del servidor."
                                        )
                                )
                        )));
    }

    // Centralizamos la comprobación para que ambas rutas acepten los mismos archivos.
    private boolean esArchivoExcelPermitido(String nombreArchivo) {
        return nombreArchivo != null
                && nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    // Convertimos el búfer a bytes y liberamos la memoria utilizada por Netty.
    private byte[] convertirBytesYLiberar(DataBuffer buffer) {
        try {
            byte[] contenido = new byte[buffer.readableByteCount()];
            buffer.read(contenido);
            return contenido;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
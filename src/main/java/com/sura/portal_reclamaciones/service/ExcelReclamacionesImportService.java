package com.sura.portal_reclamaciones.service;

import com.sura.portal_reclamaciones.dto.importacion.ResultadoImportacionExcel;
import com.sura.portal_reclamaciones.dto.importacion.ResultadoValidacionExcel;
import com.sura.portal_reclamaciones.entity.AuditLog;
import com.sura.portal_reclamaciones.entity.CaseTracking;
import com.sura.portal_reclamaciones.entity.ClaimCase;
import com.sura.portal_reclamaciones.entity.Diagnosis;
import com.sura.portal_reclamaciones.entity.ImportBatch;
import com.sura.portal_reclamaciones.entity.ImportRowError;
import com.sura.portal_reclamaciones.entity.InsurancePlan;
import com.sura.portal_reclamaciones.entity.Policy;
import com.sura.portal_reclamaciones.entity.Provider;
import com.sura.portal_reclamaciones.entity.ServiceCatalog;
import com.sura.portal_reclamaciones.repository.AuditLogRepository;
import com.sura.portal_reclamaciones.repository.CaseTrackingRepository;
import com.sura.portal_reclamaciones.repository.ClaimCaseRepository;
import com.sura.portal_reclamaciones.repository.DiagnosisRepository;
import com.sura.portal_reclamaciones.repository.ImportBatchRepository;
import com.sura.portal_reclamaciones.repository.ImportRowErrorRepository;
import com.sura.portal_reclamaciones.repository.InsurancePlanRepository;
import com.sura.portal_reclamaciones.repository.PolicyRepository;
import com.sura.portal_reclamaciones.repository.ProviderRepository;
import com.sura.portal_reclamaciones.repository.ServiceCatalogRepository;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Este servicio realiza la carga en una transacción.
// Si ocurre un error técnico, los cambios de la operación se revierten.
@Service
public class ExcelReclamacionesImportService {

    private final ExcelReclamacionesService validador;
    private final ImportBatchRepository lotes;
    private final ImportRowErrorRepository erroresRepo;
    private final ClaimCaseRepository casos;
    private final CaseTrackingRepository seguimiento;
    private final InsurancePlanRepository planes;
    private final PolicyRepository polizas;
    private final ProviderRepository prestadores;
    private final DiagnosisRepository diagnosticos;
    private final ServiceCatalogRepository servicios;
    private final AuditLogRepository auditoria;

    public ExcelReclamacionesImportService(
            ExcelReclamacionesService validador,
            ImportBatchRepository lotes,
            ImportRowErrorRepository erroresRepo,
            ClaimCaseRepository casos,
            CaseTrackingRepository seguimiento,
            InsurancePlanRepository planes,
            PolicyRepository polizas,
            ProviderRepository prestadores,
            DiagnosisRepository diagnosticos,
            ServiceCatalogRepository servicios,
            AuditLogRepository auditoria
    ) {
        this.validador = validador;
        this.lotes = lotes;
        this.erroresRepo = erroresRepo;
        this.casos = casos;
        this.seguimiento = seguimiento;
        this.planes = planes;
        this.polizas = polizas;
        this.prestadores = prestadores;
        this.diagnosticos = diagnosticos;
        this.servicios = servicios;
        this.auditoria = auditoria;
    }

    // Primero validamos el libro. Solo procesamos los datos si la estructura
    // coincide con la estructura que espera el sistema.
    @Transactional
    public ResultadoImportacionExcel importar(
            String nombreArchivo,
            byte[] contenido,
            String usuario
    ) throws Exception {

        ResultadoValidacionExcel validacion =
                validador.validarEstructura(nombreArchivo, contenido);

        if (!validacion.valido()) {
            return new ResultadoImportacionExcel(
                    false,
                    null,
                    "RECHAZADO",
                    0,
                    0,
                    0,
                    0,
                    "El archivo no pasó la validación de estructura.",
                    validacion.errores()
            );
        }

        String hash = calcularSha256(contenido);
        List<ImportRowError> erroresFila = new ArrayList<>();

        try (Workbook libro = WorkbookFactory.create(
                new ByteArrayInputStream(contenido))) {

            DataFormatter formato =
                    new DataFormatter(Locale.forLanguageTag("es-CO"));

            Sheet hojaCasos = libro.getSheet("02_Casos");
            int total = contarFilas(hojaCasos, formato);

            // Registramos el lote antes de relacionar las reclamaciones con él.
            ImportBatch lote = new ImportBatch();
            lote.setOriginalFilename(nombreArchivo);
            lote.setFileSha256(hash);
            lote.setStatus("PROCESANDO");
            lote.setTotalRows(total);
            lote.setRowsRead(total);
            lote.setRowsValid(0);
            lote.setRowsWithErrors(0);
            lote.setSummary(new LinkedHashMap<>());

            lote = lotes.saveAndFlush(lote);
            Long loteId = lote.getId();

            // Cargamos primero los catálogos para resolver las referencias.
            importarPlanes(libro, formato);
            importarPolizas(libro, formato);
            importarPrestadores(libro, formato);
            importarDiagnosticos(libro, formato);
            importarServicios(libro, formato);

            Map<String, Integer> columnas =
                    obtenerColumnas(hojaCasos, formato);

            int importadas = 0;
            int casosConErrorMonetario = 0;

            for (int indice = 1;
                 indice <= hojaCasos.getLastRowNum();
                 indice++) {

                Row fila = hojaCasos.getRow(indice);

                if (fila == null || filaVacia(fila, formato)) {
                    continue;
                }

                String codigoCaso = valor(
                        fila, columnas, "ID_Caso", formato
                ).trim();

                // El código identifica de forma única cada reclamación.
                if (codigoCaso.isBlank()) {
                    agregarError(
                            erroresFila,
                            loteId,
                            "02_Casos",
                            indice + 1,
                            "ID_Caso",
                            "CASO_SIN_ID",
                            "La fila no tiene ID_Caso y no se puede importar."
                    );
                    continue;
                }

                // Evitamos duplicar casos en cargas posteriores.
                if (casos.existsByCaseCode(codigoCaso)) {
                    agregarError(
                            erroresFila,
                            loteId,
                            "02_Casos",
                            indice + 1,
                            "ID_Caso",
                            "CASO_DUPLICADO",
                            "El caso " + codigoCaso
                                    + " ya existe y no se volvió a insertar."
                    );
                    continue;
                }

                ClaimCase caso = crearCaso(
                        fila,
                        columnas,
                        formato,
                        codigoCaso,
                        loteId,
                        indice + 1
                );

                /*
                 * Si el valor no se pudo convertir, NO descartamos el caso.
                 * Guardamos el valor original y el registro del caso, pero
                 * añadimos una incidencia monetaria que requiere revisión.
                 *
                 * R01 y las demás reglas del motor deben determinar después
                 * si el importe permite o no una aprobación.
                 */
                if (caso.getClaimAmount() == null) {
                    casosConErrorMonetario++;

                    String montoOriginal = caso.getClaimAmountRaw();

                    boolean montoVacio = montoOriginal == null
                            || montoOriginal.isBlank();

                    agregarError(
                            erroresFila,
                            loteId,
                            "02_Casos",
                            indice + 1,
                            "Valor_Reclamado",
                            montoVacio
                                    ? "IMPORTE_VACIO"
                                    : "IMPORTE_NO_VALIDO",
                            montoVacio
                                    ? "El importe está vacío. El caso se "
                                            + "importó para revisión manual."
                                    : "El importe original '"
                                            + montoOriginal
                                            + "' no se pudo convertir a un "
                                            + "importe válido. El caso se "
                                            + "importó para revisión manual."
                    );
                }

                caso = casos.save(caso);

                // Dejamos el primer evento de trazabilidad del caso.
                CaseTracking evento = new CaseTracking();
                evento.setClaimCaseId(caso.getId());
                evento.setPreviousState(null);
                evento.setNewState("RADICADO");
                evento.setActorLabel("SISTEMA");
                evento.setCommentText(
                        "Caso recibido desde el archivo Excel de reclamaciones."
                );

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("loteId", loteId);
                metadata.put("archivo", nombreArchivo);
                metadata.put("filaExcel", indice + 1);
                evento.setEventMetadata(metadata);

                seguimiento.save(evento);
                importadas++;
            }

            if (!erroresFila.isEmpty()) {
                erroresRepo.saveAll(erroresFila);
            }

            /*
             * Este contador representa las incidencias registradas.
             * Cada error queda guardado con su hoja, fila y columna.
             */
            int filasConError = erroresFila.size();

            String estadoFinal = filasConError == 0
                    ? "COMPLETADO"
                    : "COMPLETADO_CON_ERRORES";

            Map<String, Object> resumen = new LinkedHashMap<>();
            resumen.put("archivo", nombreArchivo);
            resumen.put("sha256", hash);
            resumen.put("casosLeidos", total);
            resumen.put("casosImportados", importadas);
            resumen.put("filasConError", filasConError);
            resumen.put(
                    "casosConErrorMonetario",
                    casosConErrorMonetario
            );
            resumen.put("hojasValidadas", validacion.filasPorHoja());

            lote.setRowsValid(importadas);
            lote.setRowsWithErrors(filasConError);
            lote.setStatus(estadoFinal);
            lote.setCompletedAt(Instant.now());
            lote.setSummary(resumen);

            lotes.save(lote);

            // Registramos la operación sin incluir nombres ni documentos
            // de afiliados en la bitácora de auditoría.
            AuditLog registro = new AuditLog();
            registro.setActorUsername(
                    usuario == null || usuario.isBlank()
                            ? "SISTEMA"
                            : usuario
            );
            registro.setActionCode("IMPORTAR_EXCEL_RECLAMACIONES");
            registro.setResourceType("import_batch");
            registro.setResourceId(String.valueOf(loteId));
            registro.setResult(
                    filasConError == 0 ? "OK" : "ERROR"
            );
            registro.setMessage(
                    "Finalizó la importación del archivo Excel."
            );

            Map<String, Object> detalles = new LinkedHashMap<>();
            detalles.put("archivo", nombreArchivo);
            detalles.put("casosLeidos", total);
            detalles.put("casosImportados", importadas);
            detalles.put("filasConError", filasConError);
            detalles.put(
                    "casosConErrorMonetario",
                    casosConErrorMonetario
            );
            detalles.put("sha256", hash);
            registro.setDetails(detalles);

            auditoria.save(registro);

            List<String> mensajes = erroresFila.stream()
                    .map(ImportRowError::getErrorMessage)
                    .toList();

            String mensajeFinal;

            if (filasConError == 0) {
                mensajeFinal =
                        "La importación terminó sin incidencias.";
            } else {
                mensajeFinal =
                        "La importación terminó con incidencias. "
                        + "Se importaron "
                        + importadas
                        + " casos y se registraron "
                        + filasConError
                        + " incidencias; "
                        + casosConErrorMonetario
                        + " casos presentan errores monetarios y "
                        + "requieren revisión.";
            }

            return new ResultadoImportacionExcel(
                    true,
                    loteId,
                    estadoFinal,
                    total,
                    importadas,
                    filasConError,
                    casosConErrorMonetario,
                    mensajeFinal,
                    mensajes
            );
        }
    }

    // Recorremos las referencias y actualizamos por clave natural cuando ya
    // existen, evitando insertar copias de esos datos en cada carga.
    private void importarPlanes(Workbook libro, DataFormatter f) {
        Sheet hoja = libro.getSheet("07_Planes");
        Map<String, Integer> c = obtenerColumnas(hoja, f);

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (fila == null || filaVacia(fila, f)) continue;

            String codigo = normalizarCodigo(
                    valor(fila, c, "Plan", f)
            );
            if (codigo.isBlank()) continue;

            InsurancePlan plan = planes.findByPlanCodeIgnoreCase(codigo)
                    .orElseGet(InsurancePlan::new);

            plan.setPlanCode(codigo);
            plan.setPlanName(codigo);
            plan.setMaxAutoApprovalAmount(
                    decimal(
                            valor(
                                    fila,
                                    c,
                                    "Monto_Max_Aprobacion_Automatica",
                                    f
                            )
                    )
            );
            plan.setAnnualCap(
                    decimal(valor(fila, c, "Tope_Anual", f))
            );
            plan.setActive(true);

            planes.save(plan);
        }
    }

    private void importarPolizas(Workbook libro, DataFormatter f) {
        Sheet hoja = libro.getSheet("03_Polizas");
        Map<String, Integer> c = obtenerColumnas(hoja, f);

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (fila == null || filaVacia(fila, f)) continue;

            String numero = normalizarCodigo(
                    valor(fila, c, "Numero_Poliza", f)
            );
            if (numero.isBlank()) continue;

            String planCodigo = normalizarCodigo(
                    valor(fila, c, "Plan", f)
            );

            Policy poliza = polizas
                    .findByPolicyNumberIgnoreCase(numero)
                    .orElseGet(Policy::new);

            poliza.setPolicyNumber(numero);
            poliza.setStatus(
                    valor(fila, c, "Estado", f).trim()
            );
            poliza.setStartDate(
                    fecha(fila, c, "Fecha_Inicio", f)
            );
            poliza.setEndDate(
                    fecha(fila, c, "Fecha_Fin", f)
            );
            poliza.setAnnualConsumedAmount(
                    decimal(
                            valor(
                                    fila,
                                    c,
                                    "Valor_Consumido_Anio",
                                    f
                            )
                    )
            );
            poliza.setPlan(
                    planes.findByPlanCodeIgnoreCase(planCodigo)
                            .orElse(null)
            );

            polizas.save(poliza);
        }
    }

    private void importarPrestadores(Workbook libro, DataFormatter f) {
        Sheet hoja = libro.getSheet("04_Prestadores");
        Map<String, Integer> c = obtenerColumnas(hoja, f);

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (fila == null || filaVacia(fila, f)) continue;

            String nit = normalizarCodigo(
                    valor(fila, c, "NIT_Prestador", f)
            );
            if (nit.isBlank()) continue;

            Provider prestador = prestadores.findByNit(nit)
                    .orElseGet(Provider::new);

            prestador.setNit(nit);
            prestador.setName(
                    valor(fila, c, "Nombre_Prestador", f).trim()
            );
            prestador.setInNetwork(
                    esSi(valor(fila, c, "En_Red", f))
            );
            prestador.setFraudAlert(
                    esSi(valor(fila, c, "Alerta_Fraude", f))
            );
            prestador.setActive(true);

            prestadores.save(prestador);
        }
    }

    private void importarDiagnosticos(Workbook libro, DataFormatter f) {
        Sheet hoja = libro.getSheet("05_Diagnosticos");
        Map<String, Integer> c = obtenerColumnas(hoja, f);

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (fila == null || filaVacia(fila, f)) continue;

            String codigo = normalizarCodigo(
                    valor(fila, c, "Codigo_CIE10", f)
            );
            if (codigo.isBlank()) continue;

            Diagnosis diagnostico = diagnosticos
                    .findByDiagnosisCodeIgnoreCase(codigo)
                    .orElseGet(Diagnosis::new);

            diagnostico.setDiagnosisCode(codigo);
            diagnostico.setDescription(
                    valor(fila, c, "Descripcion", f).trim()
            );
            diagnostico.setRequiresMedicalReview(
                    esSi(
                            valor(
                                    fila,
                                    c,
                                    "Requiere_Revision_Medica",
                                    f
                            )
                    )
            );
            diagnostico.setExcluded(
                    esSi(valor(fila, c, "Excluido", f))
            );
            diagnostico.setActive(true);

            diagnosticos.save(diagnostico);
        }
    }

    private void importarServicios(Workbook libro, DataFormatter f) {
        Sheet hoja = libro.getSheet("06_Servicios");
        Map<String, Integer> c = obtenerColumnas(hoja, f);

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);
            if (fila == null || filaVacia(fila, f)) continue;

            String nombre = valor(
                    fila, c, "Tipo_Servicio", f
            ).trim();

            String codigo = normalizarCodigo(nombre);
            if (codigo.isBlank()) continue;

            boolean factura = esSi(
                    valor(fila, c, "Requiere_Factura", f)
            );
            boolean historia = esSi(
                    valor(fila, c, "Requiere_Historia_Clinica", f)
            );
            boolean orden = esSi(
                    valor(fila, c, "Requiere_Orden_Medica", f)
            );

            List<String> soportes = new ArrayList<>();

            if (factura) {
                soportes.add("Factura_Adjunta");
            }
            if (historia) {
                soportes.add("Historia_Clinica_Adjunta");
            }
            if (orden) {
                soportes.add("Orden_Medica_Adjunta");
            }

            ServiceCatalog servicio = servicios
                    .findByServiceCodeIgnoreCase(codigo)
                    .orElseGet(ServiceCatalog::new);

            servicio.setServiceCode(codigo);
            servicio.setServiceName(nombre);
            servicio.setSupportsRequired(!soportes.isEmpty());
            servicio.setRequiredSupports(soportes);
            servicio.setRequiredSupportsDescription(
                    soportes.isEmpty()
                            ? "No hay soportes obligatorios definidos."
                            : String.join(", ", soportes)
            );

            servicios.save(servicio);
        }
    }

    // Convierte una fila del Excel en la estructura que utiliza claim_case.
    // Conserva los datos originales para poder explicar después las incidencias.
    private ClaimCase crearCaso(
            Row fila,
            Map<String, Integer> c,
            DataFormatter f,
            String codigoCaso,
            Long loteId,
            int numeroFila
    ) {
        ClaimCase caso = new ClaimCase();

        caso.setCaseCode(codigoCaso);
        caso.setImportBatchId(loteId);
        caso.setSourceSheetName("02_Casos");
        caso.setSourceRowNumber(numeroFila);

        caso.setAffiliateName(
                valor(fila, c, "Nombre_Afiliado", f).trim()
        );
        caso.setDocumentNumber(
                valor(fila, c, "Numero_Documento", f).trim()
        );

        String polizaRaw = valor(fila, c, "Numero_Poliza", f);
        String nitRaw = valor(fila, c, "NIT_Prestador", f);
        String servicioRaw = valor(fila, c, "Tipo_Servicio", f);
        String diagnosticoRaw = valor(fila, c, "Codigo_Diagnostico", f);
        String montoRaw = valor(fila, c, "Valor_Reclamado", f);
        String fechaServicioRaw = valor(fila, c, "Fecha_Servicio", f);
        String fechaRadicacionRaw = valor(
                fila, c, "Fecha_Radicacion", f
        );

        String polizaNormalizada = normalizarCodigo(polizaRaw);
        String nitNormalizado = normalizarCodigo(nitRaw);
        String servicioNormalizado = normalizarCodigo(servicioRaw);
        String diagnosticoNormalizado =
                normalizarCodigo(diagnosticoRaw);

        caso.setPolicyNumberRaw(polizaRaw);
        caso.setNormalizedPolicyNumber(polizaNormalizada);
        caso.setProviderNitRaw(nitRaw);
        caso.setNormalizedProviderNit(nitNormalizado);
        caso.setServiceTypeRaw(servicioRaw);
        caso.setNormalizedServiceType(servicioNormalizado);
        caso.setDiagnosisCodeRaw(diagnosticoRaw);
        caso.setNormalizedDiagnosisCode(diagnosticoNormalizado);

        // El importe original siempre se conserva.
        caso.setClaimAmountRaw(montoRaw);

        BigDecimal monto = decimalSeguro(montoRaw);

        /*
         * Un importe no reconocido o negativo queda como null para que el
         * caso pueda importarse con su valor original y revisarse después.
         * Un valor de cero sí se convierte; su validez comercial debe decidirla
         * el motor de reglas, no el importador.
         */
        caso.setClaimAmount(
                monto != null && monto.signum() >= 0
                        ? monto
                        : null
        );

        caso.setServiceDateRaw(fechaServicioRaw);
        caso.setServiceDate(
                fecha(fila, c, "Fecha_Servicio", f)
        );

        caso.setFilingDateRaw(fechaRadicacionRaw);
        caso.setFilingDate(
                fecha(fila, c, "Fecha_Radicacion", f)
        );

        // Conservamos la columna Observaciones que viene del Excel.
        caso.setObservations(
                valor(fila, c, "Observaciones", f).trim()
        );

        caso.setCurrentState("RADICADO");

        // Las referencias quedan vacías si no existen en los catálogos.
        // El caso no se descarta por esta razón.
        caso.setResolvedPolicyId(
                polizas.findByPolicyNumberIgnoreCase(polizaNormalizada)
                        .map(Policy::getId)
                        .orElse(null)
        );

        caso.setResolvedProviderId(
                prestadores.findByNit(nitNormalizado)
                        .map(Provider::getId)
                        .orElse(null)
        );

        caso.setResolvedServiceTypeId(
                servicios.findByServiceCodeIgnoreCase(
                        servicioNormalizado
                )
                        .map(ServiceCatalog::getId)
                        .orElse(null)
        );

        caso.setResolvedDiagnosisId(
                diagnosticos.findByDiagnosisCodeIgnoreCase(
                        diagnosticoNormalizado
                )
                        .map(Diagnosis::getId)
                        .orElse(null)
        );

        Map<String, Object> soportes = new LinkedHashMap<>();

        soportes.put(
                "Factura_Adjunta",
                normalizarSiNo(
                        valor(fila, c, "Factura_Adjunta", f)
                )
        );

        soportes.put(
                "Historia_Clinica_Adjunta",
                normalizarSiNo(
                        valor(
                                fila,
                                c,
                                "Historia_Clinica_Adjunta",
                                f
                        )
                )
        );

        soportes.put(
                "Orden_Medica_Adjunta",
                normalizarSiNo(
                        valor(fila, c, "Orden_Medica_Adjunta", f)
                )
        );

        caso.setSupportsPayload(soportes);

        // Guardamos la fila original como JSONB.
        Map<String, Object> originales = new LinkedHashMap<>();

        for (Map.Entry<String, Integer> columna : c.entrySet()) {
            originales.put(
                    columna.getKey(),
                    valor(fila, c, columna.getKey(), f)
            );
        }

        caso.setRawPayload(originales);

        return caso;
    }

    private void agregarError(
            List<ImportRowError> lista,
            Long loteId,
            String hoja,
            int fila,
            String columna,
            String codigo,
            String mensaje
    ) {
        ImportRowError error = new ImportRowError();

        error.setImportBatchId(loteId);
        error.setSheetName(hoja);
        error.setRowNumber(fila);
        error.setColumnName(columna);
        error.setErrorCode(codigo);
        error.setSeverity("ERROR");
        error.setErrorMessage(mensaje);

        lista.add(error);
    }

    // Relacionamos cada encabezado con su posición.
    private Map<String, Integer> obtenerColumnas(
            Sheet hoja,
            DataFormatter formato
    ) {
        Map<String, Integer> columnas = new LinkedHashMap<>();
        Row encabezados = hoja.getRow(0);

        for (int i = 0; i < encabezados.getLastCellNum(); i++) {
            Cell celda = encabezados.getCell(
                    i,
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
            );

            if (celda == null) continue;

            String nombre = formato.formatCellValue(celda).trim();

            if (!nombre.isBlank()) {
                columnas.put(nombre, i);
            }
        }

        return columnas;
    }

    private String valor(
            Row fila,
            Map<String, Integer> columnas,
            String nombre,
            DataFormatter formato
    ) {
        Integer indice = columnas.get(nombre);

        if (indice == null) return "";

        Cell celda = fila.getCell(
                indice,
                Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
        );

        return celda == null
                ? ""
                : formato.formatCellValue(celda).trim();
    }

    private boolean filaVacia(Row fila, DataFormatter formato) {
        for (int i = 0; i < fila.getLastCellNum(); i++) {
            Cell celda = fila.getCell(
                    i,
                    Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
            );

            if (celda != null
                    && !formato.formatCellValue(celda).isBlank()) {
                return false;
            }
        }

        return true;
    }

    private int contarFilas(Sheet hoja, DataFormatter formato) {
        int cantidad = 0;

        for (int i = 1; i <= hoja.getLastRowNum(); i++) {
            Row fila = hoja.getRow(i);

            if (fila != null && !filaVacia(fila, formato)) {
                cantidad++;
            }
        }

        return cantidad;
    }

    private String normalizarCodigo(String valor) {
        return valor == null
                ? ""
                : valor.trim().toUpperCase(Locale.ROOT);
    }

    private boolean esSi(String valor) {
        return "SI".equals(normalizarSiNo(valor));
    }

    private String normalizarSiNo(String valor) {
        if (valor == null) return "";

        String limpio = Normalizer.normalize(
                valor.trim(),
                Normalizer.Form.NFD
        )
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);

        if (limpio.equals("SI") || limpio.equals("S")) return "SI";
        if (limpio.equals("NO") || limpio.equals("N")) return "NO";

        return limpio;
    }

    // Las fechas pueden venir como número serial de Excel o como texto.
    private LocalDate fecha(
            Row fila,
            Map<String, Integer> columnas,
            String nombre,
            DataFormatter formato
    ) {
        Integer indice = columnas.get(nombre);

        if (indice == null) return null;

        Cell celda = fila.getCell(
                indice,
                Row.MissingCellPolicy.RETURN_BLANK_AS_NULL
        );

        if (celda == null) return null;

        try {
            if (celda.getCellType()
                    == org.apache.poi.ss.usermodel.CellType.NUMERIC
                    && DateUtil.isValidExcelDate(
                            celda.getNumericCellValue()
                    )
                    && celda.getNumericCellValue() > 20000
                    && celda.getNumericCellValue() < 80000) {

                return DateUtil.getLocalDateTime(
                        celda.getNumericCellValue()
                ).toLocalDate();
            }

            String texto = formato.formatCellValue(celda).trim();

            if (texto.isBlank()) return null;

            try {
                return LocalDate.parse(texto);
            } catch (Exception ignorado) {
                // También puede venir como día/mes/año.
            }

            return LocalDate.parse(
                    texto,
                    DateTimeFormatter.ofPattern("d/M/uuuu")
            );

        } catch (Exception ignorado) {
            // No inventamos fechas que no se puedan interpretar.
            return null;
        }
    }

    private BigDecimal decimal(String texto) {
        BigDecimal numero = decimalSeguro(texto);

        return numero == null || numero.signum() < 0
                ? BigDecimal.ZERO
                : numero;
    }

    /**
     * Convierte importes numéricos o escritos con palabras.
     *
     * Ejemplos:
     * 800000                 -> 800000.00
     * 800.000                -> 800000.00
     * $ 800.000 pesos        -> 800000.00
     * ochocientos mil        -> 800000.00
     * ochocientosmil         -> 800000.00
     * ochosientosmil         -> 800000.00
     * un millón ochocientos mil -> 1800000.00
     *
     * Devuelve null cuando no puede interpretar el valor.
     */
    private BigDecimal decimalSeguro(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String limpio = normalizarTextoImporte(texto);

        if (limpio.isBlank()) {
            return null;
        }

        boolean tieneDigitos = limpio.matches(".*\\d.*");

        boolean tieneEscala = limpio.matches(
                ".*\\b(mil|millon|millones)\\b.*"
        );

        /*
         * Para expresiones con escala, como "800 mil" o
         * "un millón ochocientos mil", procesamos conjuntamente
         * las palabras y los tokens numéricos enteros.
         */
        if (tieneEscala) {
            // No reinterpretar cifras con separadores dentro de una frase.
            if (tieneDigitos && limpio.matches(".*\\d[.,]\\d.*")) {
                return null;
            }

            BigDecimal expresion = decimalEnPalabras(limpio);

            return expresion == null
                    ? null
                    : expresion.setScale(2, RoundingMode.HALF_UP);
        }

        // Si hay dígitos, no permitimos ignorar palabras desconocidas.
        if (tieneDigitos) {
            if (!limpio.matches("[0-9\\s.,+\\-]+")) {
                return null;
            }

            String numero = limpio.replaceAll("\\s+", "");

            /*
             * Admitimos tanto la agrupación colombiana con punto como
             * importes con agrupación por comas.
             *
             * Ejemplos:
             * 800.000  -> 800000
             * 1.800.000 -> 1800000
             * 800,000  -> 800000
             * 1.234,56 -> 1234.56
             * 800,50   -> 800.50
             */
            if (numero.matches("-?\\d{1,3}(,\\d{3})+")) {
                numero = numero.replace(",", "");
            } else if (numero.contains(",")) {
                numero = numero.replace(".", "")
                        .replace(",", ".");
            } else if (numero.matches(
                    "-?\\d{1,3}(\\.\\d{3})+"
            )) {
                numero = numero.replace(".", "");
            }

            if (numero.isBlank()
                    || numero.equals("-")
                    || numero.equals("+")
                    || numero.equals(".")
                    || numero.equals("-.")) {
                return null;
            }

            try {
                return new BigDecimal(numero).setScale(
                        2,
                        RoundingMode.HALF_UP
                );
            } catch (NumberFormatException ignorado) {
                return null;
            }
        }

        // Si no hay dígitos, intentamos interpretar las palabras en español.
        BigDecimal expresion = decimalEnPalabras(limpio);

        return expresion == null
                ? null
                : expresion.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Normaliza una expresión monetaria sin alterar el valor original que
     * se conserva en ClaimCase.claimAmountRaw.
     */
    private String normalizarTextoImporte(String texto) {
        String limpio = Normalizer.normalize(
                texto.trim().toLowerCase(Locale.ROOT),
                Normalizer.Form.NFD
        )
                .replaceAll("\\p{M}", "");

        // Toleramos esta variante frecuente de digitación.
        limpio = limpio.replace("ochosientos", "ochocientos");

        // Aceptamos las escalas pegadas a la palabra anterior:
        // "ochocientosmil" pasa a "ochocientos mil".
        limpio = limpio.replaceAll(
                "(?<=\\p{L})(millones|millon|mil)\\b",
                " $1"
        );

        // Retiramos expresiones monetarias que no cambian el valor.
        limpio = limpio.replaceAll(
                "\\b(pesos?|cop|colombianos?|colombianas?|moneda)\\b",
                " "
        );

        // Los símbolos monetarios no forman parte del número.
        limpio = limpio.replaceAll("[$€£]", " ");

        // Normalizamos puntuación no numérica al final de la expresión.
        limpio = limpio.replaceAll("[;:]+", " ");
        limpio = limpio.replaceAll(
                "(?<=[\\p{L}])[,.](?=\\s|$)",
                " "
        );

        return limpio.replaceAll("\\s+", " ").trim();
    }

    /**
     * Convierte expresiones como:
     * "ochocientos mil"
     * "mil ochocientos"
     * "un millón ochocientos mil"
     * "800 mil"
     *
     * Solo admite expresiones compuestas por números españoles conocidos
     * y las escalas mil/millón. Si aparece una palabra desconocida, devuelve
     * null en vez de aceptar una conversión parcial.
     */
    private BigDecimal decimalEnPalabras(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        String limpio = texto
                .replaceAll("[,;:]", " ")
                .replaceAll("[$€£]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (limpio.isBlank()) {
            return null;
        }

        String[] palabras = limpio.split("\\s+");

        long total = 0;
        long grupo = 0;

        try {
            for (String palabraOriginal : palabras) {
                String palabra = palabraOriginal.trim();

                if (palabra.isBlank()) {
                    continue;
                }

                // La conjunción no afecta la suma: "treinta y cinco".
                if (palabra.equals("y")) {
                    continue;
                }

                if (palabra.equals("mil")) {
                    long base = grupo == 0 ? 1 : grupo;
                    total = Math.addExact(
                            total,
                            Math.multiplyExact(base, 1_000L)
                    );
                    grupo = 0;
                    continue;
                }

                if (palabra.equals("millon")
                        || palabra.equals("millones")) {
                    long base = grupo == 0 ? 1 : grupo;
                    total = Math.addExact(
                            total,
                            Math.multiplyExact(base, 1_000_000L)
                    );
                    grupo = 0;
                    continue;
                }

                Long valorPalabra = valorNumeroEnPalabras(palabra);

                // No ignoramos palabras que el parser no conoce.
                if (valorPalabra == null) {
                    return null;
                }

                grupo = Math.addExact(grupo, valorPalabra);
            }

            return BigDecimal.valueOf(
                    Math.addExact(total, grupo)
            );

        } catch (ArithmeticException ignorado) {
            // Evitamos aceptar cantidades que desborden el tipo numérico.
            return null;
        }
    }

    /**
     * Devuelve el valor de una palabra numérica española de cero a novecientos,
     * o de un token entero como "800". Devuelve null si no es reconocido.
     */
    private Long valorNumeroEnPalabras(String palabra) {
        // Permite valores numéricos enteros combinados con escalas:
        // "800 mil" y "1 millón".
        if (palabra.matches("\\d+")) {
            try {
                return Long.parseLong(palabra);
            } catch (NumberFormatException ignorado) {
                return null;
            }
        }

        return switch (palabra) {
            case "cero" -> 0L;

            case "un", "uno", "una" -> 1L;
            case "dos" -> 2L;
            case "tres" -> 3L;
            case "cuatro" -> 4L;
            case "cinco" -> 5L;
            case "seis" -> 6L;
            case "siete" -> 7L;
            case "ocho" -> 8L;
            case "nueve" -> 9L;
            case "diez" -> 10L;
            case "once" -> 11L;
            case "doce" -> 12L;
            case "trece" -> 13L;
            case "catorce" -> 14L;
            case "quince" -> 15L;
            case "dieciseis" -> 16L;
            case "diecisiete" -> 17L;
            case "dieciocho" -> 18L;
            case "diecinueve" -> 19L;
            case "veinte" -> 20L;
            case "veintiun", "veintiuno", "veintiuna" -> 21L;
            case "veintidos" -> 22L;
            case "veintitres" -> 23L;
            case "veinticuatro" -> 24L;
            case "veinticinco" -> 25L;
            case "veintiseis" -> 26L;
            case "veintisiete" -> 27L;
            case "veintiocho" -> 28L;
            case "veintinueve" -> 29L;

            case "treinta" -> 30L;
            case "cuarenta" -> 40L;
            case "cincuenta" -> 50L;
            case "sesenta" -> 60L;
            case "setenta" -> 70L;
            case "ochenta" -> 80L;
            case "noventa" -> 90L;

            case "cien", "ciento" -> 100L;
            case "doscientos" -> 200L;
            case "trescientos" -> 300L;
            case "cuatrocientos" -> 400L;
            case "quinientos" -> 500L;
            case "seiscientos" -> 600L;
            case "setecientos" -> 700L;
            case "ochocientos" -> 800L;
            case "novecientos" -> 900L;

            default -> null;
        };
    }

    // El hash identifica el contenido del archivo sin almacenar otra copia.
    private String calcularSha256(byte[] contenido) throws Exception {
        byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(contenido);

        StringBuilder resultado = new StringBuilder();

        for (byte parte : hash) {
            resultado.append(String.format("%02x", parte));
        }

        return resultado.toString();
    }
}
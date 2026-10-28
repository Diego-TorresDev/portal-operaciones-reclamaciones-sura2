
package com.sura.portal_reclamaciones.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sura.portal_reclamaciones.entity.CaseEvaluation;
import com.sura.portal_reclamaciones.entity.ClaimCase;
import com.sura.portal_reclamaciones.repository.CaseEvaluationRepository;
import com.sura.portal_reclamaciones.repository.ClaimCaseRepository;

@Service
public class ReportesService {

    private static final String ESTADO_APROBADO_AUTOMATICO =
            "APROBADO_AUTOMATICO";

    private static final String ESTADO_APROBADO_ANALISTA =
            "APROBADO_ANALISTA";

    private static final String PRIORIDAD_ALTA = "ALTA";
    private static final String PRIORIDAD_MEDIA = "MEDIA";
    private static final String PRIORIDAD_BAJA = "BAJA";
    private static final String SIN_PRIORIDAD = "SIN_PRIORIDAD";
    private static final String SIN_SEMAFORO = "SIN_SEMAFORO";

    private static final String[] COLUMNAS_CSV = {
            "ID_Caso",
            "Decision",
            "Reglas_Incumplidas",
            "Motivo",
            "Prioridad",
            "Estado_Actual",
            "Fecha_Limite_ANS",
            "Semaforo_ANS"
    };

    private final ClaimCaseRepository casos;
    private final CaseEvaluationRepository evaluaciones;

    public ReportesService(
            ClaimCaseRepository casos,
            CaseEvaluationRepository evaluaciones) {

        this.casos = casos;
        this.evaluaciones = evaluaciones;
    }

    /**
     * Calcula los indicadores generales y los resúmenes de las dos
     * modalidades de aprobación actualmente pendientes de pago.
     *
     * Las cifras de las tarjetas se basan en el estado actual del caso:
     * APROBADO_AUTOMATICO o APROBADO_ANALISTA.
     *
     * No se incluyen en esas tarjetas casos que ya pasaron a EN_LOTE_PAGO,
     * PAGADO o CERRADO.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerResumen() {

        List<ClaimCase> todos = casos.findAll();

        Map<Long, CaseEvaluation> evaluacionesActuales =
                cargarEvaluacionesActuales();

        long total = todos.size();
        long automaticos = 0;
        long manuales = 0;
        long casosPendientesPago = 0;

        BigDecimal valorAPagar = BigDecimal.ZERO;

        Map<String, Long> porPrioridad = crearConteoPrioridades();
        Map<String, Long> porEstado = new TreeMap<>();
        Map<String, Long> porSemaforo = crearConteoSemaforos();

        for (ClaimCase caso : todos) {

            CaseEvaluation evaluacion =
                    evaluacionesActuales.get(caso.getId());

            String decision = normalizarCategoria(
                    obtenerDecision(caso, evaluacion)
            );

            String prioridad = normalizarCategoria(
                    obtenerPrioridad(caso, evaluacion)
            );

            String estado = normalizarCategoria(
                    caso.getCurrentState()
            );

            String semaforo = normalizarCategoria(
                    caso.getAnsSemaphore()
            );

            /*
             * Estas métricas describen la decisión vigente del motor.
             * No equivalen al número de aprobaciones por estado actual.
             */
            if ("AUTOMATICO".equals(decision)) {
                automaticos++;
            } else if ("MANUAL".equals(decision)) {
                manuales++;
            }

            /*
             * Los casos aprobados que todavía no han pasado a un lote
             * forman parte del valor pendiente de pago.
             */
            if (esEstadoAprobado(estado)) {

                casosPendientesPago++;

                if (caso.getClaimAmount() != null) {
                    valorAPagar = valorAPagar.add(
                            caso.getClaimAmount()
                    );
                }
            }

            if (prioridad == null
                    || prioridad.isBlank()) {
                prioridad = SIN_PRIORIDAD;
            }

            incrementar(porPrioridad, prioridad);

            incrementar(
                    porEstado,
                    estado == null ? "SIN_ESTADO" : estado
            );

            incrementar(
                    porSemaforo,
                    semaforo == null ? SIN_SEMAFORO : semaforo
            );
        }

        BigDecimal porcentajeAutomatizacion = total == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(automaticos)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(
                                BigDecimal.valueOf(total),
                                2,
                                RoundingMode.HALF_UP
                        );

        /*
         * Resumen económico y operativo de las dos listas que aparecen
         * en el tablero principal.
         *
         * Se usan claves distintas de "automaticos" y "manuales",
         * porque esas claves ya representan las decisiones del motor.
         */
        Map<String, Object> resumenAprobadosAutomaticos =
                resumirAprobaciones(
                        todos,
                        ESTADO_APROBADO_AUTOMATICO,
                        evaluacionesActuales
                );

        Map<String, Object> resumenAprobadosManuales =
                resumirAprobaciones(
                        todos,
                        ESTADO_APROBADO_ANALISTA,
                        evaluacionesActuales
                );

        Map<String, Object> respuesta = new LinkedHashMap<>();

        // Indicadores generales existentes.
        respuesta.put("totalCasos", total);
        respuesta.put("automaticos", automaticos);
        respuesta.put("manuales", manuales);
        respuesta.put(
                "porcentajeAutomatizacion",
                porcentajeAutomatizacion
        );
        respuesta.put("casosPendientesPago", casosPendientesPago);
        respuesta.put("valorAPagar", valorAPagar);
        respuesta.put("casosPorPrioridad", porPrioridad);
        respuesta.put("casosPorEstado", porEstado);
        respuesta.put("casosPorSemaforo", porSemaforo);

        // Nuevos resúmenes para las tarjetas del tablero.
        respuesta.put(
                "resumenAprobadosAutomaticos",
                resumenAprobadosAutomaticos
        );

        respuesta.put(
                "resumenAprobadosManuales",
                resumenAprobadosManuales
        );

        Map<String, Object> bandejaPago = new LinkedHashMap<>();
        bandejaPago.put("cantidad", casosPendientesPago);
        bandejaPago.put("valorTotal", valorAPagar);

        respuesta.put("bandejaPago", bandejaPago);

        long casosEnBandejaManual = todos.stream()
                .filter(caso -> {
                    String estado = normalizarCategoria(
                            caso.getCurrentState()
                    );

                    return "EN_BANDEJA".equals(estado)
                            || "EN_REVISION".equals(estado);
                })
                .count();

        respuesta.put(
                "casosEnBandejaManual",
                casosEnBandejaManual
        );

        return respuesta;
    }

    /**
     * Calcula el resumen de una modalidad de aprobación.
     *
     * Los importes se suman sobre todos los casos que tienen el estado
     * objetivo, no solo sobre los registros mostrados en una página.
     *
     * valorPromedio se calcula únicamente entre los casos que tienen
     * un importe informado. Los casos sin importe se cuentan por separado.
     */
    private Map<String, Object> resumirAprobaciones(
            List<ClaimCase> todos,
            String estadoObjetivo,
            Map<Long, CaseEvaluation> evaluacionesActuales) {

        long cantidad = 0;
        long casosConValor = 0;
        long casosSinValor = 0;

        BigDecimal valorTotal = BigDecimal.ZERO;

        Map<String, Long> porPrioridad =
                crearConteoPrioridades();

        Map<String, Long> porSemaforo =
                crearConteoSemaforos();

        for (ClaimCase caso : todos) {

            String estadoActual = normalizarCategoria(
                    caso.getCurrentState()
            );

            if (!estadoObjetivo.equals(estadoActual)) {
                continue;
            }

            cantidad++;

            BigDecimal importe = caso.getClaimAmount();

            if (importe != null) {
                valorTotal = valorTotal.add(importe);
                casosConValor++;
            } else {
                casosSinValor++;
            }

            CaseEvaluation evaluacion =
                    evaluacionesActuales.get(caso.getId());

            String prioridad = normalizarCategoria(
                    obtenerPrioridad(caso, evaluacion)
            );

            if (prioridad == null
                    || prioridad.isBlank()) {
                prioridad = SIN_PRIORIDAD;
            }

            incrementar(porPrioridad, prioridad);

            String semaforo = normalizarCategoria(
                    caso.getAnsSemaphore()
            );

            incrementar(
                    porSemaforo,
                    semaforo == null ? SIN_SEMAFORO : semaforo
            );
        }

        BigDecimal valorPromedio = casosConValor == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : valorTotal.divide(
                        BigDecimal.valueOf(casosConValor),
                        2,
                        RoundingMode.HALF_UP
                );

        BigDecimal porcentajeDelTotal = todos.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(cantidad)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(
                                BigDecimal.valueOf(todos.size()),
                                2,
                                RoundingMode.HALF_UP
                        );

        Map<String, Object> resumen = new LinkedHashMap<>();

        resumen.put("estado", estadoObjetivo);
        resumen.put("cantidad", cantidad);
        resumen.put("porcentajeDelTotal", porcentajeDelTotal);
        resumen.put("casosConValor", casosConValor);
        resumen.put("casosSinValor", casosSinValor);
        resumen.put("valorTotal", valorTotal);
        resumen.put("valorPromedio", valorPromedio);
        resumen.put("casosPorPrioridad", porPrioridad);
        resumen.put("casosPorSemaforo", porSemaforo);

        return resumen;
    }

    /**
     * Exporta todas las reclamaciones a CSV UTF-8 compatible con Excel.
     * No incluye nombres, documentos ni otros datos personales del afiliado.
     */
    @Transactional(readOnly = true)
    public byte[] exportarResultadosCsv() {

        List<ClaimCase> todos = new ArrayList<>(casos.findAll());

        todos.sort(
                Comparator.comparing(
                        ClaimCase::getCaseCode,
                        Comparator.nullsLast(
                                String.CASE_INSENSITIVE_ORDER
                        )
                )
        );

        Map<Long, CaseEvaluation> evaluacionesActuales =
                cargarEvaluacionesActuales();

        StringBuilder csv = new StringBuilder();

        // El BOM ayuda a Excel a reconocer correctamente los caracteres UTF-8.
        csv.append('\uFEFF');
        agregarFila(csv, COLUMNAS_CSV);

        for (ClaimCase caso : todos) {

            CaseEvaluation evaluacion =
                    evaluacionesActuales.get(caso.getId());

            String decision = obtenerDecision(caso, evaluacion);
            String prioridad = obtenerPrioridad(caso, evaluacion);

            List<String> reglasFallidas = evaluacion == null
                    || evaluacion.getFailedRuleCodes() == null
                    ? List.of()
                    : evaluacion.getFailedRuleCodes();

            String motivo = evaluacion == null
                    ? "Sin evaluación registrada."
                    : evaluacion.getBusinessReason();

            String fechaLimite = caso.getAnsDeadlineDate() == null
                    ? ""
                    : caso.getAnsDeadlineDate().toString();

            String[] fila = {
                    caso.getCaseCode(),
                    decision,
                    String.join("; ", reglasFallidas),
                    motivo,
                    prioridad,
                    caso.getCurrentState(),
                    fechaLimite,
                    caso.getAnsSemaphore()
            };

            agregarFila(csv, fila);
        }

        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Obtiene la evaluación vigente más reciente de cada caso.
     */
    private Map<Long, CaseEvaluation> cargarEvaluacionesActuales() {

        return evaluaciones.findAll()
                .stream()
                .filter(CaseEvaluation::isCurrent)
                .collect(Collectors.toMap(
                        CaseEvaluation::getClaimCaseId,
                        evaluacion -> evaluacion,
                        (primera, segunda) ->
                                segunda.getEvaluationNumber()
                                        >= primera.getEvaluationNumber()
                                        ? segunda
                                        : primera
                ));
    }

    private String obtenerDecision(
            ClaimCase caso,
            CaseEvaluation evaluacion) {

        if (evaluacion != null
                && evaluacion.getDecision() != null) {
            return evaluacion.getDecision();
        }

        return caso.getCurrentDecision() == null
                ? "SIN_DECISION"
                : caso.getCurrentDecision();
    }

    private String obtenerPrioridad(
            ClaimCase caso,
            CaseEvaluation evaluacion) {

        if (evaluacion != null
                && evaluacion.getPriority() != null) {
            return evaluacion.getPriority();
        }

        return caso.getCurrentPriority();
    }

    private boolean esEstadoAprobado(String estado) {

        return ESTADO_APROBADO_AUTOMATICO.equals(estado)
                || ESTADO_APROBADO_ANALISTA.equals(estado);
    }

    private Map<String, Long> crearConteoPrioridades() {

        Map<String, Long> conteo = new LinkedHashMap<>();

        conteo.put(PRIORIDAD_ALTA, 0L);
        conteo.put(PRIORIDAD_MEDIA, 0L);
        conteo.put(PRIORIDAD_BAJA, 0L);
        conteo.put(SIN_PRIORIDAD, 0L);

        return conteo;
    }

    private Map<String, Long> crearConteoSemaforos() {

        Map<String, Long> conteo = new LinkedHashMap<>();

        conteo.put("VERDE", 0L);
        conteo.put("AMARILLO", 0L);
        conteo.put("ROJO", 0L);
        conteo.put("CUMPLIDO", 0L);
        conteo.put(SIN_SEMAFORO, 0L);

        return conteo;
    }

    private String normalizarCategoria(String valor) {

        if (valor == null || valor.isBlank()) {
            return null;
        }

        return valor.trim().toUpperCase(Locale.ROOT);
    }

    private void incrementar(
            Map<String, Long> conteo,
            String categoria) {

        conteo.merge(categoria, 1L, Long::sum);
    }

    /**
     * Neutraliza celdas que podrían interpretarse como fórmulas.
     * Se revisa el primer carácter significativo para cubrir espacios previos.
     */
    private String neutralizarFormula(String valor) {

        if (valor == null || valor.isEmpty()) {
            return "";
        }

        int indice = 0;

        while (indice < valor.length()
                && Character.isWhitespace(valor.charAt(indice))) {
            indice++;
        }

        if (indice < valor.length()) {
            char primero = valor.charAt(indice);

            if (primero == '='
                    || primero == '+'
                    || primero == '-'
                    || primero == '@') {

                return "'" + valor;
            }
        }

        return valor;
    }

    /**
     * Encierra cada celda entre comillas y escapa las comillas internas.
     */
    private void agregarFila(StringBuilder csv, String[] valores) {

        for (int i = 0; i < valores.length; i++) {

            if (i > 0) {
                csv.append(',');
            }

            String seguro = neutralizarFormula(valores[i]);
            String escapado = seguro.replace("\"", "\"\"");

            csv.append('"')
                    .append(escapado)
                    .append('"');
        }

        // Excel reconoce el salto de línea estándar de Windows.
        csv.append("\r\n");
    }
}

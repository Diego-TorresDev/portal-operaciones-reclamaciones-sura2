package com.sura.portal_reclamaciones.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sura.portal_reclamaciones.entity.CaseEvaluation;
import com.sura.portal_reclamaciones.entity.CaseEvaluationRule;
import com.sura.portal_reclamaciones.entity.CaseTracking;
import com.sura.portal_reclamaciones.entity.ClaimCase;
import com.sura.portal_reclamaciones.entity.Usuario;
import com.sura.portal_reclamaciones.repository.CaseEvaluationRepository;
import com.sura.portal_reclamaciones.repository.CaseEvaluationRuleRepository;
import com.sura.portal_reclamaciones.repository.CaseTrackingRepository;
import com.sura.portal_reclamaciones.repository.ClaimCaseRepository;
import com.sura.portal_reclamaciones.repository.UsuarioRepository;

@Service
public class ConsultaCasosService {

    private final ClaimCaseRepository casos;
    private final CaseEvaluationRepository evaluaciones;
    private final CaseEvaluationRuleRepository reglas;
    private final CaseTrackingRepository seguimiento;
    private final UsuarioRepository usuarios;

    public ConsultaCasosService(
            ClaimCaseRepository casos,
            CaseEvaluationRepository evaluaciones,
            CaseEvaluationRuleRepository reglas,
            CaseTrackingRepository seguimiento,
            UsuarioRepository usuarios) {

        this.casos = casos;
        this.evaluaciones = evaluaciones;
        this.reglas = reglas;
        this.seguimiento = seguimiento;
        this.usuarios = usuarios;
    }

    /**
     * Lista casos con filtros y paginación.
     *
     * El documento se enmascara en el listado.
     * El documento completo se reserva para el endpoint de detalle.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> listarCasos(
            String estado,
            String decision,
            String prioridad,
            String regla,
            String semaforo,
            int pagina,
            int tamano) {

        if (pagina < 0 || tamano < 1 || tamano > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La página debe ser mayor o igual a cero y el tamaño "
                            + "debe estar entre 1 y 100."
            );
        }

        List<ClaimCase> todos = casos.findAll();

        // Evitamos consultar repetidamente el mismo usuario.
        Map<Long, String> nombresAnalistas = usuarios.findAll()
                .stream()
                .collect(Collectors.toMap(
                        Usuario::getId,
                        Usuario::getUsername,
                        (primero, segundo) -> primero
                ));

        List<FilaCaso> filtrados = new ArrayList<>();

        for (ClaimCase caso : todos) {
            CaseEvaluation evaluacion = obtenerEvaluacionActual(caso);

            String decisionActual = evaluacion != null
                    ? evaluacion.getDecision()
                    : caso.getCurrentDecision();

            String prioridadActual = evaluacion != null
                    ? evaluacion.getPriority()
                    : caso.getCurrentPriority();

            List<String> reglasFallidas = evaluacion != null
                    ? evaluacion.getFailedRuleCodes()
                    : List.of();

            if (!coincide(estado, caso.getCurrentState())) {
                continue;
            }

            if (!coincide(decision, decisionActual)) {
                continue;
            }

            if (!coincide(prioridad, prioridadActual)) {
                continue;
            }

            if (!coincide(semaforo, caso.getAnsSemaphore())) {
                continue;
            }

            if (regla != null && !regla.isBlank()
                    && reglasFallidas.stream().noneMatch(
                            codigo -> codigo.equalsIgnoreCase(regla.trim()))) {
                continue;
            }

            String nombreAnalista = caso.getAssignedAnalystId() == null
                    ? null
                    : nombresAnalistas.get(caso.getAssignedAnalystId());

            filtrados.add(new FilaCaso(
                    caso,
                    evaluacion,
                    decisionActual,
                    prioridadActual,
                    reglasFallidas,
                    nombreAnalista
            ));
        }

        // Primero prioridad alta, después media y baja.
        // A igualdad de prioridad, se ordena por fecha límite del ANS.
        filtrados.sort(
                Comparator
                        .comparingInt(
                                (FilaCaso fila) ->
                                        ordenPrioridad(fila.prioridad())
                        )
                        .thenComparing(
                                fila -> fila.caso().getAnsDeadlineDate(),
                                Comparator.nullsLast(Comparator.naturalOrder())
                        )
                        .thenComparing(
                                fila -> fila.caso().getCaseCode(),
                                Comparator.nullsLast(
                                        String.CASE_INSENSITIVE_ORDER
                                )
                        )
        );

        long total = filtrados.size();
        long desdeLong = (long) pagina * tamano;
        int desde = (int) Math.min(desdeLong, total);
        int hasta = (int) Math.min(desdeLong + tamano, total);

        List<Map<String, Object>> contenido = filtrados
                .subList(desde, hasta)
                .stream()
                .map(this::crearFilaListado)
                .toList();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("contenido", contenido);
        respuesta.put("pagina", pagina);
        respuesta.put("tamano", tamano);
        respuesta.put("totalElementos", total);
        respuesta.put(
                "totalPaginas",
                total == 0 ? 0 : (total + tamano - 1) / tamano
        );

        return respuesta;
    }

    /**
     * Devuelve el detalle del caso buscando por código o por ID.
     *
     * Añade:
     * - El importe original del Excel.
     * - Un indicador de error monetario.
     * - Una explicación de la incidencia monetaria.
     * - Las observaciones originales del Excel.
     * - Solo las reglas incumplidas en el detalle.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerDetalle(String identificador) {

        ClaimCase caso = buscarCaso(identificador);
        CaseEvaluation evaluacion = obtenerEvaluacionActual(caso);

        Map<String, Object> respuesta = new LinkedHashMap<>();

        respuesta.put("id", caso.getId());
        respuesta.put("idCaso", caso.getCaseCode());
        respuesta.put("estadoActual", caso.getCurrentState());

        respuesta.put(
                "decision",
                evaluacion != null
                        ? evaluacion.getDecision()
                        : caso.getCurrentDecision()
        );

        respuesta.put(
                "prioridad",
                evaluacion != null
                        ? evaluacion.getPriority()
                        : caso.getCurrentPriority()
        );

        respuesta.put("afiliado", caso.getAffiliateName());
        respuesta.put("documento", caso.getDocumentNumber());

        respuesta.put("poliza", caso.getNormalizedPolicyNumber());
        respuesta.put("nitPrestador", caso.getNormalizedProviderNit());
        respuesta.put("tipoServicio", caso.getNormalizedServiceType());
        respuesta.put("codigoDiagnostico", caso.getNormalizedDiagnosisCode());

        // Importe convertido y valor original que venía en el Excel.
        respuesta.put("valorReclamado", caso.getClaimAmount());
        respuesta.put("valorReclamadoOriginal", caso.getClaimAmountRaw());

        // El importe nulo indica que el servicio de importación no consiguió
        // convertirlo a un valor monetario utilizable.
        boolean errorMonetario = caso.getClaimAmount() == null;

        respuesta.put("errorMonetario", errorMonetario);
        respuesta.put(
                "detalleErrorMonetario",
                crearDetalleErrorMonetario(caso, errorMonetario)
        );

        respuesta.put("fechaServicio", caso.getServiceDate());
        respuesta.put("fechaRadicacion", caso.getFilingDate());
        respuesta.put("soportes", caso.getSupportsPayload());

        // La columna Observaciones del Excel ya se almacena en el caso.
        respuesta.put("observaciones", caso.getObservations());

        respuesta.put("fechaLimiteAns", caso.getAnsDeadlineDate());
        respuesta.put("semaforoAns", caso.getAnsSemaphore());
        respuesta.put("alertaAns", caso.getAnsAlert());

        respuesta.put("analistaAsignado", obtenerNombreAnalista(caso));

        if (evaluacion == null) {
            respuesta.put("numeroEvaluacion", null);
            respuesta.put("motivo", null);
            respuesta.put("reglasIncumplidas", List.of());
            respuesta.put("reglas", List.of());

        } else {
            respuesta.put(
                    "numeroEvaluacion",
                    evaluacion.getEvaluationNumber()
            );

            respuesta.put("motivo", evaluacion.getBusinessReason());

            // Códigos de las reglas que incumplió el caso.
            respuesta.put(
                    "reglasIncumplidas",
                    evaluacion.getFailedRuleCodes()
            );

            /*
             * Solo incluimos las reglas que no pasaron.
             *
             * No borramos ni modificamos las demás reglas de la evaluación
             * persistida; simplemente no las incluimos en la respuesta
             * de detalle para evitar una lista innecesariamente larga.
             */
            List<CaseEvaluationRule> todasLasReglasR17 = reglas.findByCaseEvaluationIdOrderByRuleCodeAsc(evaluacion.getId());
Map<String, Object> analisisR17 = todasLasReglasR17.stream()
        .filter(regla -> "R17".equals(regla.getRuleCode()))
        .findFirst()
        .map(this::crearAnalisisR17)
        .orElse(null);
List<Map<String, Object>> detalleReglas = todasLasReglasR17.stream()
        .filter(regla -> !regla.isPassed() && !"R17".equalsIgnoreCase(regla.getRuleCode()))
        .map(this::crearDetalleRegla)
        .toList();

            respuesta.put("analisisR17", analisisR17);
respuesta.put("reglas", detalleReglas);
        }

        return respuesta;
    }

    /**
     * Explica por qué el importe se marcó con error.
     *
     * No considera cero como un error de conversión: cero es un valor
     * numérico que debe ser evaluado por las reglas de negocio.
     */
    private String crearDetalleErrorMonetario(
            ClaimCase caso,
            boolean errorMonetario) {

        if (!errorMonetario) {
            return null;
        }

        String original = caso.getClaimAmountRaw();

        if (original == null || original.isBlank()) {
            return "El importe reclamado está vacío en el archivo de origen. "
                    + "El caso se conservó para revisión manual.";
        }

        return "El importe original «"
                + original
                + "» no pudo convertirse a un valor monetario válido "
                + "no negativo. El caso se conservó para revisión manual.";
    }

    /**
     * Devuelve el historial completo de estados en orden cronológico.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerTracking(String identificador) {

        ClaimCase caso = buscarCaso(identificador);

        List<Map<String, Object>> eventos =
                seguimiento.findByClaimCaseIdOrderByEventAtAscIdAsc(
                                caso.getId()
                        )
                        .stream()
                        .map(this::crearDetalleTracking)
                        .toList();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("idCaso", caso.getCaseCode());
        respuesta.put("estadoActual", caso.getCurrentState());
        respuesta.put("cantidadEventos", eventos.size());
        respuesta.put("historial", eventos);

        return respuesta;
    }

    /**
     * Recupera la evaluación vigente sin alterar el historial anterior.
     */
    private CaseEvaluation obtenerEvaluacionActual(ClaimCase caso) {

        if (caso.getCurrentEvaluationId() != null) {
            Optional<CaseEvaluation> porId =
                    evaluaciones.findById(caso.getCurrentEvaluationId());

            if (porId.isPresent() && porId.get().isCurrent()) {
                return porId.get();
            }
        }

        return evaluaciones
                .findByClaimCaseIdAndCurrentTrue(caso.getId())
                .orElse(null);
    }

    /**
     * Busca por código visible o por ID numérico de la base de datos.
     */
    private ClaimCase buscarCaso(String identificador) {

        if (identificador == null || identificador.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debes indicar el código o el ID del caso."
            );
        }

        Optional<ClaimCase> porCodigo =
                casos.findByCaseCode(identificador.trim());

        if (porCodigo.isPresent()) {
            return porCodigo.get();
        }

        try {
            long id = Long.parseLong(identificador.trim());

            return casos.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "No se encontró el caso solicitado."
                    ));

        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontró el caso solicitado."
            );
        }
    }

    /**
     * Construye una fila segura para el listado y enmascara el documento.
     */
    private Map<String, Object> crearFilaListado(FilaCaso fila) {

        ClaimCase caso = fila.caso();

        Map<String, Object> resultado = new LinkedHashMap<>();

        resultado.put("id", caso.getId());
        resultado.put("idCaso", caso.getCaseCode());
        resultado.put("afiliado", caso.getAffiliateName());

        resultado.put(
                "documentoEnmascarado",
                enmascararDocumento(caso.getDocumentNumber())
        );

        resultado.put("valor", caso.getClaimAmount());
        resultado.put("decision", fila.decision());
        resultado.put("reglasIncumplidas", fila.reglasFallidas());

        resultado.put(
                "motivo",
                fila.evaluacion() == null
                        ? null
                        : fila.evaluacion().getBusinessReason()
        );

        resultado.put("prioridad", fila.prioridad());
        resultado.put("estadoActual", caso.getCurrentState());
        resultado.put("fechaLimiteAns", caso.getAnsDeadlineDate());
        resultado.put("semaforoAns", caso.getAnsSemaphore());
        resultado.put("alertaAns", caso.getAnsAlert());
        resultado.put("analistaAsignado", fila.analista());

        // Indicador útil para identificar casos que necesitan revisión
        // monetaria sin mostrar toda la incidencia en el listado.
        resultado.put(
                "errorMonetario",
                caso.getClaimAmount() == null
        );

        return resultado;
    }

    /**
     * En el listado se muestran únicamente los últimos cuatro caracteres.
     */
    private String enmascararDocumento(String documento) {

        if (documento == null || documento.isBlank()) {
            return null;
        }

        String limpio = documento.trim();

        if (limpio.length() <= 4) {
            return "******";
        }

        return "******" + limpio.substring(limpio.length() - 4);
    }

    /**
     * Convierte el resultado de una regla en datos de API.
     * Incluye la evidencia JSONB almacenada durante la evaluación.
     */
    private Map<String, Object> crearDetalleRegla(
            CaseEvaluationRule regla) {

        Map<String, Object> resultado = new LinkedHashMap<>();

        resultado.put("codigo", regla.getRuleCode());
        resultado.put("cumple", regla.isPassed());
        resultado.put("explicacion", regla.getBusinessExplanation());

        resultado.put(
                "evidencia",
                regla.getEvidence() != null
                        ? regla.getEvidence()
                        : Map.of()
        );

        return resultado;
    }

    /**
     * Convierte cada evento de seguimiento en un objeto de respuesta seguro.
     */
    private Map<String, Object> crearDetalleTracking(
            CaseTracking evento) {

        Map<String, Object> resultado = new LinkedHashMap<>();

        resultado.put("estadoAnterior", evento.getPreviousState());
        resultado.put("estadoNuevo", evento.getNewState());
        resultado.put("usuarioId", evento.getActorUserId());
        resultado.put("usuario", evento.getActorLabel());
        resultado.put("fechaHora", evento.getEventAt());
        resultado.put("comentario", evento.getCommentText());

        return resultado;
    }

    private String obtenerNombreAnalista(ClaimCase caso) {

        if (caso.getAssignedAnalystId() == null) {
            return null;
        }

        return usuarios.findById(caso.getAssignedAnalystId())
                .map(Usuario::getUsername)
                .orElse(null);
    }

    private boolean coincide(String filtro, String valorActual) {

        return filtro == null
                || filtro.isBlank()
                || (valorActual != null
                    && valorActual.equalsIgnoreCase(filtro.trim()));
    }

    private int ordenPrioridad(String prioridad) {

        if (prioridad == null || prioridad.isBlank()) {
            return 3;
        }

        return switch (prioridad.trim().toUpperCase(Locale.ROOT)) {
            case "ALTA" -> 0;
            case "MEDIA" -> 1;
            case "BAJA" -> 2;
            default -> 3;
        };
    }

    private record FilaCaso(
            ClaimCase caso,
            CaseEvaluation evaluacion,
            String decision,
            String prioridad,
            List<String> reglasFallidas,
            String analista) {
    }

    /**
     * Expone el anÃ¡lisis R17 de la evaluaciÃ³n mÃ¡s reciente, incluso cuando R17
     * se cumple. La evidencia se conserva tal como fue almacenada por el motor.
     */
    private Map<String, Object> crearAnalisisR17(CaseEvaluationRule regla) {
        Map<String, Object> resultado = new LinkedHashMap<>();
        Map<String, Object> evidencia = regla.getEvidence();
        if (evidencia != null) {
            resultado.putAll(evidencia);
        }
        resultado.put("codigoRegla", regla.getRuleCode());
        resultado.put("cumpleR17", regla.isPassed());
        resultado.put("explicacionRegla", regla.getBusinessExplanation());
        return resultado;
    }
}

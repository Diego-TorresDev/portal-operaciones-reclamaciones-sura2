package com.sura.portal_reclamaciones.service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sura.portal_reclamaciones.entity.AuditLog;
import com.sura.portal_reclamaciones.entity.CaseReviewDecision;
import com.sura.portal_reclamaciones.entity.CaseTracking;
import com.sura.portal_reclamaciones.entity.ClaimCase;
import com.sura.portal_reclamaciones.entity.ParameterConfig;
import com.sura.portal_reclamaciones.entity.UserRole;
import com.sura.portal_reclamaciones.entity.Usuario;
import com.sura.portal_reclamaciones.repository.AuditLogRepository;
import com.sura.portal_reclamaciones.repository.CaseReviewDecisionRepository;
import com.sura.portal_reclamaciones.repository.CaseStateTransitionRepository;
import com.sura.portal_reclamaciones.repository.CaseTrackingRepository;
import com.sura.portal_reclamaciones.repository.ClaimCaseRepository;
import com.sura.portal_reclamaciones.repository.ParameterConfigRepository;
import com.sura.portal_reclamaciones.repository.UsuarioRepository;

@Service
public class RevisionCasosService {

    private final ClaimCaseRepository casos;
    private final UsuarioRepository usuarios;
    private final CaseStateTransitionRepository transiciones;
    private final CaseTrackingRepository seguimiento;
    private final AuditLogRepository auditoria;
    private final CaseReviewDecisionRepository decisiones;
    private final ParameterConfigRepository parametros;

    public RevisionCasosService(
            ClaimCaseRepository casos,
            UsuarioRepository usuarios,
            CaseStateTransitionRepository transiciones,
            CaseTrackingRepository seguimiento,
            AuditLogRepository auditoria,
            CaseReviewDecisionRepository decisiones,
            ParameterConfigRepository parametros) {

        this.casos = casos;
        this.usuarios = usuarios;
        this.transiciones = transiciones;
        this.seguimiento = seguimiento;
        this.auditoria = auditoria;
        this.decisiones = decisiones;
        this.parametros = parametros;
    }

    /**
     * Asigna un caso de la bandeja manual al analista autenticado.
     * La operación y sus registros de seguimiento se guardan juntos.
     */
    @Transactional
    public Map<String, Object> tomarCaso(
            String codigoCaso,
            String username) {

        // El usuario se obtiene del JWT, no de un ID enviado por el cliente.
        Usuario analista = obtenerAnalistaActivo(username);

        // Buscamos la reclamación por su código.
        ClaimCase caso = casos.findByCaseCode(codigoCaso)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el caso solicitado."
                ));

        // Solo se pueden tomar casos que esperan en la bandeja manual.
        if (!"EN_BANDEJA".equals(caso.getCurrentState())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El caso no está disponible en la bandeja manual."
            );
        }

        // No permitimos reasignar casos que ya tengan un analista.
        if (caso.getAssignedAnalystId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El caso ya tiene un analista asignado."
            );
        }

        // La transición debe estar habilitada en el catálogo de PostgreSQL.
        validarTransicion("EN_BANDEJA", "EN_REVISION");

        Instant ahora = Instant.now();
        String estadoAnterior = caso.getCurrentState();

        // Asignamos el caso y cambiamos su estado.
        caso.setAssignedAnalystId(analista.getId());
        caso.setAssignedAt(ahora);
        caso.setCurrentState("EN_REVISION");

        casos.save(caso);

        // Añadimos un evento al historial; no sobrescribimos los anteriores.
        registrarTransicionRevision(
                caso,
                estadoAnterior,
                "EN_REVISION",
                analista,
                "Caso tomado por el analista para revisión.",
                "TOMAR_CASO"
        );

        // Registramos la operación en la bitácora general.
        registrarAuditoria(
                analista,
                "CASE_TAKE",
                caso.getCaseCode(),
                "Caso asignado al analista para revisión.",
                Map.of(
                        "estadoAnterior", estadoAnterior,
                        "estadoNuevo", "EN_REVISION"
                )
        );

        // Devolvemos un resumen, no la entidad completa con datos personales.
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("codigoCaso", caso.getCaseCode());
        respuesta.put("estadoAnterior", estadoAnterior);
        respuesta.put("estadoActual", caso.getCurrentState());
        respuesta.put("analistaAsignado", analista.getUsername());
        respuesta.put("fechaAsignacion", ahora.toString());
        respuesta.put("mensaje", "Caso asignado correctamente.");

        return respuesta;
    }

    /**
     * Registra la aprobación u objeción del analista asignado.
     *
     * En caso de objeción, el flujo es:
     * EN_REVISION -> OBJETADO -> CERRADO.
     *
     * La decisión humana se atribuye al analista. El cierre automático
     * se atribuye a SISTEMA y genera su propio evento de auditoría.
     *
     * Toda la operación queda dentro de una sola transacción.
     */
    @Transactional
    public Map<String, Object> decidirCaso(
            String codigoCaso,
            String username,
            String accionIngresada,
            String observacionIngresada) {

        // Identificamos al responsable a partir de la sesión autenticada.
        Usuario analista = obtenerAnalistaActivo(username);

        String accion = accionIngresada == null
                ? ""
                : accionIngresada.trim().toUpperCase(
                        java.util.Locale.ROOT
                );

        if (!"APROBAR".equals(accion)
                && !"OBJETAR".equals(accion)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La acción debe ser APROBAR u OBJETAR."
            );
        }

        String observacion = observacionIngresada == null
                ? ""
                : observacionIngresada.trim();

        int minimo = obtenerMinimoObservacion();

        // La observación es obligatoria y debe cumplir el mínimo configurado.
        if (observacion.length() < minimo) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "La observación debe tener al menos "
                            + minimo + " caracteres."
            );
        }

        ClaimCase caso = casos.findByCaseCode(codigoCaso)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No se encontró el caso solicitado."
                ));

        if (!"EN_REVISION".equals(caso.getCurrentState())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El caso debe estar en EN_REVISION para registrar una decisión."
            );
        }

        // Únicamente el analista asignado puede tomar la decisión.
        if (caso.getAssignedAnalystId() == null
                || !caso.getAssignedAnalystId().equals(analista.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo el analista asignado puede decidir sobre este caso."
            );
        }

        // No permitimos registrar dos decisiones humanas para un mismo caso.
        if (decisiones.existsByClaimCaseId(caso.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El caso ya tiene una decisión humana registrada."
            );
        }

        String destino = "APROBAR".equals(accion)
                ? "APROBADO_ANALISTA"
                : "OBJETADO";

        // Validamos las transiciones contra el catálogo existente.
        validarTransicion("EN_REVISION", destino);

        // La objeción también debe permitir el cierre automático.
        if ("OBJETAR".equals(accion)) {
            validarTransicion("OBJETADO", "CERRADO");
        }

        // Guardamos la decisión humana y la observación completa.
        CaseReviewDecision decision = new CaseReviewDecision();
        decision.setClaimCaseId(caso.getId());
        decision.setAnalystUserId(analista.getId());
        decision.setAction(accion);
        decision.setObservation(observacion);

        CaseReviewDecision decisionGuardada = decisiones.save(decision);

        // Registramos la transición humana:
        // EN_REVISION -> APROBADO_ANALISTA u OBJETADO.
        registrarTransicionRevision(
                caso,
                "EN_REVISION",
                destino,
                analista,
                observacion,
                accion
        );

        String estadoFinal = destino;

        if ("OBJETAR".equals(accion)) {

            // El cierre es automático, por lo que su actor es SISTEMA.
            // El helper admite actor null y lo registra con ID null
            // y etiqueta "SISTEMA".
            registrarTransicionRevision(
                    caso,
                    "OBJETADO",
                    "CERRADO",
                    null,
                    "Cierre automático tras la objeción.",
                    "CIERRE_AUTOMATICO_OBJECION"
            );

            estadoFinal = "CERRADO";

            // Al cerrar el caso, detenemos el cómputo del ANS.
            caso.setClosedAt(Instant.now());
            caso.setAnsSemaphore("CUMPLIDO");
            caso.setAnsAlert(null);
        }

        // Persistimos el estado final de la reclamación.
        caso.setCurrentState(estadoFinal);
        casos.save(caso);

        // Auditoría de la decisión tomada por el analista.
        // No incluye documentos ni otros datos personales.
        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("accion", accion);
        detalles.put("estadoAnterior", "EN_REVISION");
        detalles.put("estadoIntermedio", destino);
        detalles.put("estadoFinal", estadoFinal);

        registrarAuditoria(
                analista,
                "CASE_DECISION",
                caso.getCaseCode(),
                "Decisión de revisión registrada: " + accion + ".",
                detalles
        );

        // Auditoría independiente del cierre automático.
        // Solo se genera para las objeciones que cierran el caso.
        if ("OBJETAR".equals(accion)) {

            Map<String, Object> detallesCierre = new LinkedHashMap<>();
            detallesCierre.put("estadoAnterior", "OBJETADO");
            detallesCierre.put("estadoNuevo", "CERRADO");
            detallesCierre.put(
                    "causa",
                    "CIERRE_AUTOMATICO_OBJECION"
            );

            registrarAuditoria(
                    null,
                    "CASE_AUTO_CLOSE",
                    caso.getCaseCode(),
                    "Cierre automático tras la objeción.",
                    detallesCierre
            );
        }

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("codigoCaso", caso.getCaseCode());
        respuesta.put("accion", accion);
        respuesta.put("estadoActual", estadoFinal);
        respuesta.put("analista", analista.getUsername());
        respuesta.put(
                "fechaDecision",
                decisionGuardada.getDecidedAt()
        );
        respuesta.put(
                "mensaje",
                "La decisión se registró correctamente."
        );

        return respuesta;
    }

    /**
     * Busca el usuario real de la sesión y verifica que sea un analista activo.
     */
    private Usuario obtenerAnalistaActivo(String username) {

        Usuario analista = usuarios.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "El usuario autenticado no existe."
                ));

        if (!analista.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La cuenta está desactivada."
            );
        }

        if (analista.getRole() != UserRole.ANALISTA) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo un analista puede realizar esta operación."
            );
        }

        return analista;
    }

    /**
     * Consulta el mínimo de caracteres configurado por ADMIN.
     * Si el valor no está disponible o no es válido, usa el requisito de 20.
     */
    private int obtenerMinimoObservacion() {

        try {
            return parametros.findById("OBSERVACION_MIN_CARACTERES")
                    .map(ParameterConfig::getParameterValue)
                    .map(String::trim)
                    .map(Integer::parseInt)
                    .filter(valor -> valor > 0)
                    .orElse(20);
        } catch (NumberFormatException ex) {
            return 20;
        }
    }

    /**
     * Rechaza cualquier transición que no esté activa en el catálogo.
     */
    private void validarTransicion(
            String origen,
            String destino) {

        boolean permitida =
                transiciones.existsByFromStateAndToStateAndConditionCodeAndActiveTrue(
                        origen,
                        destino,
                        "NORMAL"
                );

        if (!permitida) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "La transición de " + origen + " a " + destino
                            + " no está habilitada."
            );
        }
    }

    /**
     * Añade un evento nuevo de tracking con usuario, estados y comentario.
     *
     * Cuando actor es null, el evento se atribuye a SISTEMA. La columna
     * actor_user_id admite NULL y no se inventa un usuario humano.
     */
    private void registrarTransicionRevision(
            ClaimCase caso,
            String origen,
            String destino,
            Usuario actor,
            String comentario,
            String accion) {

        CaseTracking evento = new CaseTracking();
        evento.setClaimCaseId(caso.getId());
        evento.setPreviousState(origen);
        evento.setNewState(destino);

        if (actor == null) {
            evento.setActorUserId(null);
            evento.setActorLabel("SISTEMA");
        } else {
            evento.setActorUserId(actor.getId());
            evento.setActorLabel(actor.getUsername());
        }

        evento.setCommentText(comentario);

        Map<String, Object> metadatos = new LinkedHashMap<>();
        metadatos.put("accion", accion);
        metadatos.put("codigoCaso", caso.getCaseCode());

        evento.setEventMetadata(metadatos);
        seguimiento.save(evento);
    }

    /**
     * Registra acciones importantes sin incluir credenciales ni documentos.
     *
     * actor puede ser null cuando la acción se ejecuta automáticamente.
     * En ese caso se guarda actor_user_id = NULL y actor_username = SISTEMA.
     */
    private void registrarAuditoria(
            Usuario actor,
            String accion,
            String codigoCaso,
            String mensaje,
            Map<String, Object> detalles) {

        AuditLog registro = new AuditLog();

        if (actor == null) {
            registro.setActorUserId(null);
            registro.setActorUsername("SISTEMA");
        } else {
            registro.setActorUserId(actor.getId());
            registro.setActorUsername(actor.getUsername());
        }

        registro.setActionCode(accion);
        registro.setResourceType("CLAIM_CASE");
        registro.setResourceId(codigoCaso);
        registro.setResult("OK");
        registro.setMessage(mensaje);
        registro.setDetails(detalles);

        auditoria.save(registro);
    }
}
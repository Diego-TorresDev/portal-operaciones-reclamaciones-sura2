
package com.sura.portal_reclamaciones.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.sura.portal_reclamaciones.entity.AuditLog;
import com.sura.portal_reclamaciones.entity.CaseTracking;
import com.sura.portal_reclamaciones.entity.ClaimCase;
import com.sura.portal_reclamaciones.entity.PaymentBatch;
import com.sura.portal_reclamaciones.entity.PaymentBatchItem;
import com.sura.portal_reclamaciones.entity.UserRole;
import com.sura.portal_reclamaciones.entity.Usuario;
import com.sura.portal_reclamaciones.repository.AuditLogRepository;
import com.sura.portal_reclamaciones.repository.CaseStateTransitionRepository;
import com.sura.portal_reclamaciones.repository.CaseTrackingRepository;
import com.sura.portal_reclamaciones.repository.ClaimCaseRepository;
import com.sura.portal_reclamaciones.repository.PaymentBatchItemRepository;
import com.sura.portal_reclamaciones.repository.PaymentBatchRepository;
import com.sura.portal_reclamaciones.repository.UsuarioRepository;

@Service
public class LotesService {

    private final ClaimCaseRepository casos;
    private final PaymentBatchRepository lotes;
    private final PaymentBatchItemRepository items;
    private final UsuarioRepository usuarios;
    private final CaseStateTransitionRepository transiciones;
    private final CaseTrackingRepository seguimiento;
    private final AuditLogRepository auditoria;

    public LotesService(
            ClaimCaseRepository casos,
            PaymentBatchRepository lotes,
            PaymentBatchItemRepository items,
            UsuarioRepository usuarios,
            CaseStateTransitionRepository transiciones,
            CaseTrackingRepository seguimiento,
            AuditLogRepository auditoria) {

        this.casos = casos;
        this.lotes = lotes;
        this.items = items;
        this.usuarios = usuarios;
        this.transiciones = transiciones;
        this.seguimiento = seguimiento;
        this.auditoria = auditoria;
    }

    /**
     * Genera un lote con todos los casos aprobados que todavía no
     * estén incluidos en otro lote.
     *
     * La creación del lote, sus elementos, los estados, el tracking
     * y la bitácora forman parte de una sola transacción.
     */
    @Transactional
    public Map<String, Object> generarLote(String username) {

        // Identificamos al usuario a través de su sesión autenticada.
        Usuario actor = usuarios.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "El usuario autenticado no existe."
                ));

        if (!actor.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "La cuenta está desactivada."
            );
        }

        if (actor.getRole() != UserRole.ANALISTA
                && actor.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "El usuario no tiene permiso para generar lotes."
            );
        }

        // Recuperamos ambos grupos de casos aprobados.
        List<ClaimCase> disponibles = new ArrayList<>();
        agregarCasosDisponibles("APROBADO_AUTOMATICO", disponibles);
        agregarCasosDisponibles("APROBADO_ANALISTA", disponibles);

        if (disponibles.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No hay casos aprobados disponibles para generar un lote."
            );
        }

        BigDecimal valorTotal = BigDecimal.ZERO;

        // Comprobamos importes y transiciones antes de guardar el lote.
        for (ClaimCase caso : disponibles) {

            if (caso.getClaimAmount() == null
                    || caso.getClaimAmount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Un caso aprobado tiene un importe inválido."
                );
            }

            validarTransicion(
                    caso.getCurrentState(),
                    "EN_LOTE_PAGO"
            );

            valorTotal = valorTotal.add(caso.getClaimAmount());
        }

        Instant ahora = Instant.now();

        // Creamos el encabezado del lote con cantidad y valor calculados.
        PaymentBatch lote = new PaymentBatch();
        lote.setBatchCode(generarCodigoLote());
        lote.setStatus("EN_LOTE_PAGO");
        lote.setItemCount(disponibles.size());
        lote.setTotalAmount(valorTotal);
        lote.setGeneratedByUserId(actor.getId());
        lote.setNotes(
                "Lote generado por " + actor.getUsername()
                        + " con " + disponibles.size() + " casos aprobados."
        );

        PaymentBatch loteGuardado = lotes.save(lote);

        // Incorporamos cada reclamación sin permitir que pertenezca a dos lotes.
        for (ClaimCase caso : disponibles) {

            PaymentBatchItem item = new PaymentBatchItem();
            item.setPaymentBatchId(loteGuardado.getId());
            item.setClaimCaseId(caso.getId());
            item.setCaseCodeSnapshot(caso.getCaseCode());
            item.setAmountSnapshot(caso.getClaimAmount());

            items.save(item);

            String estadoAnterior = caso.getCurrentState();

            // EN_LOTE_PAGO detiene el cómputo del ANS.
            caso.setCurrentState("EN_LOTE_PAGO");
            caso.setAnsSemaphore("CUMPLIDO");
            caso.setAnsAlert(null);

            casos.save(caso);

            // Conservamos el historial del cambio de estado.
            CaseTracking evento = new CaseTracking();
            evento.setClaimCaseId(caso.getId());
            evento.setPreviousState(estadoAnterior);
            evento.setNewState("EN_LOTE_PAGO");
            evento.setActorUserId(actor.getId());
            evento.setActorLabel(actor.getUsername());
            evento.setCommentText(
                    "Caso incorporado al lote " + loteGuardado.getBatchCode() + "."
            );

            Map<String, Object> metadatos = new LinkedHashMap<>();
            metadatos.put("loteId", loteGuardado.getId());
            metadatos.put("codigoLote", loteGuardado.getBatchCode());

            evento.setEventMetadata(metadatos);
            seguimiento.save(evento);
        }

        // Una entrada de auditoría resume la generación del lote.
        AuditLog registro = new AuditLog();
        registro.setActorUserId(actor.getId());
        registro.setActorUsername(actor.getUsername());
        registro.setActionCode("PAYMENT_BATCH_CREATE");
        registro.setResourceType("PAYMENT_BATCH");
        registro.setResourceId(loteGuardado.getBatchCode());
        registro.setResult("OK");
        registro.setMessage("Lote de pago generado correctamente.");

        Map<String, Object> detalles = new LinkedHashMap<>();
        detalles.put("loteId", loteGuardado.getId());
        detalles.put("cantidadCasos", disponibles.size());
        detalles.put("valorTotal", valorTotal.toPlainString());

        registro.setDetails(detalles);
        auditoria.save(registro);

        // Devolvemos el resumen del lote, sin datos personales de los afiliados.
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("loteId", loteGuardado.getId());
        respuesta.put("codigoLote", loteGuardado.getBatchCode());
        respuesta.put("estado", loteGuardado.getStatus());
        respuesta.put("cantidadCasos", loteGuardado.getItemCount());
        respuesta.put("valorTotal", loteGuardado.getTotalAmount());
        respuesta.put("generadoEn", loteGuardado.getGeneratedAt());
        respuesta.put(
                "mensaje",
                "Lote generado correctamente. El pago aún no ha sido confirmado."
        );

        return respuesta;
    }

    /** Añade solamente los casos que aún no estén incluidos en otro lote. */
    private void agregarCasosDisponibles(
            String estado,
            List<ClaimCase> destino) {

        List<ClaimCase> candidatos =
                casos.findByCurrentStateOrderByAnsDeadlineDateAsc(estado);

        for (ClaimCase caso : candidatos) {
            if (!items.existsByClaimCaseId(caso.getId())) {
                destino.add(caso);
            }
        }
    }

    /** Consulta el catálogo para validar cada cambio de estado. */
    private void validarTransicion(String origen, String destino) {

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

    /** Genera un código único para identificar el lote de pago. */
    private String generarCodigoLote() {

        String fecha = DateTimeFormatter
                .ofPattern("yyyyMMdd-HHmmss")
                .withZone(java.time.ZoneOffset.UTC)
                .format(Instant.now());

        String sufijo = UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase(Locale.ROOT);

        return "LOTE-" + fecha + "-" + sufijo;
    }

    
/**
 * Confirma el pago simulado de un lote.
 * Solo ADMIN puede ejecutar esta operación.
 * Cada caso pasa por PAGADO y luego se cierra automáticamente.
 */
@Transactional
public Map<String, Object> confirmarPago(Long loteId, String username) {

    // Identificamos al administrador desde la sesión autenticada.
    Usuario administrador = usuarios.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "El usuario autenticado no existe."
            ));

    if (!administrador.isActive()
            || administrador.getRole() != UserRole.ADMIN) {
        throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Solo un administrador activo puede confirmar pagos."
        );
    }

    // Recuperamos el lote y comprobamos que aún no esté pagado.
    PaymentBatch lote = lotes.findById(loteId)
            .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No se encontró el lote solicitado."
            ));

    if (!"EN_LOTE_PAGO".equals(lote.getStatus())) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El lote no está pendiente de confirmación de pago."
        );
    }

    List<PaymentBatchItem> elementos =
            items.findByPaymentBatchIdOrderByIdAsc(lote.getId());

    if (elementos.isEmpty()) {
        throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "El lote no contiene casos para pagar."
        );
    }

    // Validamos todos los casos antes de modificar cualquiera.
    List<ClaimCase> casosDelLote = new ArrayList<>();

    for (PaymentBatchItem elemento : elementos) {

        ClaimCase caso = casos.findById(elemento.getClaimCaseId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Un elemento del lote referencia un caso inexistente."
                ));

        if (!"EN_LOTE_PAGO".equals(caso.getCurrentState())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El caso " + caso.getCaseCode()
                            + " no está en EN_LOTE_PAGO."
            );
        }

        // Las dos transiciones deben estar activas en el catálogo.
        validarTransicion("EN_LOTE_PAGO", "PAGADO");
        validarTransicion("PAGADO", "CERRADO");

        casosDelLote.add(caso);
    }

    Instant ahora = Instant.now();

    // Cada caso recorre las dos transiciones exigidas por el proceso.
    for (ClaimCase caso : casosDelLote) {

        String estadoAnterior = caso.getCurrentState();

        // Primera transición: EN_LOTE_PAGO -> PAGADO.
        caso.setCurrentState("PAGADO");
        caso.setAnsSemaphore("CUMPLIDO");
        caso.setAnsAlert(null);
        casos.save(caso);

        registrarTrackingConfirmacion(
                caso,
                estadoAnterior,
                "PAGADO",
                administrador.getId(),
                administrador.getUsername(),
                "Pago simulado confirmado por el administrador.",
                lote
        );

        // Segunda transición: PAGADO -> CERRADO, automática tras el pago.
        caso.setCurrentState("CERRADO");
        caso.setClosedAt(ahora);
        caso.setAnsSemaphore("CUMPLIDO");
        caso.setAnsAlert(null);
        casos.save(caso);

        registrarTrackingConfirmacion(
                caso,
                "PAGADO",
                "CERRADO",
                null,
                "SISTEMA",
                "Cierre automático después de confirmar el pago.",
                lote
        );
    }

    // Actualizamos el encabezado del lote una vez procesados sus casos.
    lote.setStatus("PAGADO");
    lote.setConfirmedByUserId(administrador.getId());
    lote.setPaidAt(ahora);
    lote.setClosedAt(ahora);

    PaymentBatch loteActualizado = lotes.save(lote);

    // Registramos la confirmación en la bitácora de auditoría.
    AuditLog registro = new AuditLog();
    registro.setActorUserId(administrador.getId());
    registro.setActorUsername(administrador.getUsername());
    registro.setActionCode("PAYMENT_BATCH_PAY");
    registro.setResourceType("PAYMENT_BATCH");
    registro.setResourceId(loteActualizado.getBatchCode());
    registro.setResult("OK");
    registro.setMessage("Pago simulado confirmado y casos cerrados.");

    Map<String, Object> detalles = new LinkedHashMap<>();
    detalles.put("loteId", loteActualizado.getId());
    detalles.put("cantidadCasos", casosDelLote.size());
    detalles.put(
            "valorTotal",
            loteActualizado.getTotalAmount().toPlainString()
    );
    registro.setDetails(detalles);
    auditoria.save(registro);

    // Devolvemos un resumen, no los datos personales de los afiliados.
    Map<String, Object> respuesta = new LinkedHashMap<>();
    respuesta.put("loteId", loteActualizado.getId());
    respuesta.put("codigoLote", loteActualizado.getBatchCode());
    respuesta.put("estadoLote", loteActualizado.getStatus());
    respuesta.put("cantidadCasosCerrados", casosDelLote.size());
    respuesta.put("valorTotal", loteActualizado.getTotalAmount());
    respuesta.put("confirmadoPor", administrador.getUsername());
    respuesta.put("fechaPago", loteActualizado.getPaidAt());
    respuesta.put(
            "mensaje",
            "Pago simulado confirmado; los casos del lote quedaron cerrados."
    );

    return respuesta;
}

/**
 * Registra una transición del proceso de pago sin sobrescribir eventos previos.
 */
private void registrarTrackingConfirmacion(
        ClaimCase caso,
        String origen,
        String destino,
        Long usuarioId,
        String actor,
        String comentario,
        PaymentBatch lote) {

    CaseTracking evento = new CaseTracking();
    evento.setClaimCaseId(caso.getId());
    evento.setPreviousState(origen);
    evento.setNewState(destino);
    evento.setActorUserId(usuarioId);
    evento.setActorLabel(actor);
    evento.setCommentText(comentario);

    Map<String, Object> metadatos = new LinkedHashMap<>();
    metadatos.put("loteId", lote.getId());
    metadatos.put("codigoLote", lote.getBatchCode());

    evento.setEventMetadata(metadatos);
    seguimiento.save(evento);
}

}

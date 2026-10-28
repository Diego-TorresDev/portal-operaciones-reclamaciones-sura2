package com.sura.portal_reclamaciones.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import com.sura.portal_reclamaciones.entity.AuditLog;
import com.sura.portal_reclamaciones.entity.ParameterChangeLog;
import com.sura.portal_reclamaciones.entity.ParameterConfig;
import com.sura.portal_reclamaciones.entity.Usuario;
import com.sura.portal_reclamaciones.repository.AuditLogRepository;
import com.sura.portal_reclamaciones.repository.ParameterChangeLogRepository;
import com.sura.portal_reclamaciones.repository.ParameterConfigRepository;
import com.sura.portal_reclamaciones.repository.UsuarioRepository;

/**
 * Administra los parámetros de negocio guardados en PostgreSQL.
 * Cada cambio conserva su historial y queda anotado en la bitácora general.
 */
@Service
public class ParametroService {

    private static final String PARAMETRO_MODO_R17 = "R17_MODO_ANALISIS";
    private static final Set<String> MODOS_R17 = Set.of("IA", "TEXTOS", "AMBAS");

    private final ParameterConfigRepository parametroRepository;
    private final ParameterChangeLogRepository historialRepository;
    private final AuditLogRepository auditoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ObjectMapper objectMapper;

    public ParametroService(
            ParameterConfigRepository parametroRepository,
            ParameterChangeLogRepository historialRepository,
            AuditLogRepository auditoriaRepository,
            UsuarioRepository usuarioRepository,
            ObjectMapper objectMapper) {

        this.parametroRepository = parametroRepository;
        this.historialRepository = historialRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.objectMapper = objectMapper;
    }

    /** Devuelve todos los parámetros ordenados por clave. */
    @Transactional(readOnly = true)
    public List<ParametroVista> listar() {
        return parametroRepository.findAllByOrderByParameterKeyAsc()
                .stream()
                .map(this::convertirVista)
                .toList();
    }

    /**
     * Actualiza un parámetro y registra el cambio en una única transacción.
     * La identidad del usuario proviene del JWT validado por Spring Security.
     */
    @Transactional
    public ParametroVista actualizar(
            String claveIngresada,
            String nuevoValorIngresado,
            String motivoIngresado,
            String usernameIngresado) {

        String clave = normalizarClave(claveIngresada);
        String username = normalizarUsername(usernameIngresado);

        if (clave.isBlank() || clave.length() > 120) {
            throw new IllegalArgumentException("La clave del parámetro no es válida.");
        }

        if (nuevoValorIngresado == null) {
            throw new IllegalArgumentException("El nuevo valor del parámetro es obligatorio.");
        }

        String nuevoValor = nuevoValorIngresado.trim();
        String motivo = motivoIngresado == null ? "" : motivoIngresado.trim();

        if (motivo.length() < 10 || motivo.length() > 500) {
            throw new IllegalArgumentException("El motivo debe tener entre 10 y 500 caracteres.");
        }

        ParameterConfig parametro = parametroRepository.findById(clave)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No existe el parámetro solicitado."));

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "No se pudo identificar al usuario autenticado."));

        // El modo de R17 se normaliza para que la configuración almacenada
        // siempre use uno de los tres valores reconocidos por el motor.
        if (PARAMETRO_MODO_R17.equals(clave)) {
            nuevoValor = nuevoValor.toUpperCase(Locale.ROOT);
        }

        validarValor(parametro, nuevoValor);

        String valorAnterior = parametro.getParameterValue();
        if (valorAnterior.equals(nuevoValor)) {
            return convertirVista(parametro);
        }

        parametro.setParameterValue(nuevoValor);
        parametro.setUpdatedByUserId(usuario.getId());
        parametroRepository.save(parametro);

        ParameterChangeLog cambio = new ParameterChangeLog();
        cambio.setParameterKey(clave);
        cambio.setOldValue(valorAnterior);
        cambio.setNewValue(nuevoValor);
        cambio.setChangedByUserId(usuario.getId());
        cambio.setChangedByUsername(usuario.getUsername());
        cambio.setReason(motivo);
        historialRepository.save(cambio);

        AuditLog auditoria = new AuditLog();
        auditoria.setActorUserId(usuario.getId());
        auditoria.setActorUsername(usuario.getUsername());
        auditoria.setActionCode("PARAMETRO_ACTUALIZADO");
        auditoria.setResourceType("PARAMETRO");
        auditoria.setResourceId(clave);
        auditoria.setResult("OK");
        auditoria.setMessage("Se actualizó un parámetro de negocio.");

        Map<String, Object> detalles = new HashMap<>();
        detalles.put("clave", clave);
        detalles.put("tipo", parametro.getValueType());
        detalles.put("motivo", motivo);
        auditoria.setDetails(detalles);
        auditoriaRepository.save(auditoria);

        return convertirVista(parametro);
    }

    /** Valida el valor según su tipo y las restricciones de negocio específicas. */
    private void validarValor(ParameterConfig parametro, String valor) {

        String clave = parametro.getParameterKey();
        String tipo = parametro.getValueType().trim().toUpperCase(Locale.ROOT);

        // Restricción de negocio específica: no aceptar modos arbitrarios para R17.
        if (PARAMETRO_MODO_R17.equals(clave)) {
            String modo = valor.trim().toUpperCase(Locale.ROOT);
            if (!MODOS_R17.contains(modo)) {
                throw new IllegalArgumentException(
                        "R17_MODO_ANALISIS solo admite IA, TEXTOS o AMBAS.");
            }
            return;
        }

        try {
            switch (tipo) {
                case "INTEGER" -> {
                    long numero = Long.parseLong(valor);
                    if (numero < 0) {
                        throw new IllegalArgumentException(
                                "El parámetro debe ser un entero no negativo.");
                    }
                }
                case "DECIMAL" -> {
                    BigDecimal numero = new BigDecimal(valor);
                    if (numero.signum() < 0) {
                        throw new IllegalArgumentException(
                                "El parámetro debe ser un decimal no negativo.");
                    }
                }
                case "DATE" -> {
                    if (valor.isBlank() && "FECHA_REFERENCIA".equals(clave)) {
                        break;
                    }
                    LocalDate.parse(valor);
                }
                case "JSON" -> {
                    if (valor.isBlank() || objectMapper.readTree(valor) == null) {
                        throw new IllegalArgumentException(
                                "El valor debe contener un JSON válido.");
                    }
                }
                case "BOOLEAN" -> {
                    String booleano = valor.toUpperCase(Locale.ROOT);
                    if (!List.of("TRUE", "FALSE", "SI", "SÍ", "NO", "S", "N")
                            .contains(booleano)) {
                        throw new IllegalArgumentException(
                                "El valor booleano debe ser true, false, sí o no.");
                    }
                }
                case "STRING" -> {
                    if (valor.isBlank()) {
                        throw new IllegalArgumentException(
                                "El parámetro no puede quedar vacío.");
                    }
                }
                default -> throw new IllegalArgumentException(
                        "El tipo de parámetro no está reconocido.");
            }
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "El nuevo valor no cumple el formato del parámetro.");
        }
    }

    private String normalizarClave(String clave) {
        return clave == null ? "" : clave.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizarUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private ParametroVista convertirVista(ParameterConfig parametro) {
        return new ParametroVista(
                parametro.getParameterKey(),
                parametro.getParameterValue(),
                parametro.getValueType(),
                parametro.getDescription(),
                parametro.getUpdatedAt(),
                parametro.getUpdatedByUserId());
    }

    public record ParametroVista(
            String clave,
            String valor,
            String tipo,
            String descripcion,
            Instant actualizadoEn,
            Long actualizadoPorUsuarioId) {
    }
}

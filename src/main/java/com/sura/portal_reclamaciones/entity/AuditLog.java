package com.sura.portal_reclamaciones.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Bitácora general de seguridad y operaciones del portal.
 *
 * Debe registrar acciones importantes sin guardar contraseñas, tokens JWT,
 * documentos completos ni filas completas del Excel. El resultado distingue
 * operaciones exitosas, rechazadas y errores técnicos.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", nullable = false, length = 80)
    private String actorUsername = "SISTEMA";

    @Column(name = "action_code", nullable = false, length = 100)
    private String actionCode;

    @Column(name = "resource_type", length = 100)
    private String resourceType;

    @Column(name = "resource_id", length = 120)
    private String resourceId;

    // PostgreSQL limita este campo a OK, RECHAZADO o ERROR.
    @Column(name = "result", nullable = false, length = 20)
    private String result;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    // JSONB permite añadir metadatos breves sin cambiar el esquema por cada acción.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> details = new HashMap<>();

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public AuditLog() { }

    @PrePersist
    protected void beforeInsert() {
        if (actorUsername == null || actorUsername.isBlank()) actorUsername = "SISTEMA";
        if (details == null) details = new HashMap<>();
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getActorUserId() { return actorUserId; }
    public void setActorUserId(Long v) { this.actorUserId = v; }
    public String getActorUsername() { return actorUsername; }
    public void setActorUsername(String v) { this.actorUsername = v; }
    public String getActionCode() { return actionCode; }
    public void setActionCode(String v) { this.actionCode = v; }
    public String getResourceType() { return resourceType; }
    public void setResourceType(String v) { this.resourceType = v; }
    public String getResourceId() { return resourceId; }
    public void setResourceId(String v) { this.resourceId = v; }
    public String getResult() { return result; }
    public void setResult(String v) { this.result = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public Map<String, Object> getDetails() { return details; }
    public void setDetails(Map<String, Object> v) { this.details = v; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String v) { this.correlationId = v; }
    public Instant getCreatedAt() { return createdAt; }
}

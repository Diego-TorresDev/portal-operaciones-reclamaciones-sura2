package com.sura.portal_reclamaciones.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Conserva cada modificación de un parámetro de negocio.
 *
 * Se guarda el valor anterior y el nuevo, junto con la persona que realizó
 * el cambio y el motivo. El servicio de parámetros deberá insertar este registro
 * en la misma transacción en la que actualiza ParameterConfig.
 */
@Entity
@Table(name = "parameter_change_log")
public class ParameterChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parameter_key", nullable = false, length = 120)
    private String parameterKey;

    @Column(name = "old_value", nullable = false, columnDefinition = "text")
    private String oldValue;

    @Column(name = "new_value", nullable = false, columnDefinition = "text")
    private String newValue;

    @Column(name = "changed_by_user_id")
    private Long changedByUserId;

    // Conservamos también el nombre por si posteriormente se desactiva el usuario.
    @Column(name = "changed_by_username", nullable = false, length = 80)
    private String changedByUsername;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private Instant changedAt;

    public ParameterChangeLog() { }

    @PrePersist
    protected void beforeInsert() {
        if (changedAt == null) changedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getParameterKey() { return parameterKey; }
    public void setParameterKey(String v) { this.parameterKey = v; }
    public String getOldValue() { return oldValue; }
    public void setOldValue(String v) { this.oldValue = v; }
    public String getNewValue() { return newValue; }
    public void setNewValue(String v) { this.newValue = v; }
    public Long getChangedByUserId() { return changedByUserId; }
    public void setChangedByUserId(Long v) { this.changedByUserId = v; }
    public String getChangedByUsername() { return changedByUsername; }
    public void setChangedByUsername(String v) { this.changedByUsername = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
    public Instant getChangedAt() { return changedAt; }
}

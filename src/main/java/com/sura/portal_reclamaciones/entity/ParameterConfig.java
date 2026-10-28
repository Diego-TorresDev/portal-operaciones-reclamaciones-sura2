package com.sura.portal_reclamaciones.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Parámetro de negocio editable desde la aplicación.
 *
 * Se conserva el valor como texto y el campo valueType indica cómo validarlo
 * antes de utilizarlo: INTEGER, DECIMAL, DATE, JSON, STRING o BOOLEAN. Así
 * los administradores pueden cambiar la configuración sin modificar Java.
 */
@Entity
@Table(name = "parameter_config")
public class ParameterConfig {

    // La clave funcional es la clave primaria; no necesita un ID numérico aparte.
    @Id
    @Column(name = "parameter_key", nullable = false, length = 120)
    private String parameterKey;

    @Column(name = "parameter_value", nullable = false, columnDefinition = "text")
    private String parameterValue;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ParameterConfig() { }

    @PrePersist
    protected void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void beforeUpdate() { updatedAt = Instant.now(); }

    public String getParameterKey() { return parameterKey; }
    public void setParameterKey(String v) { this.parameterKey = v; }
    public String getParameterValue() { return parameterValue; }
    public void setParameterValue(String v) { this.parameterValue = v; }
    public String getValueType() { return valueType; }
    public void setValueType(String v) { this.valueType = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Long getUpdatedByUserId() { return updatedByUserId; }
    public void setUpdatedByUserId(Long v) { this.updatedByUserId = v; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

package com.sura.portal_reclamaciones.entity;

import java.math.BigDecimal;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Representa un plan de aseguramiento de la tabla insurance_plan.
 *
 * El motor de reglas consulta aquí el monto máximo permitido para aprobación
 * automática y el tope anual. Los parámetros editables del portal deberán
 * mantenerse sincronizados con estos valores desde el servicio de parámetros.
 */
@Entity
@Table(name = "insurance_plan")
public class InsurancePlan {

    // Identificador interno generado por PostgreSQL.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Código normalizado del plan, por ejemplo BÁSICO, CLÁSICO o GLOBAL.
    @Column(name = "plan_code", nullable = false, unique = true, length = 80)
    private String planCode;

    // Nombre descriptivo que se mostrará en la aplicación.
    @Column(name = "plan_name", nullable = false, length = 180)
    private String planName;

    // Monto máximo que puede aprobarse automáticamente para este plan.
    @Column(name = "max_auto_approval_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal maxAutoApprovalAmount;

    // Tope anual del plan; se compara con lo consumido más el valor reclamado.
    @Column(name = "annual_cap", nullable = false, precision = 16, scale = 2)
    private BigDecimal annualCap;

    // Permite desactivar un plan sin eliminar el historial de los casos ligados a él.
    @Column(name = "active", nullable = false)
    private boolean active = true;

    // JSONB conserva columnas adicionales del Excel que aún no tengan campo propio.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extraData = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // JPA utiliza este constructor al reconstruir el objeto desde la base de datos.
    public InsurancePlan() {
    }

    // Inicializamos las fechas antes de insertar el registro.
    @PrePersist
    protected void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (extraData == null) extraData = new HashMap<>();
    }

    // Actualizamos la fecha de modificación cuando cambia el plan.
    @PreUpdate
    protected void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public BigDecimal getMaxAutoApprovalAmount() { return maxAutoApprovalAmount; }
    public void setMaxAutoApprovalAmount(BigDecimal maxAutoApprovalAmount) { this.maxAutoApprovalAmount = maxAutoApprovalAmount; }
    public BigDecimal getAnnualCap() { return annualCap; }
    public void setAnnualCap(BigDecimal annualCap) { this.annualCap = annualCap; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Map<String, Object> getExtraData() { return extraData; }
    public void setExtraData(Map<String, Object> extraData) { this.extraData = extraData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

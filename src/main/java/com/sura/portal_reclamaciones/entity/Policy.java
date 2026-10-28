package com.sura.portal_reclamaciones.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Representa una póliza de la tabla policy.
 *
 * Aquí se conservan su estado, vigencia, plan y consumo anual. Estas propiedades
 * serán consultadas por las reglas R04, R05, R06, R15 y R16 del motor de decisión.
 */
@Entity
@Table(name = "policy")
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Número normalizado de póliza. La base de datos impide números duplicados.
    @Column(name = "policy_number", nullable = false, unique = true, length = 100)
    private String policyNumber;

    // El valor debe coincidir con el estado esperado por las reglas, por ejemplo "Vigente".
    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    // Varias pólizas pueden estar asociadas al mismo plan.
    // LAZY evita cargar el plan si una consulta solo necesita datos de la póliza.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id")
    private InsurancePlan plan;

    // Consumo acumulado del año antes de sumar el reclamo que se está evaluando.
    @Column(name = "annual_consumed_amount", nullable = false, precision = 16, scale = 2)
    private BigDecimal annualConsumedAmount = BigDecimal.ZERO;

    @Column(name = "affiliate_name", length = 200)
    private String affiliateName;

    // Este dato personal no debe exponerse completo en listados ni escribirse en logs.
    @Column(name = "affiliate_document", length = 80)
    private String affiliateDocument;

    // Guarda columnas originales de la referencia que todavía no se modelen explícitamente.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extraData = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Policy() {
    }

    @PrePersist
    protected void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (annualConsumedAmount == null) annualConsumedAmount = BigDecimal.ZERO;
        if (extraData == null) extraData = new HashMap<>();
    }

    @PreUpdate
    protected void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPolicyNumber() { return policyNumber; }
    public void setPolicyNumber(String policyNumber) { this.policyNumber = policyNumber; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public InsurancePlan getPlan() { return plan; }
    public void setPlan(InsurancePlan plan) { this.plan = plan; }
    public BigDecimal getAnnualConsumedAmount() { return annualConsumedAmount; }
    public void setAnnualConsumedAmount(BigDecimal annualConsumedAmount) { this.annualConsumedAmount = annualConsumedAmount; }
    public String getAffiliateName() { return affiliateName; }
    public void setAffiliateName(String affiliateName) { this.affiliateName = affiliateName; }
    public String getAffiliateDocument() { return affiliateDocument; }
    public void setAffiliateDocument(String affiliateDocument) { this.affiliateDocument = affiliateDocument; }
    public Map<String, Object> getExtraData() { return extraData; }
    public void setExtraData(Map<String, Object> extraData) { this.extraData = extraData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

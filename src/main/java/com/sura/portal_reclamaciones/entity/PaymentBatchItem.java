package com.sura.portal_reclamaciones.entity;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Relaciona una reclamación con el lote en el que quedó incluida.
 *
 * caseCodeSnapshot y amountSnapshot preservan el código y el monto que se
 * incorporaron al lote, incluso si posteriormente cambia algún dato del caso.
 */
@Entity
@Table(name = "payment_batch_item")
public class PaymentBatchItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_batch_id", nullable = false)
    private Long paymentBatchId;

    // La base de datos impide que una misma reclamación entre en más de un lote.
    @Column(name = "claim_case_id", nullable = false, unique = true)
    private Long claimCaseId;

    @Column(name = "case_code_snapshot", nullable = false, length = 50)
    private String caseCodeSnapshot;

    @Column(name = "amount_snapshot", nullable = false, precision = 16, scale = 2)
    private BigDecimal amountSnapshot;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    public PaymentBatchItem() { }

    @PrePersist
    protected void beforeInsert() {
        if (addedAt == null) addedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getPaymentBatchId() { return paymentBatchId; }
    public void setPaymentBatchId(Long v) { this.paymentBatchId = v; }
    public Long getClaimCaseId() { return claimCaseId; }
    public void setClaimCaseId(Long v) { this.claimCaseId = v; }
    public String getCaseCodeSnapshot() { return caseCodeSnapshot; }
    public void setCaseCodeSnapshot(String v) { this.caseCodeSnapshot = v; }
    public BigDecimal getAmountSnapshot() { return amountSnapshot; }
    public void setAmountSnapshot(BigDecimal v) { this.amountSnapshot = v; }
    public Instant getAddedAt() { return addedAt; }
}

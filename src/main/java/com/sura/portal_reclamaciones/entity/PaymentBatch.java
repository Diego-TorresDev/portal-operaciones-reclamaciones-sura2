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
 * Representa un lote de pagos simulados.
 *
 * El servicio de lotes incorporará todos los casos aprobados que todavía no
 * estén en otro lote, guardará cantidad y suma, y permitirá que solo un ADMIN
 * confirme la etapa de pago. No se realiza ninguna transferencia bancaria real.
 */
@Entity
@Table(name = "payment_batch")
public class PaymentBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_code", nullable = false, unique = true, length = 60)
    private String batchCode;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "EN_LOTE_PAGO";

    @Column(name = "item_count", nullable = false)
    private Integer itemCount = 0;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "generated_by_user_id")
    private Long generatedByUserId;

    @Column(name = "confirmed_by_user_id")
    private Long confirmedByUserId;

    @Column(name = "generated_at", nullable = false, updatable = false)
    private Instant generatedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    public PaymentBatch() { }

    @PrePersist
    protected void beforeInsert() {
        if (status == null || status.isBlank()) status = "EN_LOTE_PAGO";
        if (itemCount == null) itemCount = 0;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        if (generatedAt == null) generatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String v) { this.batchCode = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Integer getItemCount() { return itemCount; }
    public void setItemCount(Integer v) { this.itemCount = v; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal v) { this.totalAmount = v; }
    public Long getGeneratedByUserId() { return generatedByUserId; }
    public void setGeneratedByUserId(Long v) { this.generatedByUserId = v; }
    public Long getConfirmedByUserId() { return confirmedByUserId; }
    public void setConfirmedByUserId(Long v) { this.confirmedByUserId = v; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant v) { this.paidAt = v; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }
    public String getNotes() { return notes; }
    public void setNotes(String v) { this.notes = v; }
}

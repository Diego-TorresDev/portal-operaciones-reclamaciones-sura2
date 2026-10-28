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
 * Registra la decisión final de un analista sobre un caso de la bandeja manual.
 *
 * La base de datos valida que la observación tenga al menos 20 caracteres.
 * El servicio debe comprobar además que el caso esté asignado a ese analista,
 * y que la acción sea APROBAR u OBJETAR antes de guardar el resultado.
 */
@Entity
@Table(name = "case_review_decision")
public class CaseReviewDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "claim_case_id", nullable = false, unique = true)
    private Long claimCaseId;

    @Column(name = "analyst_user_id", nullable = false)
    private Long analystUserId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "observation", nullable = false, columnDefinition = "text")
    private String observation;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    public CaseReviewDecision() { }

    @PrePersist
    protected void beforeInsert() {
        if (decidedAt == null) decidedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getClaimCaseId() { return claimCaseId; }
    public void setClaimCaseId(Long v) { this.claimCaseId = v; }
    public Long getAnalystUserId() { return analystUserId; }
    public void setAnalystUserId(Long v) { this.analystUserId = v; }
    public String getAction() { return action; }
    public void setAction(String v) { this.action = v; }
    public String getObservation() { return observation; }
    public void setObservation(String v) { this.observation = v; }
    public Instant getDecidedAt() { return decidedAt; }
}

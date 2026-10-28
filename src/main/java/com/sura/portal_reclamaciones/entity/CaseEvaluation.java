package com.sura.portal_reclamaciones.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

/** Una ejecución completa del motor; las reevaluaciones crean otra fila y preservan el historial. */
@Entity
@Table(name = "case_evaluation")
public class CaseEvaluation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "claim_case_id", nullable = false)
    private Long claimCaseId;
    @Column(name = "evaluation_number", nullable = false)
    private Integer evaluationNumber;
    @Column(name = "decision", length = 20, nullable = false)
    private String decision;
    @Column(name = "priority", length = 10)
    private String priority;
    @Column(name = "business_reason", columnDefinition = "text", nullable = false)
    private String businessReason;
    @Column(name = "failed_rule_count", nullable = false)
    private Short failedRuleCount;
    // JSONB permite consultar y conservar los códigos de todas las reglas fallidas.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "failed_rule_codes", columnDefinition = "jsonb", nullable = false)
    private List<String> failedRuleCodes = new ArrayList<>();
    // Fotografía de los parámetros usados para explicar una evaluación histórica.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "parameter_snapshot", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> parameterSnapshot = new HashMap<>();
    @Column(name = "evaluated_by_user_id")
    private Long evaluatedByUserId;
    @Column(name = "is_current", nullable = false)
    private boolean current = true;
    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;
    public CaseEvaluation() { }
    @PrePersist protected void beforeInsert() {
        if (evaluatedAt == null) evaluatedAt = Instant.now();
        if (failedRuleCount == null) failedRuleCount = (short) 0;
        if (failedRuleCodes == null) failedRuleCodes = new ArrayList<>();
        if (parameterSnapshot == null) parameterSnapshot = new HashMap<>();
    }
    public Long getId() { return id; }
    public Long getClaimCaseId() { return claimCaseId; }
    public void setClaimCaseId(Long v) { this.claimCaseId = v; }
    public Integer getEvaluationNumber() { return evaluationNumber; }
    public void setEvaluationNumber(Integer v) { this.evaluationNumber = v; }
    public String getDecision() { return decision; }
    public void setDecision(String v) { this.decision = v; }
    public String getPriority() { return priority; }
    public void setPriority(String v) { this.priority = v; }
    public String getBusinessReason() { return businessReason; }
    public void setBusinessReason(String v) { this.businessReason = v; }
    public Short getFailedRuleCount() { return failedRuleCount; }
    public void setFailedRuleCount(Short v) { this.failedRuleCount = v; }
    public List<String> getFailedRuleCodes() { return failedRuleCodes; }
    public void setFailedRuleCodes(List<String> v) { this.failedRuleCodes = v; }
    public Map<String,Object> getParameterSnapshot() { return parameterSnapshot; }
    public void setParameterSnapshot(Map<String,Object> v) { this.parameterSnapshot = v; }
    public Long getEvaluatedByUserId() { return evaluatedByUserId; }
    public void setEvaluatedByUserId(Long v) { this.evaluatedByUserId = v; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean v) { this.current = v; }
    public Instant getEvaluatedAt() { return evaluatedAt; }
}

package com.sura.portal_reclamaciones.entity;

import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

/** Guarda el resultado individual de una regla, tanto si pasó como si falló. */
@Entity
@Table(name = "case_evaluation_rule")
@IdClass(CaseEvaluationRuleId.class)
public class CaseEvaluationRule {
    @Id @Column(name = "case_evaluation_id", nullable = false)
    private Long caseEvaluationId;
    @Id @Column(name = "rule_code", length = 10, nullable = false)
    private String ruleCode;
    @Column(name = "passed", nullable = false)
    private boolean passed;
    @Column(name = "business_explanation", columnDefinition = "text", nullable = false)
    private String businessExplanation;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> evidence = new HashMap<>();
    public CaseEvaluationRule() { }
    @PrePersist protected void beforeInsert() { if (evidence == null) evidence = new HashMap<>(); }
    public Long getCaseEvaluationId() { return caseEvaluationId; }
    public void setCaseEvaluationId(Long v) { this.caseEvaluationId = v; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String v) { this.ruleCode = v; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean v) { this.passed = v; }
    public String getBusinessExplanation() { return businessExplanation; }
    public void setBusinessExplanation(String v) { this.businessExplanation = v; }
    public Map<String,Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String,Object> v) { this.evidence = v; }
}

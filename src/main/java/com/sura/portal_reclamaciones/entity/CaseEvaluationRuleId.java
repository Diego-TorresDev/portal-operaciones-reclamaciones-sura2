package com.sura.portal_reclamaciones.entity;

import java.io.Serializable;
import java.util.Objects;

/** Clave compuesta: una fila por cada regla evaluada dentro de una evaluación. */
public class CaseEvaluationRuleId implements Serializable {
    private Long caseEvaluationId;
    private String ruleCode;
    public CaseEvaluationRuleId() { }
    public CaseEvaluationRuleId(Long caseEvaluationId, String ruleCode) {
        this.caseEvaluationId = caseEvaluationId; this.ruleCode = ruleCode;
    }
    public Long getCaseEvaluationId() { return caseEvaluationId; }
    public void setCaseEvaluationId(Long v) { this.caseEvaluationId = v; }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String v) { this.ruleCode = v; }
    // equals y hashCode son necesarios para que JPA compare correctamente la clave compuesta.
    @Override public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CaseEvaluationRuleId that)) return false;
        return Objects.equals(caseEvaluationId, that.caseEvaluationId) && Objects.equals(ruleCode, that.ruleCode);
    }
    @Override public int hashCode() { return Objects.hash(caseEvaluationId, ruleCode); }
}

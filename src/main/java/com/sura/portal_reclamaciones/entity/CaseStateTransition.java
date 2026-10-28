package com.sura.portal_reclamaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Una transición permitida; el servicio consulta este catálogo antes de cambiar el estado. */
@Entity
@Table(name = "case_state_transition")
public class CaseStateTransition {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // Son códigos con claves foráneas en PostgreSQL hacia case_state_definition.
    @Column(name = "from_state", length = 40, nullable = false)
    private String fromState;
    @Column(name = "to_state", length = 40, nullable = false)
    private String toState;
    @Column(name = "condition_code", length = 40, nullable = false)
    private String conditionCode = "NORMAL";
    @Column(name = "condition_description", columnDefinition = "text", nullable = false)
    private String conditionDescription;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    public CaseStateTransition() { }
    public Long getId() { return id; }
    public String getFromState() { return fromState; }
    public void setFromState(String v) { this.fromState = v; }
    public String getToState() { return toState; }
    public void setToState(String v) { this.toState = v; }
    public String getConditionCode() { return conditionCode; }
    public void setConditionCode(String v) { this.conditionCode = v; }
    public String getConditionDescription() { return conditionDescription; }
    public void setConditionDescription(String v) { this.conditionDescription = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}

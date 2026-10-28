package com.sura.portal_reclamaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo de los once estados permitidos para el ciclo de cada reclamación. */
@Entity
@Table(name = "case_state_definition")
public class CaseStateDefinition {
    @Id @Column(name = "state_code", length = 40, nullable = false)
    private String stateCode;
    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;
    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;
    @Column(name = "terminal", nullable = false)
    private boolean terminal;
    @Column(name = "display_order", nullable = false)
    private short displayOrder;
    public CaseStateDefinition() { }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String v) { this.stateCode = v; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { this.displayName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public boolean isTerminal() { return terminal; }
    public void setTerminal(boolean v) { this.terminal = v; }
    public short getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(short v) { this.displayOrder = v; }
}

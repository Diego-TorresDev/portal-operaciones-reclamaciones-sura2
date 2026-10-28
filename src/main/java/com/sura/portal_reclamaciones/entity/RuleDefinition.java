package com.sura.portal_reclamaciones.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo de reglas R01-R17; no guarda resultados por caso. */
@Entity
@Table(name = "rule_definition")
public class RuleDefinition {
    // El propio código R01...R17 es una clave estable para la regla.
    @Id @Column(name = "rule_code", length = 10, nullable = false)
    private String ruleCode;
    @Column(name = "rule_name", length = 160, nullable = false)
    private String ruleName;
    @Column(name = "description", columnDefinition = "text", nullable = false)
    private String description;
    @Column(name = "base_priority", length = 10, nullable = false)
    private String basePriority;
    @Column(name = "active", nullable = false)
    private boolean active = true;
    @Column(name = "display_order", nullable = false, unique = true)
    private short displayOrder;
    public RuleDefinition() { }
    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String v) { this.ruleCode = v; }
    public String getRuleName() { return ruleName; }
    public void setRuleName(String v) { this.ruleName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public String getBasePriority() { return basePriority; }
    public void setBasePriority(String v) { this.basePriority = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public short getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(short v) { this.displayOrder = v; }
}

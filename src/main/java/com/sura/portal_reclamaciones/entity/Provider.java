package com.sura.portal_reclamaciones.entity;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Representa un prestador de servicios de salud.
 *
 * Las reglas R08 y R09 consultarán, respectivamente, si el NIT existe y está
 * en red, y si el prestador tiene activa una alerta de fraude.
 */
@Entity
@Table(name = "provider")
public class Provider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nit", nullable = false, unique = true, length = 40)
    private String nit;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "in_network", nullable = false)
    private boolean inNetwork = false;

    @Column(name = "fraud_alert", nullable = false)
    private boolean fraudAlert = false;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extraData = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Provider() {
    }

    @PrePersist
    protected void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (extraData == null) extraData = new HashMap<>();
    }

    @PreUpdate
    protected void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getNit() { return nit; }
    public void setNit(String nit) { this.nit = nit; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isInNetwork() { return inNetwork; }
    public void setInNetwork(boolean inNetwork) { this.inNetwork = inNetwork; }
    public boolean isFraudAlert() { return fraudAlert; }
    public void setFraudAlert(boolean fraudAlert) { this.fraudAlert = fraudAlert; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Map<String, Object> getExtraData() { return extraData; }
    public void setExtraData(Map<String, Object> extraData) { this.extraData = extraData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

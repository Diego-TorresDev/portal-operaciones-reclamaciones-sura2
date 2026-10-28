package com.sura.portal_reclamaciones.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * Representa un servicio del catálogo de la hoja 06_Servicios.
 *
 * Además de validar si existe el tipo de servicio (R10), esta entidad conserva
 * los soportes exigidos para poder revisar la regla R11.
 */
@Entity
@Table(name = "service_catalog")
public class ServiceCatalog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "service_code", nullable = false, unique = true, length = 100)
    private String serviceCode;

    @Column(name = "service_name", nullable = false, length = 200)
    private String serviceName;

    @Column(name = "supports_required", nullable = false)
    private boolean supportsRequired = false;

    @Column(name = "required_supports_description", columnDefinition = "text")
    private String requiredSupportsDescription;

    // La columna PostgreSQL es JSONB; Hibernate la convertirá a una lista JSON.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_supports", columnDefinition = "jsonb", nullable = false)
    private List<String> requiredSupports = new ArrayList<>();

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extra_data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extraData = new HashMap<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public ServiceCatalog() {
    }

    @PrePersist
    protected void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (requiredSupports == null) requiredSupports = new ArrayList<>();
        if (extraData == null) extraData = new HashMap<>();
    }

    @PreUpdate
    protected void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getServiceCode() { return serviceCode; }
    public void setServiceCode(String serviceCode) { this.serviceCode = serviceCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public boolean isSupportsRequired() { return supportsRequired; }
    public void setSupportsRequired(boolean supportsRequired) { this.supportsRequired = supportsRequired; }
    public String getRequiredSupportsDescription() { return requiredSupportsDescription; }
    public void setRequiredSupportsDescription(String requiredSupportsDescription) { this.requiredSupportsDescription = requiredSupportsDescription; }
    public List<String> getRequiredSupports() { return requiredSupports; }
    public void setRequiredSupports(List<String> requiredSupports) { this.requiredSupports = requiredSupports; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Map<String, Object> getExtraData() { return extraData; }
    public void setExtraData(Map<String, Object> extraData) { this.extraData = extraData; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

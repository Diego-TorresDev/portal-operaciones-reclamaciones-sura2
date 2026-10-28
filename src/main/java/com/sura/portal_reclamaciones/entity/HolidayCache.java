package com.sura.portal_reclamaciones.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

// Permite indicar explícitamente el tipo JDBC que corresponde a una columna.
import org.hibernate.annotations.JdbcTypeCode;

// Contiene los códigos de tipos SQL reconocidos por Hibernate.
import org.hibernate.type.SqlTypes;

/**
 * Festivo nacional recibido desde la API Nager.Date.
 *
 * Guardar los días en PostgreSQL permite calcular fechas ANS sin consultar
 * la API por cada reclamación y seguir trabajando si el servicio externo falla.
 */
@Entity
@Table(name = "holiday_cache", uniqueConstraints = {
    @UniqueConstraint(name = "uq_holiday_country_date", columnNames = {"country_code", "holiday_date"})
})
public class HolidayCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

   @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode = "CO";

    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate;

    @Column(name = "local_name", length = 200)
    private String localName;

    @Column(name = "english_name", length = 200)
    private String englishName;

    @Column(name = "api_year", nullable = false)
    private Integer apiYear;

    @Column(name = "source_url", nullable = false, columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "retrieved_at", nullable = false, updatable = false)
    private Instant retrievedAt;

    public HolidayCache() { }

    @PrePersist
    protected void beforeInsert() {
        if (countryCode == null || countryCode.isBlank()) countryCode = "CO";
        if (retrievedAt == null) retrievedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCountryCode() { return countryCode; }
    public void setCountryCode(String v) { this.countryCode = v; }
    public LocalDate getHolidayDate() { return holidayDate; }
    public void setHolidayDate(LocalDate v) { this.holidayDate = v; }
    public String getLocalName() { return localName; }
    public void setLocalName(String v) { this.localName = v; }
    public String getEnglishName() { return englishName; }
    public void setEnglishName(String v) { this.englishName = v; }
    public Integer getApiYear() { return apiYear; }
    public void setApiYear(Integer v) { this.apiYear = v; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String v) { this.sourceUrl = v; }
    public Instant getRetrievedAt() { return retrievedAt; }
}

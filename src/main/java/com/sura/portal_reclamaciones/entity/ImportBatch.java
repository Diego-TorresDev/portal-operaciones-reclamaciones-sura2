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
 * Representa un intento de cargar el archivo Excel en el portal.
 *
 * Esta tabla conserva el resultado global de la carga: quién la inició,
 * cuándo comenzó, cuántas filas leyó y si hubo errores. Los errores concretos
 * se guardan por separado en ImportRowError, para poder mostrarlos sin perder
 * el resumen de la operación.
 */
@Entity
@Table(name = "import_batch")
public class ImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    // SHA-256 permite identificar el contenido del archivo sin guardar otra copia del Excel.
   @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "file_sha256", length = 64)
    private String fileSha256;

    // Los valores permitidos están restringidos también en PostgreSQL.
    @Column(name = "status", nullable = false, length = 30)
    private String status = "RECIBIDO";

    // Guardamos el identificador del usuario para mantener el mapeo simple.
    @Column(name = "uploaded_by_user_id")
    private Long uploadedByUserId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "total_rows", nullable = false)
    private Integer totalRows = 0;

    @Column(name = "rows_read", nullable = false)
    private Integer rowsRead = 0;

    @Column(name = "rows_valid", nullable = false)
    private Integer rowsValid = 0;

    @Column(name = "rows_with_errors", nullable = false)
    private Integer rowsWithErrors = 0;

    // La columna es JSONB: aquí dejamos un resumen estructurado de la carga.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> summary = new HashMap<>();

    @Column(name = "failure_message", columnDefinition = "text")
    private String failureMessage;

    public ImportBatch() { }

    // Inicializa valores antes del primer INSERT para no depender solo de defaults SQL.
    @PrePersist
    protected void beforeInsert() {
        if (startedAt == null) startedAt = Instant.now();
        if (status == null || status.isBlank()) status = "RECIBIDO";
        if (totalRows == null) totalRows = 0;
        if (rowsRead == null) rowsRead = 0;
        if (rowsValid == null) rowsValid = 0;
        if (rowsWithErrors == null) rowsWithErrors = 0;
        if (summary == null) summary = new HashMap<>();
    }

    public Long getId() { return id; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String v) { this.originalFilename = v; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String v) { this.fileSha256 = v; }
    public String getStatus() { return status; }
    public void setStatus(String v) { this.status = v; }
    public Long getUploadedByUserId() { return uploadedByUserId; }
    public void setUploadedByUserId(Long v) { this.uploadedByUserId = v; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant v) { this.startedAt = v; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant v) { this.completedAt = v; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer v) { this.totalRows = v; }
    public Integer getRowsRead() { return rowsRead; }
    public void setRowsRead(Integer v) { this.rowsRead = v; }
    public Integer getRowsValid() { return rowsValid; }
    public void setRowsValid(Integer v) { this.rowsValid = v; }
    public Integer getRowsWithErrors() { return rowsWithErrors; }
    public void setRowsWithErrors(Integer v) { this.rowsWithErrors = v; }
    public Map<String, Object> getSummary() { return summary; }
    public void setSummary(Map<String, Object> v) { this.summary = v; }
    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String v) { this.failureMessage = v; }
}

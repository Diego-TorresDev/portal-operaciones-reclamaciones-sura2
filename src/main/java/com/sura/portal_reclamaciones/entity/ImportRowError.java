package com.sura.portal_reclamaciones.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Guarda un error o advertencia detectado durante la lectura del Excel.
 *
 * Separar los errores por fila permite informar qué hoja, fila y columna
 * necesitan atención sin mezclar esos detalles con la bitácora de seguridad.
 */
@Entity
@Table(name = "import_row_error")
public class ImportRowError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Identificador de la carga a la que pertenece este error.
    @Column(name = "import_batch_id", nullable = false)
    private Long importBatchId;

    @Column(name = "sheet_name", nullable = false, length = 100)
    private String sheetName;

    // Número de fila tal como se identifica en la hoja de Excel.
    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "column_name", length = 150)
    private String columnName;

    @Column(name = "error_code", nullable = false, length = 80)
    private String errorCode;

    // Valores admitidos: INFO, WARNING y ERROR.
    @Column(name = "severity", nullable = false, length = 15)
    private String severity = "ERROR";

    @Column(name = "error_message", nullable = false, columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public ImportRowError() { }

    @PrePersist
    protected void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
        if (severity == null || severity.isBlank()) severity = "ERROR";
    }

    public Long getId() { return id; }
    public Long getImportBatchId() { return importBatchId; }
    public void setImportBatchId(Long v) { this.importBatchId = v; }
    public String getSheetName() { return sheetName; }
    public void setSheetName(String v) { this.sheetName = v; }
    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer v) { this.rowNumber = v; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String v) { this.columnName = v; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String v) { this.errorCode = v; }
    public String getSeverity() { return severity; }
    public void setSeverity(String v) { this.severity = v; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String v) { this.errorMessage = v; }
    public Instant getCreatedAt() { return createdAt; }
}

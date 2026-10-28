package com.sura.portal_reclamaciones.entity;

import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import jakarta.persistence.*;

/**
 * Reclamación leída desde la hoja 02_Casos.
 * Conserva los valores originales y los normalizados para que el motor pueda
 * explicar sus decisiones y tratar errores de datos sin perder la fila original.
 */
@Entity
@Table(name = "claim_case")
public class ClaimCase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 
    @Column(name = "case_code", length = 50, nullable = false, unique = true)
    private String caseCode;

    // 
    @Column(name = "import_batch_id")
    private Long importBatchId;

    // 
    @Column(name = "source_sheet_name", length = 100, nullable = false)
    private String sourceSheetName = "02_Casos";

    // 
    @Column(name = "source_row_number")
    private Integer sourceRowNumber;

    // 
    @Column(name = "affiliate_name", length = 200)
    private String affiliateName;

    // Documento completo: solo debe exponerse en el detalle autorizado.
    @Column(name = "document_number", length = 100)
    private String documentNumber;

    // 
    @Column(name = "policy_number_raw", columnDefinition = "text")
    private String policyNumberRaw;

    // 
    @Column(name = "normalized_policy_number", length = 100)
    private String normalizedPolicyNumber;

    // 
    @Column(name = "resolved_policy_id")
    private Long resolvedPolicyId;

    // 
    @Column(name = "provider_nit_raw", columnDefinition = "text")
    private String providerNitRaw;

    // 
    @Column(name = "normalized_provider_nit", length = 40)
    private String normalizedProviderNit;

    // 
    @Column(name = "resolved_provider_id")
    private Long resolvedProviderId;

    // 
    @Column(name = "service_type_raw", columnDefinition = "text")
    private String serviceTypeRaw;

    // 
    @Column(name = "normalized_service_type", length = 100)
    private String normalizedServiceType;

    // 
    @Column(name = "resolved_service_type_id")
    private Long resolvedServiceTypeId;

    // 
    @Column(name = "diagnosis_code_raw", columnDefinition = "text")
    private String diagnosisCodeRaw;

    // 
    @Column(name = "normalized_diagnosis_code", length = 60)
    private String normalizedDiagnosisCode;

    // 
    @Column(name = "resolved_diagnosis_id")
    private Long resolvedDiagnosisId;

    // 
    @Column(name = "claim_amount_raw", columnDefinition = "text")
    private String claimAmountRaw;

    // 
    @Column(name = "claim_amount", precision = 16, scale = 2)
    private java.math.BigDecimal claimAmount;

    // 
    @Column(name = "service_date_raw", columnDefinition = "text")
    private String serviceDateRaw;

    // 
    @Column(name = "service_date")
    private java.time.LocalDate serviceDate;

    // 
    @Column(name = "filing_date_raw", columnDefinition = "text")
    private String filingDateRaw;

    // 
    @Column(name = "filing_date")
    private java.time.LocalDate filingDate;

    // 
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supports_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> supportsPayload = new HashMap<>();

    // 
    @Column(name = "observations", columnDefinition = "text")
    private String observations;

    // 
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> rawPayload = new HashMap<>();

    // 
    @Column(name = "current_state", length = 40, nullable = false)
    private String currentState = "RADICADO";

    // 
    @Column(name = "current_decision", length = 20)
    private String currentDecision;

    // 
    @Column(name = "current_priority", length = 10)
    private String currentPriority;

    // 
    @Column(name = "assigned_analyst_id")
    private Long assignedAnalystId;

    // 
    @Column(name = "assigned_at")
    private java.time.Instant assignedAt;

    // 
    @Column(name = "ans_deadline_date")
    private java.time.LocalDate ansDeadlineDate;

    // 
    @Column(name = "ans_semaphore", length = 20)
    private String ansSemaphore;

    // 
    @Column(name = "ans_alert", columnDefinition = "text")
    private String ansAlert;

    // 
    @Column(name = "closed_at")
    private java.time.Instant closedAt;

    // 
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    // 
    @Column(name = "current_evaluation_id")
    private Long currentEvaluationId;

    // 
    @Column(name = "created_at", nullable = false, updatable = false)
    private java.time.Instant createdAt;

    // 
    @Column(name = "updated_at", nullable = false)
    private java.time.Instant updatedAt;

    public ClaimCase() { }

    // Inicializa valores técnicos antes de insertar la fila.
    @PrePersist
    protected void beforeInsert() {
        java.time.Instant now = java.time.Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (sourceSheetName == null || sourceSheetName.isBlank()) sourceSheetName = "02_Casos";
        if (supportsPayload == null) supportsPayload = new HashMap<>();
        if (rawPayload == null) rawPayload = new HashMap<>();
        if (currentState == null) currentState = "RADICADO";
        if (version == null) version = 0L;
    }

    // Marca cuándo se actualizó por última vez este caso.
    @PreUpdate
    protected void beforeUpdate() { updatedAt = java.time.Instant.now(); }

    public Long getId() { return id; }
    public String getCaseCode() { return caseCode; }
    public void setCaseCode(String caseCode) { this.caseCode = caseCode; }
    public Long getImportBatchId() { return importBatchId; }
    public void setImportBatchId(Long importBatchId) { this.importBatchId = importBatchId; }
    public String getSourceSheetName() { return sourceSheetName; }
    public void setSourceSheetName(String sourceSheetName) { this.sourceSheetName = sourceSheetName; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer sourceRowNumber) { this.sourceRowNumber = sourceRowNumber; }
    public String getAffiliateName() { return affiliateName; }
    public void setAffiliateName(String affiliateName) { this.affiliateName = affiliateName; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String documentNumber) { this.documentNumber = documentNumber; }
    public String getPolicyNumberRaw() { return policyNumberRaw; }
    public void setPolicyNumberRaw(String policyNumberRaw) { this.policyNumberRaw = policyNumberRaw; }
    public String getNormalizedPolicyNumber() { return normalizedPolicyNumber; }
    public void setNormalizedPolicyNumber(String normalizedPolicyNumber) { this.normalizedPolicyNumber = normalizedPolicyNumber; }
    public Long getResolvedPolicyId() { return resolvedPolicyId; }
    public void setResolvedPolicyId(Long resolvedPolicyId) { this.resolvedPolicyId = resolvedPolicyId; }
    public String getProviderNitRaw() { return providerNitRaw; }
    public void setProviderNitRaw(String providerNitRaw) { this.providerNitRaw = providerNitRaw; }
    public String getNormalizedProviderNit() { return normalizedProviderNit; }
    public void setNormalizedProviderNit(String normalizedProviderNit) { this.normalizedProviderNit = normalizedProviderNit; }
    public Long getResolvedProviderId() { return resolvedProviderId; }
    public void setResolvedProviderId(Long resolvedProviderId) { this.resolvedProviderId = resolvedProviderId; }
    public String getServiceTypeRaw() { return serviceTypeRaw; }
    public void setServiceTypeRaw(String serviceTypeRaw) { this.serviceTypeRaw = serviceTypeRaw; }
    public String getNormalizedServiceType() { return normalizedServiceType; }
    public void setNormalizedServiceType(String normalizedServiceType) { this.normalizedServiceType = normalizedServiceType; }
    public Long getResolvedServiceTypeId() { return resolvedServiceTypeId; }
    public void setResolvedServiceTypeId(Long resolvedServiceTypeId) { this.resolvedServiceTypeId = resolvedServiceTypeId; }
    public String getDiagnosisCodeRaw() { return diagnosisCodeRaw; }
    public void setDiagnosisCodeRaw(String diagnosisCodeRaw) { this.diagnosisCodeRaw = diagnosisCodeRaw; }
    public String getNormalizedDiagnosisCode() { return normalizedDiagnosisCode; }
    public void setNormalizedDiagnosisCode(String normalizedDiagnosisCode) { this.normalizedDiagnosisCode = normalizedDiagnosisCode; }
    public Long getResolvedDiagnosisId() { return resolvedDiagnosisId; }
    public void setResolvedDiagnosisId(Long resolvedDiagnosisId) { this.resolvedDiagnosisId = resolvedDiagnosisId; }
    public String getClaimAmountRaw() { return claimAmountRaw; }
    public void setClaimAmountRaw(String claimAmountRaw) { this.claimAmountRaw = claimAmountRaw; }
    public java.math.BigDecimal getClaimAmount() { return claimAmount; }
    public void setClaimAmount(java.math.BigDecimal claimAmount) { this.claimAmount = claimAmount; }
    public String getServiceDateRaw() { return serviceDateRaw; }
    public void setServiceDateRaw(String serviceDateRaw) { this.serviceDateRaw = serviceDateRaw; }
    public java.time.LocalDate getServiceDate() { return serviceDate; }
    public void setServiceDate(java.time.LocalDate serviceDate) { this.serviceDate = serviceDate; }
    public String getFilingDateRaw() { return filingDateRaw; }
    public void setFilingDateRaw(String filingDateRaw) { this.filingDateRaw = filingDateRaw; }
    public java.time.LocalDate getFilingDate() { return filingDate; }
    public void setFilingDate(java.time.LocalDate filingDate) { this.filingDate = filingDate; }
    public Map<String, Object> getSupportsPayload() { return supportsPayload; }
    public void setSupportsPayload(Map<String, Object> supportsPayload) { this.supportsPayload = supportsPayload; }
    public String getObservations() { return observations; }
    public void setObservations(String observations) { this.observations = observations; }
    public Map<String, Object> getRawPayload() { return rawPayload; }
    public void setRawPayload(Map<String, Object> rawPayload) { this.rawPayload = rawPayload; }
    public String getCurrentState() { return currentState; }
    public void setCurrentState(String currentState) { this.currentState = currentState; }
    public String getCurrentDecision() { return currentDecision; }
    public void setCurrentDecision(String currentDecision) { this.currentDecision = currentDecision; }
    public String getCurrentPriority() { return currentPriority; }
    public void setCurrentPriority(String currentPriority) { this.currentPriority = currentPriority; }
    public Long getAssignedAnalystId() { return assignedAnalystId; }
    public void setAssignedAnalystId(Long assignedAnalystId) { this.assignedAnalystId = assignedAnalystId; }
    public java.time.Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(java.time.Instant assignedAt) { this.assignedAt = assignedAt; }
    public java.time.LocalDate getAnsDeadlineDate() { return ansDeadlineDate; }
    public void setAnsDeadlineDate(java.time.LocalDate ansDeadlineDate) { this.ansDeadlineDate = ansDeadlineDate; }
    public String getAnsSemaphore() { return ansSemaphore; }
    public void setAnsSemaphore(String ansSemaphore) { this.ansSemaphore = ansSemaphore; }
    public String getAnsAlert() { return ansAlert; }
    public void setAnsAlert(String ansAlert) { this.ansAlert = ansAlert; }
    public java.time.Instant getClosedAt() { return closedAt; }
    public void setClosedAt(java.time.Instant closedAt) { this.closedAt = closedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Long getCurrentEvaluationId() { return currentEvaluationId; }
    public void setCurrentEvaluationId(Long currentEvaluationId) { this.currentEvaluationId = currentEvaluationId; }
    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }
    public java.time.Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(java.time.Instant updatedAt) { this.updatedAt = updatedAt; }
}

package com.sura.portal_reclamaciones.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.CaseEvaluation;
/** Acceso al historial de evaluaciones sin sobrescribir decisiones anteriores. */
public interface CaseEvaluationRepository extends JpaRepository<CaseEvaluation, Long> {
    List<CaseEvaluation> findByClaimCaseIdOrderByEvaluationNumberAsc(Long claimCaseId);
    Optional<CaseEvaluation> findFirstByClaimCaseIdOrderByEvaluationNumberDesc(Long claimCaseId);
    Optional<CaseEvaluation> findByClaimCaseIdAndCurrentTrue(Long claimCaseId);
    long countByClaimCaseId(Long claimCaseId);
}

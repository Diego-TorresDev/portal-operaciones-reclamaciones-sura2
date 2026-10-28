package com.sura.portal_reclamaciones.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.CaseReviewDecision;

/** Permite consultar la decisión humana tomada para una reclamación. */
public interface CaseReviewDecisionRepository extends JpaRepository<CaseReviewDecision, Long> {
    Optional<CaseReviewDecision> findByClaimCaseId(Long claimCaseId);
    boolean existsByClaimCaseId(Long claimCaseId);
}

package com.sura.portal_reclamaciones.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.CaseTracking;
/** Recupera el historial de un caso en orden cronológico. */
public interface CaseTrackingRepository extends JpaRepository<CaseTracking, Long> {
    List<CaseTracking> findByClaimCaseIdOrderByEventAtAscIdAsc(Long claimCaseId);
}

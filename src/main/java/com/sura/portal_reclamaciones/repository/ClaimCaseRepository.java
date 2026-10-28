package com.sura.portal_reclamaciones.repository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.ClaimCase;
/** Consultas frecuentes de reclamaciones; los filtros más avanzados se añadirán en el servicio de bandejas. */
public interface ClaimCaseRepository extends JpaRepository<ClaimCase, Long> {
    Optional<ClaimCase> findByCaseCode(String caseCode);
    boolean existsByCaseCode(String caseCode);
    List<ClaimCase> findByCurrentStateOrderByAnsDeadlineDateAsc(String currentState);
    List<ClaimCase> findByCurrentDecisionOrderByCaseCodeAsc(String currentDecision);
}

package com.sura.portal_reclamaciones.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.CaseStateTransition;
/** Consulta las transiciones habilitadas antes de permitir un cambio de estado. */
public interface CaseStateTransitionRepository extends JpaRepository<CaseStateTransition, Long> {
    List<CaseStateTransition> findByFromStateAndActiveTrue(String fromState);
    boolean existsByFromStateAndToStateAndConditionCodeAndActiveTrue(String fromState, String toState, String conditionCode);
}

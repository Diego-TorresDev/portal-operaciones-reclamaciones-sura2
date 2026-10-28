package com.sura.portal_reclamaciones.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.CaseEvaluationRule;
import com.sura.portal_reclamaciones.entity.CaseEvaluationRuleId;
/** Consulta todos los resultados de una evaluación y permite filtrar reglas fallidas. */
public interface CaseEvaluationRuleRepository extends JpaRepository<CaseEvaluationRule, CaseEvaluationRuleId> {
    List<CaseEvaluationRule> findByCaseEvaluationIdOrderByRuleCodeAsc(Long caseEvaluationId);
    List<CaseEvaluationRule> findByCaseEvaluationIdAndPassedFalseOrderByRuleCodeAsc(Long caseEvaluationId);
}

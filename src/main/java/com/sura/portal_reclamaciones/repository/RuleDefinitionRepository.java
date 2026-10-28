package com.sura.portal_reclamaciones.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.RuleDefinition;
/** Consultas del catálogo R01-R17. */
public interface RuleDefinitionRepository extends JpaRepository<RuleDefinition, String> {
    // Mantiene el orden funcional en que se presentan las reglas.
    List<RuleDefinition> findAllByActiveTrueOrderByDisplayOrderAsc();
}

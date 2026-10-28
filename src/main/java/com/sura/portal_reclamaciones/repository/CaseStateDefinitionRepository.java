package com.sura.portal_reclamaciones.repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.CaseStateDefinition;
/** Acceso al catálogo de estados del proceso. */
public interface CaseStateDefinitionRepository extends JpaRepository<CaseStateDefinition, String> {
    List<CaseStateDefinition> findAllByOrderByDisplayOrderAsc();
}

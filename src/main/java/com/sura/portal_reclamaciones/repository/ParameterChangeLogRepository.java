package com.sura.portal_reclamaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.ParameterChangeLog;

/** Permite consultar el historial de cambios de configuración. */
public interface ParameterChangeLogRepository extends JpaRepository<ParameterChangeLog, Long> {
    List<ParameterChangeLog> findByParameterKeyOrderByChangedAtDesc(String parameterKey);
    List<ParameterChangeLog> findAllByOrderByChangedAtDesc();
}

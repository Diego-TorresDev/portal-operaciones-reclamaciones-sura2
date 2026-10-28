package com.sura.portal_reclamaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.ImportBatch;

/** Consultas para consultar el historial de cargas de Excel. */
public interface ImportBatchRepository extends JpaRepository<ImportBatch, Long> {
    // Muestra las cargas más recientes primero para facilitar el seguimiento operativo.
    List<ImportBatch> findAllByOrderByStartedAtDesc();
    List<ImportBatch> findByStatusOrderByStartedAtDesc(String status);
}

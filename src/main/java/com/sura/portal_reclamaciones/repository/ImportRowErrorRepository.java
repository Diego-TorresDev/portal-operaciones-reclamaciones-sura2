package com.sura.portal_reclamaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.ImportRowError;

/** Acceso a los errores detectados al procesar cada hoja y fila del Excel. */
public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {
    // Permite presentar los errores en el mismo orden en que aparecieron en el archivo.
    List<ImportRowError> findByImportBatchIdOrderBySheetNameAscRowNumberAsc(Long importBatchId);
    long countByImportBatchIdAndSeverity(Long importBatchId, String severity);
}

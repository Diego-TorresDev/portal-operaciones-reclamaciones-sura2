package com.sura.portal_reclamaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import com.sura.portal_reclamaciones.entity.AuditLog;

/** Consultas de la bitácora para auditoría y diagnóstico operativo. */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByActorUsernameOrderByCreatedAtDesc(String actorUsername);
    List<AuditLog> findByActionCodeOrderByCreatedAtDesc(String actionCode);
    List<AuditLog> findByResourceTypeAndResourceIdOrderByCreatedAtDesc(String resourceType, String resourceId);
    List<AuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

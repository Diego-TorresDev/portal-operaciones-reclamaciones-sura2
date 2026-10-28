package com.sura.portal_reclamaciones.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.PaymentBatch;

/** Consultas de los lotes de pago simulados. */
public interface PaymentBatchRepository extends JpaRepository<PaymentBatch, Long> {
    Optional<PaymentBatch> findByBatchCode(String batchCode);
    List<PaymentBatch> findByStatusOrderByGeneratedAtDesc(String status);
    List<PaymentBatch> findAllByOrderByGeneratedAtDesc();
}

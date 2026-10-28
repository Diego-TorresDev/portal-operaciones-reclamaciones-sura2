package com.sura.portal_reclamaciones.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.sura.portal_reclamaciones.entity.PaymentBatchItem;

/** Acceso a los casos incluidos en cada lote. */
public interface PaymentBatchItemRepository extends JpaRepository<PaymentBatchItem, Long> {
    List<PaymentBatchItem> findByPaymentBatchIdOrderByIdAsc(Long paymentBatchId);
    boolean existsByClaimCaseId(Long claimCaseId);
    long countByPaymentBatchId(Long paymentBatchId);
}

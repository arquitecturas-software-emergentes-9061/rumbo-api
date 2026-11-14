package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.repositories;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.PaymentOrderJpaEntity;
public interface SpringDataPaymentOrderRepository extends JpaRepository<PaymentOrderJpaEntity, UUID> {
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select o from PaymentOrderJpaEntity o where o.id = :id")
  Optional<PaymentOrderJpaEntity> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
  Optional<PaymentOrderJpaEntity> findByIdAndPayerUserId(UUID id, UUID payerUserId);
}

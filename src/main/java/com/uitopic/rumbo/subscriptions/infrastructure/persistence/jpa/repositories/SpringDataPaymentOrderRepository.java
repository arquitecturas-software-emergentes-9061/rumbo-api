package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.repositories;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.PaymentOrderJpaEntity;
public interface SpringDataPaymentOrderRepository extends JpaRepository<PaymentOrderJpaEntity, UUID> {
  Optional<PaymentOrderJpaEntity> findByIdAndPayerUserId(UUID id, UUID payerUserId);
}

package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.adapters;

import java.util.*;
import org.springframework.stereotype.Repository;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.repositories.PaymentOrderRepository;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.repositories.SpringDataPaymentOrderRepository;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers.PaymentOrderPersistenceMapper;
@Repository
public class PaymentOrderRepositoryImpl implements PaymentOrderRepository {
  private final SpringDataPaymentOrderRepository repository;
  public PaymentOrderRepositoryImpl(SpringDataPaymentOrderRepository repository) { this.repository = repository; }
  public PaymentOrder save(PaymentOrder order) {
    return PaymentOrderPersistenceMapper.toDomain(repository.save(PaymentOrderPersistenceMapper.toEntity(order)));
  }
  public Optional<PaymentOrder> findByIdAndPayerUserId(UUID id, UUID payerUserId) {
    return repository.findByIdAndPayerUserId(id, payerUserId).map(PaymentOrderPersistenceMapper::toDomain);
  }
}

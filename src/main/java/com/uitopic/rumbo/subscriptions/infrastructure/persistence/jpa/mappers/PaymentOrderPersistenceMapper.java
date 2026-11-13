package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.PaymentOrder;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.Money;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.PaymentOrderJpaEntity;
public final class PaymentOrderPersistenceMapper {
  private PaymentOrderPersistenceMapper() {}
  public static PaymentOrder toDomain(PaymentOrderJpaEntity e) {
    return new PaymentOrder(e.getId(), e.getPayerUserId(), e.getPlanCode(), new Money(e.getAmount(), e.getCurrency()),
      e.getStatus(), e.getProviderSessionId(), e.getCreatedAt(), e.getUpdatedAt());
  }
  public static PaymentOrderJpaEntity toEntity(PaymentOrder o) {
    var e = new PaymentOrderJpaEntity();
    e.setId(o.id()); e.setPayerUserId(o.payerUserId()); e.setPlanCode(o.planCode());
    e.setAmount(o.amount().amount()); e.setCurrency(o.amount().currency()); e.setStatus(o.status());
    e.setProviderSessionId(o.providerSessionId()); e.setCreatedAt(o.createdAt()); e.setUpdatedAt(o.updatedAt());
    return e;
  }
}

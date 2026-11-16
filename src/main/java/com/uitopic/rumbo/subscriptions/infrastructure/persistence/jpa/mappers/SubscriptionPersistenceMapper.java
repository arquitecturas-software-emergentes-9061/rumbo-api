package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers;
import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Subscription;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.SubscriptionJpaEntity;
public final class SubscriptionPersistenceMapper {
  private SubscriptionPersistenceMapper() { }
  public static Subscription toDomain(SubscriptionJpaEntity e) {
    return new Subscription(e.getId(), e.getOwnerUserId(), e.getPlanCode(), e.getStatus(), e.getCurrentPeriodStart(), e.getCurrentPeriodEnd(), e.getProviderSubscriptionId(), e.getCreatedAt(), e.getUpdatedAt());
  }
  public static SubscriptionJpaEntity toEntity(Subscription s) {
    var e = new SubscriptionJpaEntity();
    e.setId(s.id());
    e.setOwnerUserId(s.ownerUserId());
    e.setPlanCode(s.planCode());
    e.setStatus(s.status());
    e.setCurrentPeriodStart(s.currentPeriodStart());
    e.setCurrentPeriodEnd(s.currentPeriodEnd());
    e.setProviderSubscriptionId(s.providerSubscriptionId());
    e.setCreatedAt(s.createdAt());
    e.setUpdatedAt(s.updatedAt());
    return e;
  }
}

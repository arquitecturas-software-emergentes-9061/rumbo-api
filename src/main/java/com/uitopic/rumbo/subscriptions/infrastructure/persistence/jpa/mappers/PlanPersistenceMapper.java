package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.Money;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.PlanJpaEntity;

public final class PlanPersistenceMapper {
  private PlanPersistenceMapper() {}

  public static Plan toDomain(PlanJpaEntity entity) {
    return new Plan(entity.getId(), entity.getCode(), entity.getName(),
      new Money(entity.getPriceAmount(), entity.getCurrency()), entity.getBillingInterval(),
      entity.getPurchaseMode(), entity.getDisplayOrder(), entity.getActive());
  }
}

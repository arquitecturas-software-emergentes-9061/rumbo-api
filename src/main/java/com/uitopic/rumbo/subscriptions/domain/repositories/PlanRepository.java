package com.uitopic.rumbo.subscriptions.domain.repositories;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import java.util.List;
import java.util.Optional;

public interface PlanRepository {
  List<Plan> findAllActive();
  Optional<Plan> findByCode(PlanCode code);
}

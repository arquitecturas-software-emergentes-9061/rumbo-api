package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.adapters;

import com.uitopic.rumbo.subscriptions.domain.model.aggregates.Plan;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import com.uitopic.rumbo.subscriptions.domain.repositories.PlanRepository;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.mappers.PlanPersistenceMapper;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.repositories.SpringDataPlanRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public class PlanRepositoryImpl implements PlanRepository {
  private final SpringDataPlanRepository repository;

  public PlanRepositoryImpl(SpringDataPlanRepository repository) { this.repository = repository; }

  @Override
  public List<Plan> findAllActive() {
    return repository.findAllByActiveTrue().stream().map(PlanPersistenceMapper::toDomain).toList();
  }

  @Override
  public Optional<Plan> findByCode(PlanCode code) {
    return repository.findByCode(code).map(PlanPersistenceMapper::toDomain);
  }
}

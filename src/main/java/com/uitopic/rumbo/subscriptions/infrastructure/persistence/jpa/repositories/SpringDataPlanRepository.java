package com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import com.uitopic.rumbo.subscriptions.infrastructure.persistence.jpa.entities.PlanJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpringDataPlanRepository extends JpaRepository<PlanJpaEntity, UUID> {
  List<PlanJpaEntity> findAllByActiveTrue();
  Optional<PlanJpaEntity> findByCode(PlanCode code);
}

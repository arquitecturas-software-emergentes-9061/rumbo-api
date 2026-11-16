package com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.repositories;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationEventType;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.entities.NavigationEventJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataNavigationEventRepository extends JpaRepository<NavigationEventJpaEntity, UUID> {
  boolean existsByNavigationSessionIdAndEventType(UUID navigationSessionId, NavigationEventType eventType);
}

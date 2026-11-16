package com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.adapters;

import com.uitopic.rumbo.analytics.domain.model.aggregates.NavigationEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.NavigationEventType;
import com.uitopic.rumbo.analytics.domain.repositories.NavigationEventRepository;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.mappers.NavigationEventPersistenceMapper;
import com.uitopic.rumbo.analytics.infrastructure.persistence.jpa.repositories.SpringDataNavigationEventRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** PostgreSQL adapter of {@link NavigationEventRepository}. Inserts are sent in JDBC batches. */
@Repository
public class NavigationEventRepositoryImpl implements NavigationEventRepository {
  private final SpringDataNavigationEventRepository jpa;

  public NavigationEventRepositoryImpl(SpringDataNavigationEventRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public void saveAll(List<NavigationEvent> events) {
    jpa.saveAll(events.stream().map(NavigationEventPersistenceMapper::toEntity).toList());
  }

  @Override
  public boolean existsBySessionIdAndType(UUID navigationSessionId, NavigationEventType eventType) {
    return jpa.existsByNavigationSessionIdAndEventType(navigationSessionId, eventType);
  }
}

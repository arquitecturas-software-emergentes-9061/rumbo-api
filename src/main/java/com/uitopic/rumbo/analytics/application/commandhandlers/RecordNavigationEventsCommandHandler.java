package com.uitopic.rumbo.analytics.application.commandhandlers;

import com.uitopic.rumbo.analytics.application.ports.NavigationMetricsCache;
import com.uitopic.rumbo.analytics.domain.model.aggregates.NavigationEvent;
import com.uitopic.rumbo.analytics.domain.model.commands.RecordNavigationEventsCommand;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.EventMetadata;
import com.uitopic.rumbo.analytics.domain.repositories.NavigationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Records a batch of navigation facts (TS-30): creates one {@link NavigationEvent} per element, stores them in one
 * batch and, once committed, invalidates the cached metrics of every site involved, following the EventStorming
 * policy "update the consolidated metrics when new results are recorded".
 */
@Service
public class RecordNavigationEventsCommandHandler {
  public static final int MAX_BATCH_SIZE = 100;
  private final NavigationEventRepository events;
  private final NavigationMetricsCache metricsCache;
  private final Clock clock;

  public RecordNavigationEventsCommandHandler(NavigationEventRepository events, NavigationMetricsCache metricsCache,
                                              Clock clock) {
    this.events = events;
    this.metricsCache = metricsCache;
    this.clock = clock;
  }

  @Transactional
  public int handle(RecordNavigationEventsCommand command) {
    if (command.events().isEmpty()) throw new IllegalArgumentException("events cannot be empty");
    if (command.events().size() > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException("A batch has at most " + MAX_BATCH_SIZE + " events");
    }
    Instant now = clock.instant();
    List<NavigationEvent> recorded = command.events().stream()
      .map(event -> NavigationEvent.record(UUID.randomUUID(), event.navigationSessionId(), event.siteId(),
        event.floorId(), event.eventType(), event.nodeReference(), event.occurredAt(),
        new EventMetadata(event.metadata()), now))
      .toList();
    events.saveAll(recorded);
    Set<UUID> sites = recorded.stream().map(NavigationEvent::siteId).collect(Collectors.toSet());
    afterCommit(() -> metricsCache.invalidate(sites));
    return recorded.size();
  }

  private static void afterCommit(Runnable action) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      action.run();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        action.run();
      }
    });
  }
}

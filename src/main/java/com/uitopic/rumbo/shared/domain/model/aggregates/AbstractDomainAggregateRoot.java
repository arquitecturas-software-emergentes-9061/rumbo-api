package com.uitopic.rumbo.shared.domain.model.aggregates;

import com.uitopic.rumbo.shared.domain.model.events.DomainEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Framework-independent base for aggregate identity and pending domain events.
 * Events are registered by an aggregate and drained by its application layer after
 * the state transition has been persisted. No event transport is implied here.
 *
 * @param <ID> aggregate identity type
 */
public abstract class AbstractDomainAggregateRoot<ID> {
  private final List<DomainEvent> pendingEvents = new ArrayList<>();

  /** Returns the identity assigned by the owning bounded context. */
  public abstract ID id();

  /** Records an event raised by a successful domain transition. */
  protected final void registerDomainEvent(DomainEvent event) {
    pendingEvents.add(Objects.requireNonNull(event, "event is required"));
  }

  /** Returns and clears pending events, preserving their registration order. */
  public final List<DomainEvent> drainDomainEvents() {
    List<DomainEvent> events = List.copyOf(pendingEvents);
    pendingEvents.clear();
    return events;
  }
}

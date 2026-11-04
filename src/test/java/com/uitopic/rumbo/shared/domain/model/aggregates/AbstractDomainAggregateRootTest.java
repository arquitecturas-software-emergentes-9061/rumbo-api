package com.uitopic.rumbo.shared.domain.model.aggregates;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.uitopic.rumbo.shared.domain.model.events.DomainEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AbstractDomainAggregateRootTest {
  private record TestEvent(String name) implements DomainEvent {}

  private static final class TestAggregate extends AbstractDomainAggregateRoot<UUID> {
    private final UUID id = UUID.randomUUID();

    @Override public UUID id() { return id; }
    void raise(DomainEvent event) { registerDomainEvent(event); }
  }

  @Test
  void drainsEventsInOrderExactlyOnce() {
    TestAggregate aggregate = new TestAggregate();
    aggregate.raise(new TestEvent("first"));
    aggregate.raise(new TestEvent("second"));

    List<DomainEvent> drained = aggregate.drainDomainEvents();
    assertEquals(List.of(new TestEvent("first"), new TestEvent("second")), drained);
    assertTrue(aggregate.drainDomainEvents().isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> drained.add(new TestEvent("third")));
    assertThrows(NullPointerException.class, () -> aggregate.raise(null));
  }
}

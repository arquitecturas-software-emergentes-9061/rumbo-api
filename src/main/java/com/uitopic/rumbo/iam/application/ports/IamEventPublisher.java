package com.uitopic.rumbo.iam.application.ports;

import java.util.List;

/**
 * Outbound port that publishes the domain events recorded by the aggregates. Handlers call it inside their
 * transaction; listeners in other bounded contexts receive the events only after the commit.
 */
public interface IamEventPublisher {
  void publish(List<Object> events);
}

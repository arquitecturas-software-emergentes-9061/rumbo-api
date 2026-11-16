package com.uitopic.rumbo.iam.infrastructure.events;

import com.uitopic.rumbo.iam.application.ports.IamEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * In-process adapter of {@link IamEventPublisher}. Consumers in other bounded contexts listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)}, so they never see an account that was rolled back.
 * It can be replaced by a RabbitMQ publisher without touching the handlers.
 */
@Component
public class SpringIamEventPublisher implements IamEventPublisher {
  private final ApplicationEventPublisher publisher;

  public SpringIamEventPublisher(ApplicationEventPublisher publisher) {
    this.publisher = publisher;
  }

  @Override
  public void publish(List<Object> events) {
    events.forEach(publisher::publishEvent);
  }
}

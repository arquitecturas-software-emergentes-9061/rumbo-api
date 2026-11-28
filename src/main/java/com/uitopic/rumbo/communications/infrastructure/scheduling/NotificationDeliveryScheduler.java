package com.uitopic.rumbo.communications.infrastructure.scheduling;

import com.uitopic.rumbo.communications.application.commandhandlers.DeliverDueNotificationsCommandHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Runs the delivery job every {@code rumbo.communications.delivery.interval} (WI-TS35-04). Disabled with
 * {@code rumbo.communications.delivery.scheduler.enabled=false}, for example in tests that drive the job by hand.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "rumbo.communications.delivery.scheduler.enabled", havingValue = "true",
    matchIfMissing = true)
public class NotificationDeliveryScheduler {
  private static final Logger LOGGER = LoggerFactory.getLogger(NotificationDeliveryScheduler.class);

  private final DeliverDueNotificationsCommandHandler deliveries;

  public NotificationDeliveryScheduler(DeliverDueNotificationsCommandHandler deliveries) {
    this.deliveries = deliveries;
  }

  @Scheduled(fixedDelayString = "${rumbo.communications.delivery.interval:PT30S}",
      initialDelayString = "${rumbo.communications.delivery.interval:PT30S}")
  public void deliverDueNotifications() {
    try {
      var run = deliveries.handle();
      if (run.attempted() > 0) {
        LOGGER.info("Notification deliveries: {} sent, {} to retry, {} failed", run.sent(), run.retryScheduled(),
            run.failed());
      }
    } catch (RuntimeException failure) {
      LOGGER.error("The notification delivery job failed; it runs again on its next schedule", failure);
    }
  }
}

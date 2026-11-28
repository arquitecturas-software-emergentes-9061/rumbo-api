package com.uitopic.rumbo.communications.domain;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Delivery lifecycle of TS-36: PENDING, then RETRY_PENDING (1 min, 5 min) and FAILED, or SENT. */
class NotificationDeliveryLifecycleTest {
  private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");
  private final UUID admin = UUID.randomUUID();
  private final UUID site = UUID.randomUUID();
  private final UUID source = UUID.randomUUID();

  @Test
  void anInAppNotificationIsDeliveredWhenItIsCreated() {
    Notification created = Notification.create(admin, NotificationType.INCIDENT_REPORTED, NotificationChannel.IN_APP,
        "BLOCKED_PATH", site, source, NotificationPriority.HIGH, NOW);

    assertThat(created.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(created.deliveryAttempts()).isEqualTo(1);
    assertThat(created.isDue(NOW)).isFalse();
    assertThat(created.siteId()).isEqualTo(site);
    assertThat(created.sourceEventId()).isEqualTo(source);
    assertThat(created.priority()).isEqualTo(NotificationPriority.HIGH);
  }

  @Test
  void anEmailWaitsForItsFirstAttemptAndIsDueAtOnce() {
    Notification created = email();

    assertThat(created.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(created.deliveryAttempts()).isZero();
    assertThat(created.isDue(NOW)).isTrue();
  }

  @Test
  void temporaryFailuresBackOffAndTheThirdOneIsFinal() {
    Notification first = email().recordFailedAttempt(NOW);
    assertThat(first.deliveryStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
    assertThat(first.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    assertThat(first.isDue(NOW)).isFalse();
    assertThat(first.isDue(NOW.plus(Duration.ofMinutes(1)))).isTrue();

    Instant later = NOW.plus(Duration.ofMinutes(1));
    Notification second = first.recordFailedAttempt(later);
    assertThat(second.deliveryAttempts()).isEqualTo(2);
    assertThat(second.nextAttemptAt()).isEqualTo(later.plus(Duration.ofMinutes(5)));

    Notification third = second.recordFailedAttempt(later.plus(Duration.ofMinutes(5)));
    assertThat(third.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(third.deliveryAttempts()).isEqualTo(Notification.MAX_DELIVERY_ATTEMPTS);
    assertThat(third.nextAttemptAt()).isNull();
    assertThat(third.isDue(later.plus(Duration.ofHours(1)))).isFalse();
  }

  @Test
  void aPermanentFailureIsNotRetried() {
    Notification failed = email().recordPermanentFailure(NOW);

    assertThat(failed.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(failed.deliveryAttempts()).isEqualTo(1);
    assertThat(failed.nextAttemptAt()).isNull();
  }

  @Test
  void aSuccessfulRetryEndsTheDelivery() {
    Notification sent = email().recordFailedAttempt(NOW).recordDelivered(NOW.plusSeconds(60));

    assertThat(sent.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(sent.deliveryAttempts()).isEqualTo(2);
    assertThat(sent.lastAttemptAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(sent.nextAttemptAt()).isNull();
  }

  @Test
  void aFinishedDeliveryCannotBeAttemptedAgain() {
    Notification sent = email().recordDelivered(NOW);

    assertThatThrownBy(() -> sent.recordFailedAttempt(NOW)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sent.recordDelivered(NOW)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void readingDoesNotChangeTheDelivery() {
    Notification read = email().recordFailedAttempt(NOW).markRead(NOW.plusSeconds(5));

    assertThat(read.isRead()).isTrue();
    assertThat(read.deliveryStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
    assertThat(read.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
  }

  @Test
  void onlyIncidentsAndMapPublicationsAreAboutTheOperationOfASite() {
    assertThat(NotificationType.INCIDENT_REPORTED.isOperational()).isTrue();
    assertThat(NotificationType.MAP_PUBLISHED.isOperational()).isTrue();
    assertThat(NotificationType.WELCOME.isOperational()).isFalse();
  }

  private Notification email() {
    return Notification.create(admin, NotificationType.INCIDENT_REPORTED, NotificationChannel.EMAIL, "SIGNAL_LOST",
        site, source, NotificationPriority.HIGH, NOW);
  }
}

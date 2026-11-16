package com.uitopic.rumbo.communications.domain;

import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationTest {
  private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");
  private static final Instant T1 = T0.plusSeconds(60);
  private final UUID user = UUID.randomUUID();

  @Test
  void anInAppNotificationIsDeliveredAndUnreadWhenCreated() {
    Notification notification = Notification.inApp(user, NotificationType.WELCOME, null, T0);

    assertThat(notification.channel()).isEqualTo(NotificationChannel.IN_APP);
    assertThat(notification.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(notification.deliveryAttempts()).isEqualTo(1);
    assertThat(notification.lastAttemptAt()).isEqualTo(T0);
    assertThat(notification.isRead()).isFalse();
  }

  @Test
  void readingKeepsTheFirstInstant() {
    Notification read = Notification.inApp(user, NotificationType.WELCOME, null, T0).markRead(T1);

    assertThat(read.isRead()).isTrue();
    assertThat(read.readAt()).isEqualTo(T1);
    assertThat(read.markRead(T1.plusSeconds(60))).isSameAs(read);
  }

  @Test
  void aFailedAttemptSchedulesARetryUntilTheLimit() {
    Notification pending = Notification.pending(user, NotificationType.ACCOUNT_LOCKED, NotificationChannel.IN_APP, "x", T0);
    assertThat(pending.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
    assertThat(pending.deliveryAttempts()).isZero();
    assertThat(pending.lastAttemptAt()).isNull();

    Notification first = pending.recordFailedAttempt(T1);
    Notification second = first.recordFailedAttempt(T1);
    Notification third = second.recordFailedAttempt(T1);

    assertThat(first.deliveryStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
    assertThat(second.deliveryStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
    assertThat(third.deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
    assertThat(third.deliveryAttempts()).isEqualTo(Notification.MAX_DELIVERY_ATTEMPTS);
    assertThat(third.deliveryStatus().isFinal()).isTrue();
  }

  @Test
  void aRetryThatSucceedsEndsAsSent() {
    Notification pending = Notification.pending(user, NotificationType.WELCOME, NotificationChannel.IN_APP, null, T0);

    Notification sent = pending.recordFailedAttempt(T0).recordDelivered(T1);

    assertThat(sent.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(sent.deliveryAttempts()).isEqualTo(2);
    assertThat(sent.lastAttemptAt()).isEqualTo(T1);
  }

  @Test
  void aFinishedDeliveryCannotBeAttemptedAgain() {
    Notification sent = Notification.inApp(user, NotificationType.WELCOME, null, T0);
    Notification failed = Notification.pending(user, NotificationType.WELCOME, NotificationChannel.IN_APP, null, T0)
        .recordFailedAttempt(T0).recordFailedAttempt(T0).recordFailedAttempt(T0);

    assertThatThrownBy(() -> sent.recordDelivered(T1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> sent.recordFailedAttempt(T1)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> failed.recordDelivered(T1)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rejectsAnOversizedDetailAndMissingFields() {
    assertThatThrownBy(() -> Notification.inApp(user, NotificationType.WELCOME, "x".repeat(256), T0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Notification.inApp(null, NotificationType.WELCOME, null, T0))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Notification.inApp(user, null, null, T0)).isInstanceOf(NullPointerException.class);
  }
}

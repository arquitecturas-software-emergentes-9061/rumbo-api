package com.uitopic.rumbo.communications.infrastructure;

import com.uitopic.rumbo.TestcontainersConfiguration;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationSlice;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Persistence of notifications against the real Flyway schema (PostgreSQL in Testcontainers). */
@SpringBootTest
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationRepositoryImplTest {
  private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");
  @Autowired NotificationRepository notifications;
  @Autowired EntityManager entityManager;

  @Test
  void savesAndReloadsEveryField() {
    UUID user = UUID.randomUUID();
    Notification saved = notifications.save(Notification
        .pending(user, NotificationType.ACCOUNT_LOCKED, NotificationChannel.IN_APP, "2026-11-15T10:15:00Z", T0)
        .recordFailedAttempt(T0.plusSeconds(5)));
    entityManager.clear();

    Notification loaded = notifications.findByIdAndRecipient(saved.id(), user).orElseThrow();

    assertThat(loaded.type()).isEqualTo(NotificationType.ACCOUNT_LOCKED);
    assertThat(loaded.detail()).isEqualTo("2026-11-15T10:15:00Z");
    assertThat(loaded.deliveryStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
    assertThat(loaded.deliveryAttempts()).isEqualTo(1);
    assertThat(loaded.lastAttemptAt()).isEqualTo(T0.plusSeconds(5));
    assertThat(loaded.readAt()).isNull();
    // PostgreSQL rounds to microseconds while the auditing instant can carry nanoseconds on some platforms
    assertThat(Duration.between(saved.createdAt(), loaded.createdAt()).abs())
      .isLessThanOrEqualTo(Duration.of(1, ChronoUnit.MICROS));
  }

  @Test
  void aNotificationIsInvisibleToAnotherAccount() {
    Notification mine = notifications.save(Notification.inApp(UUID.randomUUID(), NotificationType.WELCOME, null, T0));

    assertThat(notifications.findByIdAndRecipient(mine.id(), UUID.randomUUID())).isEmpty();
  }

  @Test
  void pagesNewestFirstAndFiltersUnread() {
    // created_at is stamped by JPA auditing when the row is inserted, so insertion order is the order under test
    UUID user = UUID.randomUUID();
    Notification oldest = notifications.save(Notification.inApp(user, NotificationType.WELCOME, null, T0));
    Notification middle = notifications.save(Notification.inApp(user, NotificationType.PASSWORD_CHANGED, null, T0.plusSeconds(10)));
    Notification newest = notifications.save(Notification.inApp(user, NotificationType.ACCOUNT_LOCKED, "x", T0.plusSeconds(20)));
    notifications.save(Notification.inApp(UUID.randomUUID(), NotificationType.WELCOME, null, T0.plusSeconds(30)));
    notifications.save(middle.markRead(T0.plusSeconds(40)));

    NotificationSlice firstPage = notifications.findPage(user, false, 0, 2);
    NotificationSlice secondPage = notifications.findPage(user, false, 1, 2);
    NotificationSlice unread = notifications.findPage(user, true, 0, 10);

    assertThat(firstPage.items()).extracting(Notification::id).containsExactly(newest.id(), middle.id());
    assertThat(firstPage.total()).isEqualTo(3);
    assertThat(secondPage.items()).extracting(Notification::id).containsExactly(oldest.id());
    assertThat(unread.items()).extracting(Notification::id).containsExactly(newest.id(), oldest.id());
    assertThat(unread.total()).isEqualTo(2);
    assertThat(notifications.countUnread(user)).isEqualTo(2);
  }

  @Test
  void marksAllReadOnlyForTheRecipient() {
    UUID user = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    notifications.save(Notification.inApp(user, NotificationType.WELCOME, null, T0));
    notifications.save(Notification.inApp(user, NotificationType.PASSWORD_CHANGED, null, T0.plusSeconds(1)));
    notifications.save(Notification.inApp(other, NotificationType.WELCOME, null, T0));

    int updated = notifications.markAllRead(user, T0.plusSeconds(60));

    assertThat(updated).isEqualTo(2);
    assertThat(notifications.countUnread(user)).isZero();
    assertThat(notifications.countUnread(other)).isEqualTo(1);
    assertThat(notifications.markAllRead(user, T0.plusSeconds(120))).isZero();
  }
}

package com.uitopic.rumbo.communications.application;

import com.uitopic.rumbo.analytics.domain.model.events.NavigationIncidentReportedEvent;
import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import com.uitopic.rumbo.communications.application.commandhandlers.DeliverDueNotificationsCommandHandler;
import com.uitopic.rumbo.communications.application.commandhandlers.NotifySiteAdministratorsCommandHandler;
import com.uitopic.rumbo.communications.application.eventhandlers.OperationalEventsNotificationHandler;
import com.uitopic.rumbo.communications.application.ports.EmailNotificationSender;
import com.uitopic.rumbo.communications.application.ports.RecipientDirectory;
import com.uitopic.rumbo.communications.application.ports.RecipientDirectory.Contact;
import com.uitopic.rumbo.communications.application.ports.SiteAdministrators;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.commands.NotifySiteAdministratorsCommand;
import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationChannel;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationPriority;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import com.uitopic.rumbo.planning.domain.model.events.MapVersionPublishedEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** US-32 notices to the administrators of a site, and the TS-35/TS-36 delivery job. */
class OperationalNotificationHandlersTest {
  private static final Instant NOW = Instant.parse("2026-11-27T15:00:00Z");
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final UUID site = UUID.randomUUID();
  private final Contact admin = new Contact(UUID.randomUUID(), "admin@clinica.pe", "CLINIC_ADMIN", true);

  @Nested
  class NotifyingTheAdministrators {
    private final SiteAdministrators administrators = mock(SiteAdministrators.class);

    @Test
    void anIncidentReachesTheAdministratorInTheAppWithHighPriority() {
      when(administrators.of(site)).thenReturn(List.of(admin));
      UUID report = UUID.randomUUID();

      int created = handler(administrators, null).handle(incident(report));

      assertThat(created).isEqualTo(1);
      Notification saved = savedOne();
      assertThat(saved.recipientUserId()).isEqualTo(admin.userId());
      assertThat(saved.type()).isEqualTo(NotificationType.INCIDENT_REPORTED);
      assertThat(saved.channel()).isEqualTo(NotificationChannel.IN_APP);
      assertThat(saved.priority()).isEqualTo(NotificationPriority.HIGH);
      assertThat(saved.siteId()).isEqualTo(site);
      assertThat(saved.sourceEventId()).isEqualTo(report);
      assertThat(saved.deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    void withEmailEnabledTheNoticeWaitsForTheDeliveryJob() {
      when(administrators.of(site)).thenReturn(List.of(admin));

      handler(administrators, mock(EmailNotificationSender.class)).handle(incident(UUID.randomUUID()));

      Notification saved = savedOne();
      assertThat(saved.channel()).isEqualTo(NotificationChannel.EMAIL);
      assertThat(saved.deliveryStatus()).isEqualTo(DeliveryStatus.PENDING);
      assertThat(saved.isDue(NOW)).isTrue();
    }

    @Test
    void anAdministratorWithoutAnAddressStillGetsItInTheApp() {
      when(administrators.of(site)).thenReturn(List.of(new Contact(admin.userId(), " ", "CLINIC_ADMIN", true)));

      handler(administrators, mock(EmailNotificationSender.class)).handle(incident(UUID.randomUUID()));

      assertThat(savedOne().channel()).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    void theSameEventNotifiesEachAdministratorOnce() {
      UUID report = UUID.randomUUID();
      when(administrators.of(site)).thenReturn(List.of(admin));
      when(notifications.existsForSource(report, admin.userId())).thenReturn(true);

      assertThat(handler(administrators, null).handle(incident(report))).isZero();
      verify(notifications, never()).save(any());
    }

    @Test
    void disabledAccountsAndPatientsAreNeverNotified() {
      when(administrators.of(site)).thenReturn(List.of(
          new Contact(UUID.randomUUID(), "off@clinica.pe", "CLINIC_ADMIN", false),
          new Contact(UUID.randomUUID(), "patient@mail.pe", "PATIENT", true)));

      assertThat(handler(administrators, null).handle(incident(UUID.randomUUID()))).isZero();
      verify(notifications, never()).save(any());
    }

    @Test
    void aSiteWithoutAdministratorsOrWithoutTheResolverNotifiesNobody() {
      when(administrators.of(site)).thenReturn(List.of());

      assertThat(handler(administrators, null).handle(incident(UUID.randomUUID()))).isZero();
      assertThat(handler(null, null).handle(incident(UUID.randomUUID()))).isZero();
      verify(notifications, never()).save(any());
    }

    @Test
    void aConcurrentDuplicateIsReportedAsSuch() {
      when(administrators.of(site)).thenReturn(List.of(admin));
      doThrow(new DataIntegrityViolationException("ux_notifications_source")).when(notifications).save(any());

      assertThatThrownBy(() -> handler(administrators, null).handle(incident(UUID.randomUUID())))
          .isInstanceOf(NotifySiteAdministratorsCommandHandler.DuplicateNotificationException.class);
    }

    @Test
    void onlyOperationalTypesCanBeSentToTheAdministratorsOfASite() {
      assertThatThrownBy(() -> new NotifySiteAdministratorsCommand(site, NotificationType.WELCOME, null,
          UUID.randomUUID(), NotificationPriority.NORMAL)).isInstanceOf(IllegalArgumentException.class);
    }

    private NotifySiteAdministratorsCommand incident(UUID report) {
      return new NotifySiteAdministratorsCommand(site, NotificationType.INCIDENT_REPORTED, "BLOCKED_PATH", report,
          NotificationPriority.HIGH);
    }
  }

  @Nested
  class ListeningToEvents {
    private final NotifySiteAdministratorsCommandHandler commands = mock(NotifySiteAdministratorsCommandHandler.class);
    private final OperationalEventsNotificationHandler listener = new OperationalEventsNotificationHandler(commands);

    @Test
    void anIncidentReportIsAHighPriorityNoticeKeyedByTheReport() {
      UUID report = UUID.randomUUID();
      listener.on(new NavigationIncidentReportedEvent(report, UUID.randomUUID(), site, null, null,
          IncidentCategory.SIGNAL_LOST, null, null, NOW));

      ArgumentCaptor<NotifySiteAdministratorsCommand> sent = ArgumentCaptor.forClass(NotifySiteAdministratorsCommand.class);
      verify(commands).handle(sent.capture());
      assertThat(sent.getValue()).isEqualTo(new NotifySiteAdministratorsCommand(site,
          NotificationType.INCIDENT_REPORTED, "SIGNAL_LOST", report, NotificationPriority.HIGH));
    }

    @Test
    void aMapPublicationIsANormalNoticeKeyedByTheVersion() {
      UUID version = UUID.randomUUID();
      listener.on(new MapVersionPublishedEvent(site, UUID.randomUUID(), version, NOW));

      ArgumentCaptor<NotifySiteAdministratorsCommand> sent = ArgumentCaptor.forClass(NotifySiteAdministratorsCommand.class);
      verify(commands).handle(sent.capture());
      assertThat(sent.getValue().type()).isEqualTo(NotificationType.MAP_PUBLISHED);
      assertThat(sent.getValue().sourceEventId()).isEqualTo(version);
      assertThat(sent.getValue().priority()).isEqualTo(NotificationPriority.NORMAL);
    }

    @Test
    void aFailureNeverReachesThePublisherOfTheEvent() {
      when(commands.handle(any())).thenThrow(new IllegalStateException("database down"));

      assertThatCode(() -> listener.on(new MapVersionPublishedEvent(site, UUID.randomUUID(), UUID.randomUUID(), NOW)))
          .doesNotThrowAnyException();
    }
  }

  @Nested
  class DeliveringDueNotifications {
    private final RecipientDirectory directory = mock(RecipientDirectory.class);
    private final FakeSender sender = new FakeSender();

    @Test
    void anAcceptedEmailIsSentWithTheRenderedTemplate() {
      Notification due = dueEmail();
      when(notifications.lockDueDeliveries(NOW, DeliverDueNotificationsCommandHandler.BATCH_SIZE))
          .thenReturn(List.of(due));
      when(directory.contactOf(admin.userId())).thenReturn(Optional.of(admin));

      var run = job(sender).handle();

      assertThat(run).isEqualTo(new DeliverDueNotificationsCommandHandler.DeliveryRun(1, 0, 0));
      assertThat(savedOne().deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
      assertThat(sender.sent).singleElement().satisfies(mail -> {
        assertThat(mail.to()).isEqualTo("admin@clinica.pe");
        assertThat(mail.subject()).isEqualTo("Rumbo — Nuevo reporte en tu sede");
        assertThat(mail.body()).contains("un paso bloqueado");
      });
    }

    @Test
    void aServerThatIsDownSchedulesARetryAfterOneMinute() {
      when(notifications.lockDueDeliveries(any(), anyInt())).thenReturn(List.of(dueEmail()));
      when(directory.contactOf(admin.userId())).thenReturn(Optional.of(admin));
      sender.failNext(true);

      var run = job(sender).handle();

      assertThat(run.retryScheduled()).isEqualTo(1);
      Notification saved = savedOne();
      assertThat(saved.deliveryStatus()).isEqualTo(DeliveryStatus.RETRY_PENDING);
      assertThat(saved.nextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void theLastFailedAttemptLeavesItFailed() {
      Notification twiceFailed = dueEmail().recordFailedAttempt(NOW.minusSeconds(400))
          .recordFailedAttempt(NOW.minusSeconds(300));
      when(notifications.lockDueDeliveries(any(), anyInt())).thenReturn(List.of(twiceFailed));
      when(directory.contactOf(admin.userId())).thenReturn(Optional.of(admin));
      sender.failNext(true);

      assertThat(job(sender).handle().failed()).isEqualTo(1);
      assertThat(savedOne().deliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
      assertThat(savedOne().deliveryAttempts()).isEqualTo(Notification.MAX_DELIVERY_ATTEMPTS);
    }

    @Test
    void aRejectedAddressFailsAtOnce() {
      when(notifications.lockDueDeliveries(any(), anyInt())).thenReturn(List.of(dueEmail()));
      when(directory.contactOf(admin.userId())).thenReturn(Optional.of(admin));
      sender.failNext(false);

      assertThat(job(sender).handle().failed()).isEqualTo(1);
      assertThat(savedOne().nextAttemptAt()).isNull();
    }

    @Test
    void aDisabledOrMissingRecipientFailsWithoutWriting() {
      Notification first = dueEmail();
      Notification second = dueEmail();
      when(notifications.lockDueDeliveries(any(), anyInt())).thenReturn(List.of(first, second));
      when(directory.contactOf(admin.userId()))
          .thenReturn(Optional.of(new Contact(admin.userId(), admin.email(), "CLINIC_ADMIN", false)))
          .thenReturn(Optional.empty());

      assertThat(job(sender).handle()).isEqualTo(new DeliverDueNotificationsCommandHandler.DeliveryRun(0, 0, 2));
      assertThat(sender.sent).isEmpty();
    }

    @Test
    void withoutASenderTheAttemptCountsAsTemporary() {
      when(notifications.lockDueDeliveries(any(), anyInt())).thenReturn(List.of(dueEmail()));

      assertThat(job(null).handle().retryScheduled()).isEqualTo(1);
      verify(notifications, times(1)).save(any());
    }

    private DeliverDueNotificationsCommandHandler job(EmailNotificationSender email) {
      return new DeliverDueNotificationsCommandHandler(notifications, provider(directory), provider(email), clock);
    }

    private Notification dueEmail() {
      return Notification.create(admin.userId(), NotificationType.INCIDENT_REPORTED, NotificationChannel.EMAIL,
          "BLOCKED_PATH", site, UUID.randomUUID(), NotificationPriority.HIGH, NOW.minusSeconds(30));
    }
  }

  private NotifySiteAdministratorsCommandHandler handler(SiteAdministrators administrators,
                                                         EmailNotificationSender email) {
    return new NotifySiteAdministratorsCommandHandler(notifications, provider(administrators), provider(email), clock);
  }

  private Notification savedOne() {
    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications, times(1)).save(saved.capture());
    return saved.getValue();
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> provider(T bean) {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(bean);
    return provider;
  }

  private record Mail(String to, String subject, String body) { }

  private static final class FakeSender implements EmailNotificationSender {
    private final List<Mail> sent = new ArrayList<>();
    private Boolean nextFailureTemporary;

    void failNext(boolean temporary) {
      nextFailureTemporary = temporary;
    }

    @Override
    public void send(String to, String subject, String body) {
      if (nextFailureTemporary != null) {
        boolean temporary = nextFailureTemporary;
        nextFailureTemporary = null;
        throw new DeliveryFailure("rejected", temporary, null);
      }
      sent.add(new Mail(to, subject, body));
    }
  }
}

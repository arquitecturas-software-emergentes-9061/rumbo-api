package com.uitopic.rumbo.communications.application;

import com.uitopic.rumbo.communications.application.commandhandlers.CreateNotificationCommandHandler;
import com.uitopic.rumbo.communications.application.commandhandlers.MarkAllNotificationsReadCommandHandler;
import com.uitopic.rumbo.communications.application.commandhandlers.MarkNotificationReadCommandHandler;
import com.uitopic.rumbo.communications.application.queryhandlers.GetMyNotificationQueryHandler;
import com.uitopic.rumbo.communications.application.queryhandlers.ListMyNotificationsQueryHandler;
import com.uitopic.rumbo.communications.domain.exceptions.NotificationNotFoundException;
import com.uitopic.rumbo.communications.domain.model.aggregates.Notification;
import com.uitopic.rumbo.communications.domain.model.commands.CreateNotificationCommand;
import com.uitopic.rumbo.communications.domain.model.commands.MarkAllNotificationsReadCommand;
import com.uitopic.rumbo.communications.domain.model.commands.MarkNotificationReadCommand;
import com.uitopic.rumbo.communications.domain.model.queries.GetMyNotificationQuery;
import com.uitopic.rumbo.communications.domain.model.queries.ListMyNotificationsQuery;
import com.uitopic.rumbo.communications.domain.model.valueobjects.DeliveryStatus;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationFilter;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationListing;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationSlice;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.communications.domain.repositories.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationHandlersTest {
  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
  private final UUID user = UUID.randomUUID();

  @Test
  void createsADeliveredInAppNotificationForTheRecipient() {
    new CreateNotificationCommandHandler(notifications, clock)
        .handle(new CreateNotificationCommand(user, NotificationType.SUBSCRIPTION_ACTIVATED, "PROFESSIONAL"));

    ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
    verify(notifications).save(saved.capture());
    assertThat(saved.getValue().recipientUserId()).isEqualTo(user);
    assertThat(saved.getValue().type()).isEqualTo(NotificationType.SUBSCRIPTION_ACTIVATED);
    assertThat(saved.getValue().detail()).isEqualTo("PROFESSIONAL");
    assertThat(saved.getValue().deliveryStatus()).isEqualTo(DeliveryStatus.SENT);
    assertThat(saved.getValue().createdAt()).isEqualTo(NOW);
  }

  @Test
  void listsOnlyTheCallersNotificationsWithTheUnreadCounter() {
    Notification one = Notification.inApp(user, NotificationType.WELCOME, null, NOW);
    when(notifications.findPage(NotificationFilter.of(user, true), 2, 10)).thenReturn(new NotificationSlice(List.of(one), 21));
    when(notifications.countUnread(user)).thenReturn(7L);

    NotificationListing listing = new ListMyNotificationsQueryHandler(notifications)
        .handle(ListMyNotificationsQuery.of(user, true, 2, 10));

    assertThat(listing.items()).containsExactly(one);
    assertThat(listing.total()).isEqualTo(21);
    assertThat(listing.unread()).isEqualTo(7);
    assertThat(listing.page()).isEqualTo(2);
    assertThat(listing.size()).isEqualTo(10);
  }

  @Test
  void bringsOutOfRangePagingBackIntoRange() {
    assertThat(ListMyNotificationsQuery.of(user, false, -3, 0)).isEqualTo(new ListMyNotificationsQuery(user, false, 0, 1));
    assertThat(ListMyNotificationsQuery.of(user, false, 1, 5000).size()).isEqualTo(ListMyNotificationsQuery.MAX_SIZE);
  }

  @Test
  void anotherAccountsNotificationAnswersNotFound() {
    UUID id = UUID.randomUUID();
    when(notifications.findByIdAndRecipient(id, user)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> new GetMyNotificationQueryHandler(notifications).handle(new GetMyNotificationQuery(user, id)))
        .isInstanceOf(NotificationNotFoundException.class);
    assertThatThrownBy(() -> new MarkNotificationReadCommandHandler(notifications, clock)
        .handle(new MarkNotificationReadCommand(user, id))).isInstanceOf(NotificationNotFoundException.class);
    verify(notifications, never()).save(any());
  }

  @Test
  void marksOneNotificationReadOnce() {
    Notification unread = Notification.inApp(user, NotificationType.WELCOME, null, NOW.minusSeconds(60));
    when(notifications.findByIdAndRecipient(unread.id(), user)).thenReturn(Optional.of(unread));
    when(notifications.save(any())).thenAnswer(call -> call.getArgument(0));
    MarkNotificationReadCommandHandler handler = new MarkNotificationReadCommandHandler(notifications, clock);

    Notification read = handler.handle(new MarkNotificationReadCommand(user, unread.id()));

    assertThat(read.readAt()).isEqualTo(NOW);

    when(notifications.findByIdAndRecipient(unread.id(), user)).thenReturn(Optional.of(read));
    assertThat(handler.handle(new MarkNotificationReadCommand(user, unread.id()))).isSameAs(read);
    verify(notifications).save(any());
  }

  @Test
  void marksEveryUnreadNotificationOfTheCaller() {
    when(notifications.markAllRead(user, NOW)).thenReturn(4);

    assertThat(new MarkAllNotificationsReadCommandHandler(notifications, clock)
        .handle(new MarkAllNotificationsReadCommand(user))).isEqualTo(4);
  }
}

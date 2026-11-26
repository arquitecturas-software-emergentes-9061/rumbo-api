package com.uitopic.rumbo.communications.application;

import com.uitopic.rumbo.communications.application.commandhandlers.CreateNotificationCommandHandler;
import com.uitopic.rumbo.communications.application.eventhandlers.AccountEventsNotificationHandler;
import com.uitopic.rumbo.communications.application.eventhandlers.SubscriptionEventsNotificationHandler;
import com.uitopic.rumbo.communications.domain.model.commands.CreateNotificationCommand;
import com.uitopic.rumbo.communications.domain.model.valueobjects.NotificationType;
import com.uitopic.rumbo.iam.domain.model.events.UserAccountLockedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserPasswordChangedEvent;
import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import com.uitopic.rumbo.subscriptions.domain.model.events.SubscriptionActivatedEvent;
import com.uitopic.rumbo.subscriptions.domain.model.valueobjects.PlanCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class NotificationEventHandlersTest {
  private final CreateNotificationCommandHandler commands = mock(CreateNotificationCommandHandler.class);
  private final AccountEventsNotificationHandler account = new AccountEventsNotificationHandler(commands);
  private final SubscriptionEventsNotificationHandler subscription = new SubscriptionEventsNotificationHandler(commands);
  private final UUID user = UUID.randomUUID();
  private final Instant now = Instant.now();

  @Test
  void aRegistrationWelcomesTheNewAccount() {
    account.on(new UserRegisteredEvent(user, "ana@example.com", "PATIENT", "Ana Torres", null, now));

    verify(commands).handle(new CreateNotificationCommand(user, NotificationType.WELCOME, null));
  }

  @Test
  void aLockWarnsTheOwnerWithTheUnlockInstant() {
    Instant until = now.plusSeconds(900);

    account.on(new UserAccountLockedEvent(user, until, now));

    verify(commands).handle(new CreateNotificationCommand(user, NotificationType.ACCOUNT_LOCKED, until.toString()));
  }

  @Test
  void aPasswordChangeWarnsTheOwner() {
    account.on(new UserPasswordChangedEvent(user, now));

    verify(commands).handle(new CreateNotificationCommand(user, NotificationType.PASSWORD_CHANGED, null));
  }

  @Test
  void anActivatedSubscriptionNotifiesItsOwnerWithThePlan() {
    subscription.on(new SubscriptionActivatedEvent(user, PlanCode.PROFESSIONAL, now, now.plusSeconds(3600)));

    verify(commands).handle(new CreateNotificationCommand(user, NotificationType.SUBSCRIPTION_ACTIVATED, "PROFESSIONAL"));
  }

  @Test
  void aFailureIsLoggedAndNeverReachesTheCommittedEvent(CapturedOutput output) {
    doThrow(new IllegalStateException("database down")).when(commands).handle(any());

    assertThatCode(() -> account.on(new UserPasswordChangedEvent(user, now))).doesNotThrowAnyException();
    assertThatCode(() -> subscription.on(new SubscriptionActivatedEvent(user, PlanCode.BASIC, now, now)))
        .doesNotThrowAnyException();

    assertThat(output).contains("Could not create PASSWORD_CHANGED notification for user " + user);
    assertThat(output).contains("Could not create subscription notification for user " + user);
  }

  @Test
  void everyListenerRunsOnlyAfterTheCommit() {
    long listeners = 0;
    for (Class<?> type : new Class<?>[] {AccountEventsNotificationHandler.class, SubscriptionEventsNotificationHandler.class}) {
      for (Method method : Arrays.stream(type.getDeclaredMethods()).filter(m -> m.getName().equals("on")).toList()) {
        TransactionalEventListener listener = method.getAnnotation(TransactionalEventListener.class);
        assertThat(listener).as(method.toString()).isNotNull();
        assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        listeners++;
      }
    }
    assertThat(listeners).isEqualTo(4);
  }
}

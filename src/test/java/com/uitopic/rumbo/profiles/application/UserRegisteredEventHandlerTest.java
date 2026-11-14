package com.uitopic.rumbo.profiles.application;

import com.uitopic.rumbo.iam.domain.model.events.UserRegisteredEvent;
import com.uitopic.rumbo.profiles.application.commandhandlers.InitializeProfileCommandHandler;
import com.uitopic.rumbo.profiles.application.eventhandlers.UserRegisteredEventHandler;
import com.uitopic.rumbo.profiles.domain.model.commands.InitializeProfileCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(OutputCaptureExtension.class)
class UserRegisteredEventHandlerTest {
  private final InitializeProfileCommandHandler commands = mock(InitializeProfileCommandHandler.class);
  private final UserRegisteredEventHandler handler = new UserRegisteredEventHandler(commands);

  @ParameterizedTest
  @ValueSource(strings = {"PATIENT", "CLINIC_ADMIN"})
  void translatesOnlyTheProfileFields(String role) {
    UUID userId = UUID.randomUUID();

    handler.on(new UserRegisteredEvent(userId, "ana@example.com", role, "Ana Torres", "Ignored clinic", Instant.now()));

    verify(commands).handle(new InitializeProfileCommand(userId, "Ana Torres", role));
    verifyNoMoreInteractions(commands);
  }

  @Test
  void logsAFailureWithoutPropagatingIt(CapturedOutput output) {
    UUID userId = UUID.randomUUID();
    doThrow(new IllegalStateException("profile storage failed")).when(commands).handle(any());

    assertThatCode(() -> handler.on(
      new UserRegisteredEvent(userId, "ana@example.com", "PATIENT", "Ana Torres", null, Instant.now())))
      .doesNotThrowAnyException();

    assertThat(output.getAll()).contains("Could not initialize profile for user " + userId, "profile storage failed");
  }

  @Test
  void runsAfterTheRegistrationCommitsWithoutATransactionOfItsOwn() throws NoSuchMethodException {
    Method on = UserRegisteredEventHandler.class.getMethod("on", UserRegisteredEvent.class);

    assertThat(on.getAnnotation(TransactionalEventListener.class).phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    assertThat(on.getAnnotation(TransactionalEventListener.class).fallbackExecution()).isFalse();
    assertThat(on.getAnnotation(Transactional.class)).isNull();
  }
}

package com.uitopic.rumbo.profiles.application;

import com.uitopic.rumbo.profiles.application.commandhandlers.InitializeProfileCommandHandler;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.InitializeProfileCommand;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitializeProfileCommandHandlerTest {
  private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

  private final ProfileRepository profiles = mock(ProfileRepository.class);
  private final InitializeProfileCommandHandler handler =
    new InitializeProfileCommandHandler(profiles, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void createsTheInitialProfile() {
    UUID userId = UUID.randomUUID();

    handler.handle(new InitializeProfileCommand(userId, "Ana Torres", "PATIENT"));

    ArgumentCaptor<Profile> saved = ArgumentCaptor.forClass(Profile.class);
    verify(profiles).save(saved.capture());
    Profile profile = saved.getValue();
    assertThat(profile.userId()).isEqualTo(userId);
    assertThat(profile.fullName().value()).isEqualTo("Ana Torres");
    assertThat(profile.role()).isEqualTo("PATIENT");
    assertThat(profile.createdAt()).isEqualTo(NOW);
    assertThat(profile.phoneNumber()).isNull();
    assertThat(profile.image()).isNull();
  }

  @Test
  void leavesAnExistingProfileUnchanged() {
    UUID userId = UUID.randomUUID();
    when(profiles.existsByUserId(userId)).thenReturn(true);

    handler.handle(new InitializeProfileCommand(userId, "Ana Torres", "PATIENT"));

    verify(profiles, never()).save(any());
  }

  @Test
  void writesInItsOwnTransaction() throws NoSuchMethodException {
    Transactional transactional = InitializeProfileCommandHandler.class
      .getMethod("handle", InitializeProfileCommand.class).getAnnotation(Transactional.class);

    assertThat(transactional).isNotNull();
    assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
  }
}

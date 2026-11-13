package com.uitopic.rumbo.profiles.application;

import com.uitopic.rumbo.profiles.application.queryhandlers.GetMyProfileQueryHandler;
import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.queries.GetMyProfileQuery;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GetMyProfileQueryHandlerTest {
  private final ProfileRepository profiles = mock(ProfileRepository.class);
  private final GetMyProfileQueryHandler handler = new GetMyProfileQueryHandler(profiles);

  @Test void returnsTheUsersProfile() {
    UUID userId = UUID.randomUUID();
    Profile profile = Profile.initialize(userId, "Ana Torres", "PATIENT", Instant.now());
    when(profiles.findByUserId(userId)).thenReturn(Optional.of(profile));

    assertThat(handler.handle(new GetMyProfileQuery(userId))).isSameAs(profile);
    verify(profiles).findByUserId(userId);
    verifyNoMoreInteractions(profiles);
  }

  @Test void rejectsMissingProfile() {
    UUID userId = UUID.randomUUID();
    when(profiles.findByUserId(userId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> handler.handle(new GetMyProfileQuery(userId)))
        .isInstanceOf(ProfileNotFoundException.class);
    verify(profiles).findByUserId(userId);
    verifyNoMoreInteractions(profiles);
  }

  @Test void requiresUserId() {
    assertThatNullPointerException().isThrownBy(() -> new GetMyProfileQuery(null));
  }
}

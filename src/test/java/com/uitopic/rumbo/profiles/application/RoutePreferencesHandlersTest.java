package com.uitopic.rumbo.profiles.application;

import com.uitopic.rumbo.profiles.application.commandhandlers.UpdateRoutePreferencesCommandHandler;
import com.uitopic.rumbo.profiles.application.queryhandlers.GetMyRoutePreferencesQueryHandler;
import com.uitopic.rumbo.profiles.domain.exceptions.ProfileNotFoundException;
import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.commands.UpdateRoutePreferencesCommand;
import com.uitopic.rumbo.profiles.domain.model.queries.GetMyRoutePreferencesQuery;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.RoutePreferences;
import com.uitopic.rumbo.profiles.domain.repositories.ProfileRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoutePreferencesHandlersTest {
  private final ProfileRepository profiles = mock(ProfileRepository.class);
  private final Instant now = Instant.parse("2026-11-12T15:00:00Z");
  private final UpdateRoutePreferencesCommandHandler update =
      new UpdateRoutePreferencesCommandHandler(profiles, Clock.fixed(now, ZoneOffset.UTC));
  private final GetMyRoutePreferencesQueryHandler get = new GetMyRoutePreferencesQueryHandler(profiles);

  @Test void missingProfileIsRejectedByBothHandlers() {
    UUID userId = UUID.randomUUID();
    when(profiles.findByUserId(userId)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> get.handle(new GetMyRoutePreferencesQuery(userId)))
        .isInstanceOf(ProfileNotFoundException.class);
    assertThatThrownBy(() -> update.handle(new UpdateRoutePreferencesCommand(userId, true, false, true)))
        .isInstanceOf(ProfileNotFoundException.class);
    verify(profiles, never()).save(any());
  }

  @Test void readsWithoutWritingAndSavesWithClockTimestamp() {
    UUID userId = UUID.randomUUID();
    Profile original = Profile.initialize(userId, "Ana", "PATIENT", now.minusSeconds(10));
    when(profiles.findByUserId(userId)).thenReturn(Optional.of(original));
    assertThat(get.handle(new GetMyRoutePreferencesQuery(userId))).isSameAs(original);
    verify(profiles, never()).save(any());
    when(profiles.save(any())).thenAnswer(call -> call.getArgument(0));
    Profile result = update.handle(new UpdateRoutePreferencesCommand(userId, true, false, true));
    assertThat(result.preferences()).isEqualTo(new RoutePreferences(true, false, true));
    assertThat(result.preferencesUpdatedAt()).isEqualTo(now);
    assertThat(result.updatedAt()).isEqualTo(now);
    verify(profiles).save(result);
  }
}

package com.uitopic.rumbo.profiles.domain;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ProfileTest {
  private static final Instant NOW = Instant.parse("2026-11-12T12:00:00Z");

  @ParameterizedTest
  @ValueSource(strings = {"PATIENT", "CLINIC_ADMIN"})
  void initializesWithEventValuesAndNoPhoneOrImage(String role) {
    UUID userId = UUID.randomUUID();

    Profile profile = Profile.initialize(userId, "Ana Torres", role, NOW);

    assertThat(profile.id()).isNotNull();
    assertThat(profile.userId()).isEqualTo(userId);
    assertThat(profile.fullName().value()).isEqualTo("Ana Torres");
    assertThat(profile.role()).isEqualTo(role);
    assertThat(profile.phoneNumber()).isNull();
    assertThat(profile.image()).isNull();
    assertThat(profile.createdAt()).isEqualTo(NOW);
    assertThat(profile.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void rejectsEmptyName() {
    assertThatIllegalArgumentException().isThrownBy(() -> Profile.initialize(UUID.randomUUID(), "  ", "PATIENT", NOW));
  }

  @Test
  void rejectsMissingUserId() {
    assertThatNullPointerException().isThrownBy(() -> Profile.initialize(null, "Ana Torres", "PATIENT", NOW));
  }

  @Test
  void rejectsUnknownRole() {
    assertThatIllegalArgumentException().isThrownBy(() -> Profile.initialize(UUID.randomUUID(), "Ana", "GUEST", NOW));
  }
}

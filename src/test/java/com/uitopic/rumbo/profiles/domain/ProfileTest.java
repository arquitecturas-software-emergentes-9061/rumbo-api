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
  @Test
  void immutableChangesPreserveIdentityAndCreationTime() {
    Profile original = Profile.initialize(UUID.randomUUID(), "Ana", "PATIENT", NOW);
    Instant later = NOW.plusSeconds(10);
    var name = new com.uitopic.rumbo.profiles.domain.model.valueobjects.FullName("  Maria  ");
    var phone = new com.uitopic.rumbo.profiles.domain.model.valueobjects.PhoneNumber("+51 123456");
    var image = new com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImage("https://example.com/image", "key");
    Profile updated = original.updatePersonalData(name, phone, later);
    Profile replaced = updated.replaceImage(image, later);
    Profile removed = replaced.removeImage(later);
    for (Profile changed : java.util.List.of(updated, replaced, removed)) {
      assertThat(changed.id()).isEqualTo(original.id());
      assertThat(changed.userId()).isEqualTo(original.userId());
      assertThat(changed.createdAt()).isEqualTo(NOW);
      assertThat(changed.updatedAt()).isEqualTo(later);
    }
    assertThat(updated.fullName().value()).isEqualTo("Maria");
    assertThat(updated.phoneNumber()).isEqualTo(phone);
    assertThat(replaced.image()).isEqualTo(image);
    assertThat(removed.image()).isNull();
    assertThat(original.image()).isNull();
  }
}

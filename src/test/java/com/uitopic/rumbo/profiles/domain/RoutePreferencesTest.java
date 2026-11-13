package com.uitopic.rumbo.profiles.domain;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RoutePreferencesTest {
  @Test void defaultsAreFalseAndValuesHaveEquality() {
    assertThat(RoutePreferences.DEFAULTS).isEqualTo(new RoutePreferences(false, false, false));
    assertThat(new RoutePreferences(true, false, true))
        .isNotEqualTo(RoutePreferences.DEFAULTS);
  }

  @Test void updatesBothTimestampsAndPreservesOtherData() {
    Instant now = Instant.parse("2026-11-12T15:00:00Z");
    Profile original = Profile.initialize(UUID.randomUUID(), "Ana", "PATIENT", now);
    assertThat(original.preferences()).isEqualTo(RoutePreferences.DEFAULTS);
    assertThat(original.preferencesUpdatedAt()).isNull();
    var preferences = new RoutePreferences(true, false, true);
    Profile updated = original.updatePreferences(preferences, now.plusSeconds(10));
    assertThat(updated.preferences()).isEqualTo(preferences);
    assertThat(updated.preferencesUpdatedAt()).isEqualTo(now.plusSeconds(10));
    assertThat(updated.updatedAt()).isEqualTo(updated.preferencesUpdatedAt());
    assertThat(updated.id()).isEqualTo(original.id());
    assertThat(updated.userId()).isEqualTo(original.userId());
    assertThat(updated.createdAt()).isEqualTo(now);
    assertThat(updated.fullName()).isEqualTo(original.fullName());
    assertThat(updated.role()).isEqualTo(original.role());
    assertThat(original.preferencesUpdatedAt()).isNull();
    Profile again = updated.updatePreferences(RoutePreferences.DEFAULTS, now.plusSeconds(20));
    assertThat(again.preferencesUpdatedAt()).isEqualTo(now.plusSeconds(20));
    assertThat(again.updatedAt()).isEqualTo(now.plusSeconds(20));
    var image = new ProfileImage("https://example.com/image", "key");
    Profile personal = updated.updatePersonalData(new FullName("Maria"), new PhoneNumber("+51 123456"), now.plusSeconds(30));
    for (Profile changed : java.util.List.of(personal, personal.replaceImage(image, now.plusSeconds(30)),
        personal.removeImage(now.plusSeconds(30)))) {
      assertThat(changed.preferences()).isEqualTo(preferences);
      assertThat(changed.preferencesUpdatedAt()).isEqualTo(updated.preferencesUpdatedAt());
    }
    assertThatNullPointerException().isThrownBy(() -> original.updatePreferences(null, now));
    assertThatNullPointerException().isThrownBy(() -> original.updatePreferences(preferences, null));
  }
}

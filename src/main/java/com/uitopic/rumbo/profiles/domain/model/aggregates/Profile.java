package com.uitopic.rumbo.profiles.domain.model.aggregates;

import com.uitopic.rumbo.profiles.domain.model.valueobjects.FullName;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.PhoneNumber;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImage;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.RoutePreferences;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Personal data of an account, owned by Profiles and Preferences. The account is referenced only by {@code userId};
 * {@code role} is a snapshot received from IAM at registration.
 *
 * @param id identifier of the profile
 * @param userId identifier of the IAM account it belongs to
 * @param fullName name typed in the sign-up form
 * @param role {@code CLINIC_ADMIN} or {@code PATIENT}
 * @param phoneNumber optional phone; {@code null} until the user adds one
 * @param image optional profile image; {@code null} until the user uploads one
 * @param createdAt creation instant
 * @param updatedAt last modification instant
 * @param preferences stored route preferences
 * @param preferencesUpdatedAt last preference update, or null before the first configuration
 */
public record Profile(UUID id, UUID userId, FullName fullName, String role, PhoneNumber phoneNumber,
                      ProfileImage image, Instant createdAt, Instant updatedAt,
                      RoutePreferences preferences, Instant preferencesUpdatedAt) {
  private static final Set<String> ROLES = Set.of("CLINIC_ADMIN", "PATIENT");

  public Profile {
    Objects.requireNonNull(id, "id is required");
    Objects.requireNonNull(userId, "userId is required");
    Objects.requireNonNull(fullName, "fullName is required");
    Objects.requireNonNull(createdAt, "createdAt is required");
    Objects.requireNonNull(updatedAt, "updatedAt is required");
    Objects.requireNonNull(preferences, "preferences is required");
    if (!ROLES.contains(role)) throw new IllegalArgumentException("Invalid profile role");
  }

  /** Initial profile of a newly registered account: no phone and no image. */
  public static Profile initialize(UUID userId, String fullName, String role, Instant now) {
    return new Profile(UUID.randomUUID(), userId, new FullName(fullName), role, null, null, now, now,
        RoutePreferences.DEFAULTS, null);
  }
  public Profile updatePersonalData(FullName name, PhoneNumber phone, Instant now) {
    return new Profile(id, userId, name, role, phone, image, createdAt, now, preferences, preferencesUpdatedAt);
  }

  public Profile replaceImage(ProfileImage replacement, Instant now) {
    return new Profile(id, userId, fullName, role, phoneNumber, Objects.requireNonNull(replacement), createdAt, now,
        preferences, preferencesUpdatedAt);
  }

  public Profile removeImage(Instant now) {
    return new Profile(id, userId, fullName, role, phoneNumber, null, createdAt, now, preferences, preferencesUpdatedAt);
  }

  public Profile updatePreferences(RoutePreferences replacement, Instant now) {
    return new Profile(id, userId, fullName, role, phoneNumber, image, createdAt,
        Objects.requireNonNull(now), Objects.requireNonNull(replacement), now);
  }
}

package com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.mappers;

import com.uitopic.rumbo.profiles.domain.model.aggregates.Profile;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.FullName;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.PhoneNumber;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImage;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.RoutePreferences;
import com.uitopic.rumbo.profiles.infrastructure.persistence.jpa.entities.ProfileJpaEntity;

/** Converts between the {@link Profile} aggregate and its persistence entity. Absent values map to SQL {@code NULL}. */
public final class ProfileJpaMapper {
  private ProfileJpaMapper() {}

  public static ProfileJpaEntity toEntity(Profile profile) {
    ProfileJpaEntity entity = new ProfileJpaEntity();
    entity.setId(profile.id());
    entity.setUserId(profile.userId());
    entity.setFullName(profile.fullName().value());
    entity.setRole(profile.role());
    entity.setPhoneNumber(profile.phoneNumber() == null ? null : profile.phoneNumber().value());
    entity.setImageUrl(profile.image() == null ? null : profile.image().url());
    entity.setImageStorageKey(profile.image() == null ? null : profile.image().storageKey());
    entity.setCreatedAt(profile.createdAt());
    entity.setAvoidStairs(profile.preferences().avoidStairs());
    entity.setSimplerRoute(profile.preferences().simplerRoute());
    entity.setVoiceGuidance(profile.preferences().voiceGuidance());
    entity.setPreferencesUpdatedAt(profile.preferencesUpdatedAt());
    return entity;
  }

  public static Profile toDomain(ProfileJpaEntity entity) {
    PhoneNumber phone = entity.getPhoneNumber() == null ? null : new PhoneNumber(entity.getPhoneNumber());
    ProfileImage image = entity.getImageUrl() == null && entity.getImageStorageKey() == null
      ? null
      : new ProfileImage(entity.getImageUrl(), entity.getImageStorageKey());
    return new Profile(entity.getId(), entity.getUserId(), new FullName(entity.getFullName()), entity.getRole(),
      phone, image, entity.getCreatedAt(), entity.getUpdatedAt(),
      new RoutePreferences(entity.isAvoidStairs(), entity.isSimplerRoute(), entity.isVoiceGuidance()),
      entity.getPreferencesUpdatedAt());
  }
}

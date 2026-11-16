package com.uitopic.rumbo.profiles.infrastructure.storage.cloudinary;

import com.uitopic.rumbo.profiles.application.exceptions.ProfileImageStorageUnavailableException;
import com.uitopic.rumbo.profiles.application.ports.ProfileImageStorage;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImage;
import com.uitopic.rumbo.profiles.domain.model.valueobjects.ProfileImageFile;
import java.util.UUID;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.stereotype.Component;

@Component
@Conditional(UnavailableProfileImageStorage.Unconfigured.class)
public class UnavailableProfileImageStorage implements ProfileImageStorage {
  public static class Unconfigured implements Condition {
    @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      return !new CloudinaryConfiguredCondition().matches(context, metadata);
    }
  }
  @Override public void requireAvailable() { throw new ProfileImageStorageUnavailableException(); }
  @Override public ProfileImage store(UUID userId, ProfileImageFile file) {
    throw new ProfileImageStorageUnavailableException();
  }
  @Override public void delete(String storageKey) { throw new ProfileImageStorageUnavailableException(); }
}

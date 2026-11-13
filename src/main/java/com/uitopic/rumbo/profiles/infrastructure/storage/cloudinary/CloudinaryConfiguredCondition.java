package com.uitopic.rumbo.profiles.infrastructure.storage.cloudinary;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class CloudinaryConfiguredCondition implements Condition {
  @Override public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    return !context.getEnvironment().getProperty("rumbo.profiles.images.cloudinary-url", "").isBlank();
  }
}

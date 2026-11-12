package com.uitopic.rumbo.profiles.domain.model.valueobjects;

/**
 * Name shown on the profile.
 *
 * @param value non-blank name of at most {@value #MAX_LENGTH} characters
 */
public record FullName(String value) {
  public static final int MAX_LENGTH = 255;

  public FullName {
    if (value == null || value.isBlank() || value.length() > MAX_LENGTH) {
      throw new IllegalArgumentException("fullName must contain 1 to " + MAX_LENGTH + " characters");
    }
  }
}

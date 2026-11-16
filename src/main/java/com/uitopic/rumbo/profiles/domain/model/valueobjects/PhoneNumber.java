package com.uitopic.rumbo.profiles.domain.model.valueobjects;

/**
 * Optional contact phone of a profile.
 *
 * @param value non-blank phone of at most {@value #MAX_LENGTH} characters
 */
public record PhoneNumber(String value) {
  public static final int MAX_LENGTH = 30;

  public PhoneNumber {
    if (value == null || value.isBlank() || !value.matches("^(?=.{6,30}$)\\+?[0-9 ()-]+$")) {
      throw new IllegalArgumentException("Invalid phone number");
    }
  }
}

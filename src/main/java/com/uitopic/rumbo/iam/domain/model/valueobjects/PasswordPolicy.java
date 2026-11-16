package com.uitopic.rumbo.iam.domain.model.valueobjects;

/**
 * Minimum complexity every new password must meet before it is hashed.
 * The upper bound of {@value #MAX_LENGTH} characters keeps the whole password inside BCrypt's 72-byte input.
 *
 * @param minLength minimum number of characters
 * @param requiresDigit whether at least one digit is required
 * @param requiresUppercase whether at least one uppercase letter is required
 */
public record PasswordPolicy(int minLength, boolean requiresDigit, boolean requiresUppercase) {
  public static final int MAX_LENGTH = 72;
  /** Policy shown to users in the sign-up form: at least 8 characters, 1 uppercase and 1 number. */
  public static final PasswordPolicy DEFAULT = new PasswordPolicy(8, true, true);

  public PasswordPolicy {
    if (minLength < 1 || minLength > MAX_LENGTH) throw new IllegalArgumentException("Minimum length is out of range");
  }

  public boolean isSatisfiedBy(String rawPassword) {
    if (rawPassword == null || rawPassword.length() < minLength || rawPassword.length() > MAX_LENGTH) return false;
    if (rawPassword.isBlank()) return false;
    if (requiresDigit && rawPassword.chars().noneMatch(Character::isDigit)) return false;
    return !requiresUppercase || rawPassword.chars().anyMatch(Character::isUpperCase);
  }

  /** Human-readable rule, used in validation errors. */
  public String description() {
    return "At least " + minLength + " characters" + (requiresUppercase ? ", 1 uppercase" : "")
      + (requiresDigit ? ", 1 number" : "") + " and at most " + MAX_LENGTH + " characters";
  }
}

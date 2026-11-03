package com.uitopic.rumbo.iam.domain.model.valueobjects;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Email that identifies an account. It is trimmed and lower-cased on construction, so two spellings of the same
 * address can never create two accounts.
 *
 * @param value normalized address
 */
public record EmailAddress(String value) {
  public static final int MAX_LENGTH = 254;
  private static final Pattern FORMAT = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9-]+(\\.[a-z0-9-]+)*\\.[a-z]{2,}$");

  public EmailAddress {
    if (value == null) throw new IllegalArgumentException("Email is required");
    value = value.trim().toLowerCase(Locale.ROOT);
    if (value.length() > MAX_LENGTH || !FORMAT.matcher(value).matches()) {
      throw new IllegalArgumentException("Email format is invalid");
    }
  }

  @Override
  public String toString() { return value; }
}

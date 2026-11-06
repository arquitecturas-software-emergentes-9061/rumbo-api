package com.uitopic.rumbo.planning.domain.model.aggregates;

import java.util.Objects;
import java.util.UUID;

/** Administrative organization owned by one account until multi-admin membership is agreed. */
public record Clinic(UUID id, String name, String code, String contactEmail, String phone) {
  public Clinic {
    Objects.requireNonNull(id, "Clinic ID is required");
    name = required(name, 160, "Clinic name");
    code = required(code, 60, "Clinic code");
    contactEmail = required(contactEmail, 254, "Contact email");
    phone = phone == null || phone.isBlank() ? null : phone.strip();
    if (phone != null && phone.length() > 40) throw new IllegalArgumentException("Phone is too long");
  }

  private static String required(String value, int max, String field) {
    if (value == null || value.isBlank() || value.strip().length() > max)
      throw new IllegalArgumentException(field + " is required and must contain at most " + max + " characters");
    return value.strip();
  }
}

package com.uitopic.rumbo.planning.domain.model.entities;

import java.util.Locale;
import java.util.UUID;

/** A registered beacon reference, not a live Bluetooth observation. */
public record Beacon(UUID id, UUID siteId, UUID floorId, String identifier,
                     double x, double y, boolean active) {
  public Beacon {
    if (id == null || siteId == null || floorId == null) throw new IllegalArgumentException("Beacon IDs are required");
    identifier = normalizeIdentifier(identifier);
    if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0) {
      throw new IllegalArgumentException("Beacon coordinates must be finite and non-negative");
    }
  }

  public static String normalizeIdentifier(String input) {
    if (input == null) throw new IllegalArgumentException("Beacon identifier is required");
    String value = input.strip().toLowerCase(Locale.ROOT);
    if (value.length() < 4 || value.length() > 100 || !value.matches("[a-z0-9:_-]+")) {
      throw new IllegalArgumentException("Beacon identifier must contain 4-100 ASCII letters, digits, :, _ or -");
    }
    return value;
  }
}

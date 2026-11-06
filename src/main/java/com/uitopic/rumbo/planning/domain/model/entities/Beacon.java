package com.uitopic.rumbo.planning.domain.model.entities;

import java.util.Locale;
import java.util.UUID;

/**
 * A registered beacon reference, not a live Bluetooth observation.
 *
 * <p>Position is optional: a beacon can be registered before it is placed on the plan, and an
 * unplaced beacon can never be active for navigation.
 */
public record Beacon(UUID id, UUID siteId, UUID floorId, String identifier,
                     Double x, Double y, boolean active,
                     String name, UUID proximityUuid, Integer major, Integer minor,
                     Integer txPowerDbm) {
  private static final int MAX_NAME_LENGTH = 100;
  private static final int MAX_BLE_FIELD = 65535;

  public Beacon {
    if (id == null || siteId == null || floorId == null) throw new IllegalArgumentException("Beacon IDs are required");
    identifier = normalizeIdentifier(identifier);
    if ((x == null) != (y == null)) {
      throw new IllegalArgumentException("Beacon position needs both x and y");
    }
    if (x != null && (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || y < 0)) {
      throw new IllegalArgumentException("Beacon coordinates must be finite and non-negative");
    }
    if (active && x == null) throw new IllegalArgumentException("An unplaced beacon cannot be active");
    name = normalizeName(name);
    if ((proximityUuid == null) != (major == null) || (major == null) != (minor == null)) {
      throw new IllegalArgumentException("UUID, major and minor must be provided together");
    }
    if (major != null && (major < 0 || major > MAX_BLE_FIELD || minor < 0 || minor > MAX_BLE_FIELD)) {
      throw new IllegalArgumentException("Major and minor must be between 0 and 65535");
    }
    if (txPowerDbm != null && (txPowerDbm < -120 || txPowerDbm > 0)) {
      throw new IllegalArgumentException("Calibrated Tx power must be between -120 and 0 dBm");
    }
  }

  /** Legacy shape: identifier and position only. */
  public Beacon(UUID id, UUID siteId, UUID floorId, String identifier,
                double x, double y, boolean active) {
    this(id, siteId, floorId, identifier, x, y, active, null, null, null, null, null);
  }

  public boolean placed() {
    return x != null;
  }

  public static String normalizeIdentifier(String input) {
    if (input == null) throw new IllegalArgumentException("Beacon identifier is required");
    String value = input.strip().toLowerCase(Locale.ROOT);
    if (value.length() < 4 || value.length() > 100 || !value.matches("[a-z0-9:_-]+")) {
      throw new IllegalArgumentException("Beacon identifier must contain 4-100 ASCII letters, digits, :, _ or -");
    }
    return value;
  }

  /** Identifier derived from the iBeacon triplet when the client does not send one. */
  public static String deriveIdentifier(UUID proximityUuid, Integer major, Integer minor) {
    if (proximityUuid == null || major == null || minor == null) return null;
    return proximityUuid + ":" + major + ":" + minor;
  }

  private static String normalizeName(String input) {
    if (input == null) return null;
    String value = input.strip();
    if (value.isEmpty()) return null;
    if (value.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("Beacon name must have at most 100 characters");
    }
    return value;
  }
}

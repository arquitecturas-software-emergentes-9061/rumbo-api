package com.uitopic.rumbo.planning.domain.model.entities;

import java.util.UUID;

/** A clinic site identified independently of its map versions. */
public record Site(UUID id, String name, String address, UUID clinicId) {
  /** Existing name-only clients remain compatible while administrative structure is registered. */
  public Site(UUID id, String name) { this(id, name, null, null); }
  public Site {
    if (id == null) throw new IllegalArgumentException("Site ID is required");
    if (name == null) throw new IllegalArgumentException("Site name is required");
    name = name.strip();
    if (name.length() < 2 || name.length() > 120)
      throw new IllegalArgumentException("Site name must contain 2-120 characters");
    address = address == null || address.isBlank() ? null : address.strip();
    if (address != null && address.length() > 200)
      throw new IllegalArgumentException("Address must contain at most 200 characters");
  }

  public static String normalizedAddress(String address) {
    return address == null ? "" : address.strip().replaceAll("\\s+", " ").toLowerCase(java.util.Locale.ROOT);
  }
}

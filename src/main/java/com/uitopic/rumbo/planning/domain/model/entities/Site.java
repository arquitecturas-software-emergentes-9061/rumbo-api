package com.uitopic.rumbo.planning.domain.model.entities;

import java.util.UUID;

/** A clinic site identified independently of its map versions. */
public record Site(UUID id, String name) {
  public Site {
    if (id == null) throw new IllegalArgumentException("Site ID is required");
    if (name == null) throw new IllegalArgumentException("Site name is required");
    name = name.strip();
    if (name.length() < 2 || name.length() > 120)
      throw new IllegalArgumentException("Site name must contain 2-120 characters");
  }
}

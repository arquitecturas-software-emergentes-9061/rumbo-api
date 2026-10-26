package com.uitopic.rumbo.navigation.domain;
import java.util.UUID;
public record NodeKey(UUID floorId, UUID nodeId) {
  public NodeKey {
    if (floorId == null || nodeId == null) throw new IllegalArgumentException("Node identifiers required");
  }
}

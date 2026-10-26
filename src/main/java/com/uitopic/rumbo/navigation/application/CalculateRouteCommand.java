package com.uitopic.rumbo.navigation.application;
import java.util.UUID;
public record CalculateRouteCommand(UUID siteId, UUID startFloorId, UUID startNodeId,
                                    UUID destinationFloorId, UUID destinationNodeId, boolean accessibleOnly) {
  public CalculateRouteCommand {
    if (siteId==null || startFloorId==null || startNodeId==null || destinationFloorId==null || destinationNodeId==null)
      throw new IllegalArgumentException("siteId, start and destination identifiers required");
  }
}

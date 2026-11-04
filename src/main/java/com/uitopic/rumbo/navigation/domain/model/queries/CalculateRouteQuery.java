package com.uitopic.rumbo.navigation.domain.model.queries;
import java.util.UUID;
public record CalculateRouteQuery(UUID siteId, UUID startFloorId, UUID startNodeId,
                                    UUID destinationFloorId, UUID destinationNodeId, boolean accessibleOnly) {
  public CalculateRouteQuery {
    if (siteId==null || startFloorId==null || startNodeId==null || destinationFloorId==null || destinationNodeId==null)
      throw new IllegalArgumentException("siteId, start and destination identifiers required");
  }
}

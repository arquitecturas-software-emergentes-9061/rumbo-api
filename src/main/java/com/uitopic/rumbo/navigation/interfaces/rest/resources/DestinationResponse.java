package com.uitopic.rumbo.navigation.interfaces.rest.resources;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.Destination;
import java.util.UUID;

/** Provisional destination representation for the demo API. */
public record DestinationResponse(UUID id, UUID siteId, UUID floorId, UUID nodeId,
                                  String name, String specialty, String category,
                                  boolean accessible) {
  public static DestinationResponse from(Destination destination) {
    return new DestinationResponse(destination.id(), destination.siteId(),
        destination.floorId(), destination.nodeId(), destination.name(),
        destination.specialty(), destination.category(), destination.accessible());
  }
}

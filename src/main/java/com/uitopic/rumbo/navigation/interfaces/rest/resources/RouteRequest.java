package com.uitopic.rumbo.navigation.interfaces.rest.resources;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** Request accepted by the provisional demo route endpoint. */
public record RouteRequest(@NotNull UUID siteId, @NotNull UUID startFloorId,
                           @NotNull UUID startNodeId, @NotNull UUID destinationFloorId,
                           @NotNull UUID destinationNodeId, boolean accessibleOnly) {}

package com.uitopic.rumbo.planning.interfaces.acl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Stable read-only Planning contract for other bounded contexts. */
public interface PlanningPublishedMapFacade {
  /** Returns the current published version for a site, if one exists. */
  Optional<PublishedMapData> findCurrentBySite(UUID siteId);

  /** Spatial data exported without Planning aggregates or persistence entities. */
  record PublishedMapData(UUID siteId, UUID mapId, UUID modelVersionId,
                          List<PlanData> plans, List<NodeData> nodes,
                          List<ConnectionData> connections) {
    public PublishedMapData {
      Objects.requireNonNull(siteId, "siteId is required");
      Objects.requireNonNull(mapId, "mapId is required");
      Objects.requireNonNull(modelVersionId, "modelVersionId is required");
      plans = List.copyOf(plans);
      nodes = List.copyOf(nodes);
      connections = List.copyOf(connections);
    }
  }

  /** Floor-plan bounds and physical conversion for a published version. */
  record PlanData(UUID floorId, UUID planVersionId, double widthUnits,
                  double heightUnits, double metersPerUnit) {}

  /** Node coordinates and semantic role in the published version. */
  record NodeData(UUID id, UUID floorId, UUID planVersionId,
                  double x, double y, String role) {}

  /** Weighted published connection with independent physical distance. */
  record ConnectionData(UUID id, UUID sourceNodeId, UUID targetNodeId,
                        String kind, double distanceMeters, double weight,
                        boolean bidirectional) {}
}

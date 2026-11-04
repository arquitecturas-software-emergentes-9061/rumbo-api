package com.uitopic.rumbo.navigation.infrastructure.acl;

import com.uitopic.rumbo.navigation.domain.model.valueobjects.PublishedSpatialGraph;
import com.uitopic.rumbo.navigation.domain.repositories.PublishedSpatialGraphRepository;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningPublishedMapFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** Anti-corruption adapter translating Planning's published contract to Navigation's model. */
@Repository
@Profile("postgres")
public class PlanningPublishedSpatialGraphRepository implements PublishedSpatialGraphRepository {
  private final PlanningPublishedMapFacade planning;

  public PlanningPublishedSpatialGraphRepository(PlanningPublishedMapFacade planning) {
    this.planning = planning;
  }

  @Override
  public Optional<PublishedSpatialGraph> findCurrentBySite(UUID siteId) {
    return planning.findCurrentBySite(siteId).map(this::translate);
  }

  private PublishedSpatialGraph translate(PlanningPublishedMapFacade.PublishedMapData source) {
    return new PublishedSpatialGraph(source.siteId(), source.mapId(), source.modelVersionId(),
        source.plans().stream().map(plan -> new PublishedSpatialGraph.Plan(plan.floorId(),
            plan.planVersionId(), plan.widthUnits(), plan.heightUnits(),
            plan.metersPerUnit())).toList(),
        source.nodes().stream().map(node -> new PublishedSpatialGraph.Node(node.id(),
            node.floorId(), node.planVersionId(), node.x(), node.y(),
            PublishedSpatialGraph.NodeRole.valueOf(node.role()))).toList(),
        source.connections().stream().map(link -> new PublishedSpatialGraph.Link(link.id(),
            link.sourceNodeId(), link.targetNodeId(),
            PublishedSpatialGraph.LinkKind.valueOf(link.kind()),
            link.distanceMeters(), link.weight(), link.verticalPenalty(),
            link.bidirectional())).toList());
  }
}

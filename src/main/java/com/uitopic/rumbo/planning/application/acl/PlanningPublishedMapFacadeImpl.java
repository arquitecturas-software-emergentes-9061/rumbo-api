package com.uitopic.rumbo.planning.application.acl;

import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.planning.domain.repositories.PublishedMapReader;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningPublishedMapFacade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Maps Planning's published read model to its cross-context facade contract. */
@Service
@Profile("postgres")
public class PlanningPublishedMapFacadeImpl implements PlanningPublishedMapFacade {
  private final PublishedMapReader reader;

  public PlanningPublishedMapFacadeImpl(PublishedMapReader reader) {
    this.reader = reader;
  }

  @Override
  public Optional<PublishedMapData> findCurrentBySite(UUID siteId) {
    return reader.findBySite(siteId).map(this::toData);
  }

  private PublishedMapData toData(PublishedMapSnapshot source) {
    return new PublishedMapData(source.siteId(), source.mapId(), source.modelVersionId(),
        source.plans().stream().map(plan -> new PlanData(plan.floorId(),
            plan.planVersionId(), plan.widthUnits(), plan.heightUnits(),
            plan.metersPerUnit())).toList(),
        source.nodes().stream().map(node -> new NodeData(node.id(), node.floorId(),
            node.planVersionId(), node.x(), node.y(), node.type().name())).toList(),
        source.connections().stream().map(link -> new ConnectionData(link.id(),
            link.sourceNodeId(), link.targetNodeId(), link.type().name(),
            link.distanceMeters(), link.weight(), link.bidirectional())).toList());
  }
}

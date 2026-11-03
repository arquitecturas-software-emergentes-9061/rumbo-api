package com.uitopic.rumbo.planning.application.acl;

import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.planning.domain.repositories.PublishedMapRepository;
import com.uitopic.rumbo.planning.interfaces.acl.PlanningPublishedMapFacade;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Maps Planning's published read model to its cross-context facade contract. */
@Service
@Profile("postgres")
public class PlanningPublishedMapFacadeImpl implements PlanningPublishedMapFacade {
  private final PublishedMapRepository repository;

  public PlanningPublishedMapFacadeImpl(PublishedMapRepository repository) {
    this.repository = repository;
  }

  @Override
  public Optional<PublishedMapData> findCurrentBySite(UUID siteId) {
    return repository.findBySite(siteId).map(this::toData);
  }

  @Override
  public List<DestinationData> searchCurrentDestinations(UUID siteId, String term) {
    return repository.searchDestinations(siteId, term).stream()
        .map(this::toDestinationData).toList();
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
            link.distanceMeters(), link.weight(), link.verticalPenalty(),
            link.bidirectional())).toList(),
        source.destinations().stream().map(this::toDestinationData).toList());
  }

  private DestinationData toDestinationData(PublishedMapSnapshot.Destination source) {
    return new DestinationData(source.id(), source.modelVersionId(), source.nodeId(),
        source.floorId(), source.type().name(), source.name(), source.specialty());
  }
}

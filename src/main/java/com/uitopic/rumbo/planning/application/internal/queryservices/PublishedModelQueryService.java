package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedSite;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import com.uitopic.rumbo.planning.domain.repositories.PublishedMapRepository;
import com.uitopic.rumbo.planning.domain.repositories.ClinicStructureRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Consistent read of the committed public model and its floor labels. */
@Service
@Profile("postgres")
public class PublishedModelQueryService {
  private final PublishedMapRepository models;
  private final ClinicStructureRepository structure;

  public PublishedModelQueryService(PublishedMapRepository models, ClinicStructureRepository structure) {
    this.models = models;
    this.structure = structure;
  }

  @Transactional(readOnly = true)
  public List<PublishedSite> catalog(UUID ownerUserId) { return models.findPublishedSites(ownerUserId); }

  public record View(PublishedMapSnapshot graph, List<Floor> floors) {}

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Optional<View> current(UUID siteId) {
    return models.findBySite(siteId).map(graph -> new View(graph, structure.findFloors(siteId).stream()
        .filter(floor -> graph.plans().stream().anyMatch(plan -> plan.floorId().equals(floor.id()))).toList()));
  }
}

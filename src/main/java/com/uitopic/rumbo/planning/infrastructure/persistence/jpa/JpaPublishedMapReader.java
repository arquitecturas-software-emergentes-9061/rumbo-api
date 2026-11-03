package com.uitopic.rumbo.planning.infrastructure.persistence.jpa;

import com.uitopic.rumbo.planning.domain.repositories.PublishedMapReader;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.IndoorMapJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapConnectionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapNodeJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapVersionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.PlanCoordinateSpaceJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA read adapter translating a committed published version to the Planning export. */
@Repository
@Profile("postgres")
public class JpaPublishedMapReader implements PublishedMapReader {
  @PersistenceContext private EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public Optional<PublishedMapSnapshot> findBySite(UUID siteId) {
    var roots = entityManager.createQuery("select m from IndoorMapJpaEntity m "
        + "where m.siteId = :siteId", IndoorMapJpaEntity.class)
        .setParameter("siteId", siteId).getResultList();
    if (roots.isEmpty()) return Optional.empty();
    UUID mapId = roots.getFirst().id();
    var versions = entityManager.createQuery("select v from MapVersionJpaEntity v "
        + "where v.mapId = :mapId and v.status = :status", MapVersionJpaEntity.class)
        .setParameter("mapId", mapId)
        .setParameter("status", MapVersionStatus.PUBLISHED).getResultList();
    if (versions.isEmpty()) return Optional.empty();
    UUID versionId = versions.getFirst().id();

    var plans = entityManager.createQuery("select p from PlanCoordinateSpaceJpaEntity p "
        + "where p.id.modelVersionId = :versionId order by p.id.elementId",
        PlanCoordinateSpaceJpaEntity.class).setParameter("versionId", versionId).getResultList()
        .stream().map(row -> {
          if (row.metersPerUnit() == null) {
            throw new IllegalStateException("Published plan has no confirmed scale");
          }
          return new PublishedMapSnapshot.Plan(row.floorId(), row.id().elementId(),
              row.widthUnits(), row.heightUnits(), row.metersPerUnit());
        }).toList();
    var nodes = entityManager.createQuery("select n from MapNodeJpaEntity n "
        + "where n.id.modelVersionId = :versionId order by n.id.elementId",
        MapNodeJpaEntity.class).setParameter("versionId", versionId).getResultList()
        .stream().map(row -> new PublishedMapSnapshot.Node(row.id().elementId(),
            row.floorId(), row.planVersionId(), row.x(), row.y(),
            NodeType.valueOf(row.nodeType()))).toList();
    var connections = entityManager.createQuery("select c from MapConnectionJpaEntity c "
        + "where c.id.modelVersionId = :versionId order by c.id.elementId",
        MapConnectionJpaEntity.class).setParameter("versionId", versionId).getResultList()
        .stream().map(row -> new PublishedMapSnapshot.Connection(row.id().elementId(),
            row.sourceNodeId(), row.targetNodeId(), ConnectionType.valueOf(row.connectionType()),
            row.distanceMeters(), row.weight(), row.bidirectional())).toList();
    return Optional.of(new PublishedMapSnapshot(siteId, mapId, versionId,
        plans, nodes, connections));
  }
}

package com.uitopic.rumbo.planning.infrastructure.persistence.jpa;

import com.uitopic.rumbo.planning.application.ports.IndoorMapStore;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.IndoorMapJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapConnectionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapNodeJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapVersionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.PlanCoordinateSpaceJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.VersionedElementId;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.repositories.IndoorMapJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL adapter that stores one complete draft graph atomically. */
@Repository
@Profile("postgres")
public class JpaIndoorMapStore implements IndoorMapStore {
  private final IndoorMapJpaRepository roots;

  @PersistenceContext
  private EntityManager entityManager;

  public JpaIndoorMapStore(IndoorMapJpaRepository roots) {
    this.roots = roots;
  }

  @Override
  @Transactional
  public void saveDraft(IndoorMap map) {
    IndoorMapJpaEntity root = roots.findById(map.id()).orElse(null);
    if (root == null) {
      roots.saveAndFlush(new IndoorMapJpaEntity(map.id(), map.siteId()));
    } else if (!root.siteId().equals(map.siteId())) {
      throw new IllegalArgumentException("Map ID already belongs to another site");
    }
    MapVersionJpaEntity version = entityManager.find(MapVersionJpaEntity.class, map.draftVersionId());
    if (version == null) {
      entityManager.persist(new MapVersionJpaEntity(map.draftVersionId(), map.id(), "DRAFT"));
      entityManager.flush();
    } else if (!version.mapId().equals(map.id()) || !version.status().equals("DRAFT")) {
      throw new IllegalArgumentException("Model version is not this map's draft");
    }

    UUID versionId = map.draftVersionId();
    entityManager.createQuery("delete from MapConnectionJpaEntity c where c.id.modelVersionId = :versionId")
        .setParameter("versionId", versionId).executeUpdate();
    entityManager.createQuery("delete from MapNodeJpaEntity n where n.id.modelVersionId = :versionId")
        .setParameter("versionId", versionId).executeUpdate();
    entityManager.createQuery("delete from PlanCoordinateSpaceJpaEntity p where p.id.modelVersionId = :versionId")
        .setParameter("versionId", versionId).executeUpdate();
    entityManager.flush();
    entityManager.clear();

    Map<UUID, MapScale> scales = map.scales().stream()
        .collect(Collectors.toMap(MapScale::planVersionId, Function.identity()));
    for (PlanCoordinateSpace space : map.plans()) {
      MapScale scale = scales.get(space.planVersionId());
      entityManager.persist(new PlanCoordinateSpaceJpaEntity(
          new VersionedElementId(space.planVersionId(), versionId), space.floorId(),
          space.widthUnits(), space.heightUnits(), scale == null ? null : scale.metersPerUnit()));
    }
    entityManager.flush();
    for (MapNode node : map.nodes()) {
      entityManager.persist(new MapNodeJpaEntity(
          new VersionedElementId(node.id(), versionId), node.position().floorId(),
          node.position().planVersionId(), node.position().x(), node.position().y(), node.type().name()));
    }
    entityManager.flush();
    for (MapConnection connection : map.connections()) {
      entityManager.persist(new MapConnectionJpaEntity(
          new VersionedElementId(connection.id(), versionId), connection.sourceNodeId(),
          connection.targetNodeId(), connection.type().name(), connection.distanceMeters(),
          connection.weight(), connection.bidirectional()));
    }
    entityManager.flush();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<IndoorMap> findDraft(UUID mapId, UUID modelVersionId) {
    IndoorMapJpaEntity root = roots.findById(mapId).orElse(null);
    MapVersionJpaEntity version = entityManager.find(MapVersionJpaEntity.class, modelVersionId);
    if (root == null || version == null || !version.mapId().equals(mapId)
        || !version.status().equals("DRAFT")) {
      return Optional.empty();
    }
    IndoorMap map = new IndoorMap(root.id(), root.siteId(), version.id());
    var planRows = entityManager.createQuery("select p from PlanCoordinateSpaceJpaEntity p "
        + "where p.id.modelVersionId = :versionId order by p.id.elementId", PlanCoordinateSpaceJpaEntity.class)
        .setParameter("versionId", modelVersionId).getResultList();
    Map<UUID, PlanCoordinateSpace> spaces = planRows.stream()
        .map(row -> new PlanCoordinateSpace(row.floorId(), row.id().elementId(),
            row.widthUnits(), row.heightUnits()))
        .collect(Collectors.toMap(PlanCoordinateSpace::planVersionId, Function.identity()));
    for (PlanCoordinateSpace space : spaces.values()) map.addPlan(space);
    for (PlanCoordinateSpaceJpaEntity row : planRows) {
      if (row.metersPerUnit() != null) {
        map.setScale(new MapScale(row.floorId(), row.id().elementId(), row.metersPerUnit()));
      }
    }
    var nodeRows = entityManager.createQuery("select n from MapNodeJpaEntity n "
        + "where n.id.modelVersionId = :versionId order by n.id.elementId", MapNodeJpaEntity.class)
        .setParameter("versionId", modelVersionId).getResultList();
    for (MapNodeJpaEntity row : nodeRows) {
      PlanCoordinateSpace space = spaces.get(row.planVersionId());
      if (space == null) throw new IllegalStateException("Stored node refers to a missing plan version");
      map.addNode(new MapNode(row.id().elementId(), modelVersionId,
          new SpatialCoordinate(row.floorId(), row.planVersionId(), row.x(), row.y()),
          NodeType.valueOf(row.nodeType()), space));
    }
    var connectionRows = entityManager.createQuery("select c from MapConnectionJpaEntity c "
        + "where c.id.modelVersionId = :versionId order by c.id.elementId", MapConnectionJpaEntity.class)
        .setParameter("versionId", modelVersionId).getResultList();
    for (MapConnectionJpaEntity row : connectionRows) {
      map.addConnection(new MapConnection(row.id().elementId(), modelVersionId,
          row.sourceNodeId(), row.targetNodeId(), ConnectionType.valueOf(row.connectionType()),
          row.distanceMeters(), row.weight(), row.bidirectional()));
    }
    return Optional.of(map);
  }
}

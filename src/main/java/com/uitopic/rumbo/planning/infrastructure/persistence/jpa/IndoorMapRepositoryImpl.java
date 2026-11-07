package com.uitopic.rumbo.planning.infrastructure.persistence.jpa;

import com.uitopic.rumbo.planning.domain.repositories.IndoorMapRepository;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapVersionStateException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.exceptions.DraftGraphRevisionConflictException;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapDestination;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.entities.MapVersion;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphElementType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssue;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.IndoorMapJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapConnectionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapDestinationJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapNodeJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapVersionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.PlanCoordinateSpaceJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.VersionedElementId;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.repositories.IndoorMapJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

/** PostgreSQL adapter that stores one complete draft graph atomically. */
@Repository
@Profile("postgres")
public class IndoorMapRepositoryImpl implements IndoorMapRepository {
  private final IndoorMapJpaRepository roots;

  @PersistenceContext
  private EntityManager entityManager;

  public IndoorMapRepositoryImpl(IndoorMapJpaRepository roots) {
    this.roots = roots;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean lockMapForUpdate(UUID siteId, UUID mapId) {
    IndoorMapJpaEntity root = entityManager.find(IndoorMapJpaEntity.class,
        mapId, LockModeType.PESSIMISTIC_WRITE);
    return root != null && root.siteId().equals(siteId);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean siteHasMap(UUID siteId) {
    return !entityManager.createQuery("select r.id from IndoorMapJpaEntity r where r.siteId = :siteId", UUID.class)
        .setParameter("siteId", siteId).setMaxResults(1).getResultList().isEmpty();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean hasDraft(UUID mapId) {
    return !entityManager.createQuery("select v.id from MapVersionJpaEntity v "
            + "where v.mapId = :mapId and v.status = :status", UUID.class)
        .setParameter("mapId", mapId)
        .setParameter("status", MapVersionStatus.DRAFT)
        .setMaxResults(1).getResultList().isEmpty();
  }

  @Override
  @Transactional
  public void saveDraft(IndoorMap map) {
    if (map.status() != MapVersionStatus.DRAFT) {
      throw new InvalidMapVersionStateException(map.status());
    }
    IndoorMapJpaEntity root = roots.findById(map.id()).orElse(null);
    if (root == null) {
      roots.saveAndFlush(new IndoorMapJpaEntity(map.id(), map.siteId()));
    } else if (!root.siteId().equals(map.siteId())) {
      throw new IllegalArgumentException("Map ID already belongs to another site");
    }
    MapVersionJpaEntity version = entityManager.find(MapVersionJpaEntity.class,
        map.modelVersionId(), LockModeType.PESSIMISTIC_WRITE);
    if (version == null) {
      if (map.editRevision() != 0) throw new DraftGraphRevisionConflictException();
      entityManager.persist(new MapVersionJpaEntity(map.modelVersionId(), map.id(), MapVersionStatus.DRAFT));
      entityManager.flush();
    } else if (!version.mapId().equals(map.id())) {
      throw new IllegalArgumentException("Model version belongs to another map");
    } else if (version.status() != MapVersionStatus.DRAFT) {
      throw new InvalidMapVersionStateException(version.status());
    } else {
      if (version.editRevision() != map.editRevision()) {
        throw new DraftGraphRevisionConflictException();
      }
      map.advanceEditRevision();
      version.advanceEditRevision();
      entityManager.flush();
    }

    for (PlanCoordinateSpace space : map.plans()) {
      entityManager.createNativeQuery("INSERT INTO planning_floor_sites (floor_id, site_id) "
              + "VALUES (:floorId, :siteId) ON CONFLICT (floor_id) DO NOTHING")
          .setParameter("floorId", space.floorId())
          .setParameter("siteId", map.siteId()).executeUpdate();
      UUID owner = (UUID) entityManager.createNativeQuery(
              "SELECT site_id FROM planning_floor_sites WHERE floor_id = :floorId")
          .setParameter("floorId", space.floorId()).getSingleResult();
      if (!owner.equals(map.siteId())) {
        throw invalidStored(GraphIssueCode.PLAN_FLOOR_MISMATCH, GraphElementType.PLAN,
            space.planVersionId(), "Floor belongs to another site");
      }
    }

    UUID versionId = map.modelVersionId();
    entityManager.createQuery("delete from MapDestinationJpaEntity d where d.id.modelVersionId = :versionId")
        .setParameter("versionId", versionId).executeUpdate();
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
          connection.weight(), connection.verticalPenalty(), connection.accessibility().name(),
          connection.bidirectional()));
    }
    entityManager.flush();
    for (MapDestination destination : map.destinations()) {
      entityManager.persist(new MapDestinationJpaEntity(
          new VersionedElementId(destination.id(), versionId), destination.nodeId(),
          destination.type().name(), destination.name(), destination.specialty(),
          DirectorySearchIndex.searchableText(destination.name(), destination.specialty(),
              destination.type().name())));
    }
    entityManager.flush();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<IndoorMap> findDraft(UUID mapId, UUID modelVersionId) {
    return loadDraft(mapId, modelVersionId, false);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<IndoorMap> findDraftBySite(UUID siteId) {
    return entityManager.createQuery("""
        select version from MapVersionJpaEntity version, IndoorMapJpaEntity root
        where version.mapId = root.id and root.siteId = :siteId and version.status = :status
        """, MapVersionJpaEntity.class)
      .setParameter("siteId", siteId).setParameter("status", MapVersionStatus.DRAFT)
      .getResultList().stream().findFirst()
      .flatMap(version -> loadDraft(version.mapId(), version.id(), false));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<IndoorMap> findDraftForUpdate(UUID mapId, UUID modelVersionId) {
    return loadDraft(mapId, modelVersionId, true);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void publish(IndoorMap map) {
    if (map.status() != MapVersionStatus.PUBLISHED) {
      throw new InvalidMapVersionStateException(map.status());
    }
    MapVersionJpaEntity version = entityManager.find(MapVersionJpaEntity.class,
        map.modelVersionId(), LockModeType.PESSIMISTIC_WRITE);
    if (version == null || !version.mapId().equals(map.id())) {
      throw new IllegalArgumentException("Model version does not belong to this map");
    }
    if (version.status() != MapVersionStatus.DRAFT) {
      throw new InvalidMapVersionStateException(version.status());
    }
    List<MapVersionJpaEntity> published = entityManager.createQuery(
        "select v from MapVersionJpaEntity v where v.mapId = :mapId and v.status = :status",
        MapVersionJpaEntity.class)
        .setParameter("mapId", map.id())
        .setParameter("status", MapVersionStatus.PUBLISHED)
        .setLockMode(LockModeType.PESSIMISTIC_WRITE)
        .getResultList();
    for (MapVersionJpaEntity previous : published) {
      MapVersion previousVersion = new MapVersion(previous.id(), previous.status());
      previousVersion.archive();
      previous.changeStatus(previousVersion.status());
    }
    entityManager.flush();
    version.changeStatus(MapVersionStatus.PUBLISHED);
    entityManager.flush();
  }

  private Optional<IndoorMap> loadDraft(UUID mapId, UUID modelVersionId, boolean lock) {
    IndoorMapJpaEntity root = roots.findById(mapId).orElse(null);
    MapVersionJpaEntity version = lock
        ? entityManager.find(MapVersionJpaEntity.class, modelVersionId, LockModeType.PESSIMISTIC_WRITE)
        : entityManager.find(MapVersionJpaEntity.class, modelVersionId);
    if (root == null || version == null || !version.mapId().equals(mapId)) {
      return Optional.empty();
    }
    if (version.status() != MapVersionStatus.DRAFT) {
      if (lock) throw new InvalidMapVersionStateException(version.status());
      return Optional.empty();
    }
    IndoorMap map = new IndoorMap(root.id(), root.siteId(), version.id(),
        version.editRevision());
    var planRows = entityManager.createQuery("select p from PlanCoordinateSpaceJpaEntity p "
        + "where p.id.modelVersionId = :versionId order by p.id.elementId", PlanCoordinateSpaceJpaEntity.class)
        .setParameter("versionId", modelVersionId).getResultList();
    Map<UUID, PlanCoordinateSpace> spaces = new LinkedHashMap<>();
    for (PlanCoordinateSpaceJpaEntity row : planRows) {
      PlanCoordinateSpace space;
      try {
        space = new PlanCoordinateSpace(row.floorId(), row.id().elementId(),
            row.widthUnits(), row.heightUnits());
      } catch (IllegalArgumentException error) {
        throw invalidStored(GraphIssueCode.INVALID_PLAN_DATA, GraphElementType.PLAN,
            row.id().elementId(), "Stored plan coordinate space is invalid");
      }
      spaces.put(space.planVersionId(), space);
      map.addPlan(space);
    }
    for (PlanCoordinateSpaceJpaEntity row : planRows) {
      if (row.metersPerUnit() != null) {
        try {
          map.setScale(new MapScale(row.floorId(), row.id().elementId(), row.metersPerUnit()));
        } catch (IllegalArgumentException error) {
          throw invalidStored(GraphIssueCode.INVALID_PLAN_DATA, GraphElementType.PLAN,
              row.id().elementId(), "Stored plan scale is invalid");
        }
      }
    }
    var nodeRows = entityManager.createQuery("select n from MapNodeJpaEntity n "
        + "where n.id.modelVersionId = :versionId order by n.id.elementId", MapNodeJpaEntity.class)
        .setParameter("versionId", modelVersionId).getResultList();
    for (MapNodeJpaEntity row : nodeRows) {
      PlanCoordinateSpace space = spaces.get(row.planVersionId());
      if (space == null) {
        throw invalidStored(GraphIssueCode.MISSING_PLAN_VERSION, GraphElementType.NODE,
            row.id().elementId(), "Stored node refers to a missing plan version");
      }
      try {
        map.addNode(new MapNode(row.id().elementId(), modelVersionId,
            new SpatialCoordinate(row.floorId(), row.planVersionId(), row.x(), row.y()),
            NodeType.valueOf(row.nodeType()), space));
      } catch (InvalidMapGraphException error) {
        throw error;
      } catch (IllegalArgumentException error) {
        throw invalidStored(GraphIssueCode.INVALID_NODE_DATA, GraphElementType.NODE,
            row.id().elementId(), "Stored node position or type is invalid");
      }
    }
    var connectionRows = entityManager.createQuery("select c from MapConnectionJpaEntity c "
        + "where c.id.modelVersionId = :versionId order by c.id.elementId", MapConnectionJpaEntity.class)
        .setParameter("versionId", modelVersionId).getResultList();
    for (MapConnectionJpaEntity row : connectionRows) {
      try {
        map.addConnection(new MapConnection(row.id().elementId(), modelVersionId,
            row.sourceNodeId(), row.targetNodeId(), ConnectionType.valueOf(row.connectionType()),
            row.distanceMeters(), row.weight(), row.verticalPenalty(),
            ConnectionAccessibility.valueOf(row.accessibilityStatus()), row.bidirectional()));
      } catch (InvalidMapGraphException error) {
        throw error;
      } catch (IllegalArgumentException error) {
        throw invalidStored(GraphIssueCode.INVALID_CONNECTION_DATA, GraphElementType.CONNECTION,
            row.id().elementId(), "Stored connection geometry, type, or metrics are invalid");
      }
    }
    var destinationRows = entityManager.createQuery("select d from MapDestinationJpaEntity d "
        + "where d.id.modelVersionId = :versionId order by d.id.elementId", MapDestinationJpaEntity.class)
        .setParameter("versionId", modelVersionId).getResultList();
    for (MapDestinationJpaEntity row : destinationRows) {
      try {
        map.addDestination(new MapDestination(row.id().elementId(), modelVersionId,
            row.nodeId(), DirectoryEntryType.valueOf(row.entryType()),
            row.name(), row.specialty()));
      } catch (InvalidMapGraphException error) {
        throw error;
      } catch (IllegalArgumentException error) {
        throw invalidStored(GraphIssueCode.INVALID_DESTINATION_DATA, GraphElementType.DESTINATION,
            row.id().elementId(), "Stored destination metadata is invalid");
      }
    }
    return Optional.of(map);
  }

  private InvalidMapGraphException invalidStored(GraphIssueCode code, GraphElementType elementType,
                                                 UUID elementId, String message) {
    return new InvalidMapGraphException(new GraphIssue(code, elementType, elementId, message));
  }
}

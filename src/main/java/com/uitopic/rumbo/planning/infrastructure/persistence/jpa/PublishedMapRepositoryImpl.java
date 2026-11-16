package com.uitopic.rumbo.planning.infrastructure.persistence.jpa;

import com.uitopic.rumbo.planning.domain.repositories.PublishedMapRepository;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidPublishedMapDataException;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionAccessibility;
import com.uitopic.rumbo.planning.domain.model.valueobjects.DirectoryEntryType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapVersionStatus;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapWall;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapDoor;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.IndoorMapJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapConnectionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapDestinationJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapNodeJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.MapVersionJpaEntity;
import com.uitopic.rumbo.planning.infrastructure.persistence.jpa.model.PlanCoordinateSpaceJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JPA read adapter translating a committed published version to the Planning export. */
@Repository
@Profile("postgres")
public class PublishedMapRepositoryImpl implements PublishedMapRepository {
  @PersistenceContext private EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public Optional<PublishedMapSnapshot> findBySite(UUID siteId) {
    try {
      return readPublishedSnapshot(siteId);
    } catch (IllegalArgumentException | IllegalStateException error) {
      throw new InvalidPublishedMapDataException(error);
    }
  }

  private Optional<PublishedMapSnapshot> readPublishedSnapshot(UUID siteId) {
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
            row.distanceMeters(), row.weight(), row.verticalPenalty(),
            ConnectionAccessibility.valueOf(row.accessibilityStatus()),
            row.bidirectional())).toList();
    Map<UUID, PublishedMapSnapshot.Node> nodesById = nodes.stream()
        .collect(Collectors.toMap(PublishedMapSnapshot.Node::id, Function.identity()));
    var destinationRows = entityManager.createQuery("select d from MapDestinationJpaEntity d "
        + "where d.id.modelVersionId = :versionId order by d.id.elementId",
        MapDestinationJpaEntity.class).setParameter("versionId", versionId).getResultList();
    if (destinationRows.stream().anyMatch(row -> !nodesById.containsKey(row.nodeId())
        || nodesById.get(row.nodeId()).type() != NodeType.DESTINATION)) {
      throw new IllegalStateException("Published directory contains an invalid destination node");
    }
    var destinations = destinationRows.stream()
        .map(row -> new PublishedMapSnapshot.Destination(row.id().elementId(), versionId,
            row.nodeId(), nodesById.get(row.nodeId()).floorId(),
            DirectoryEntryType.valueOf(row.entryType()), row.name(), row.specialty())).toList();
    List<MapWall> walls = new java.util.ArrayList<>();
    for (Object raw : entityManager.createNativeQuery("SELECT id, floor_id, plan_version_id, x1, y1, x2, y2 FROM map_walls WHERE model_version_id = :versionId ORDER BY id")
        .setParameter("versionId", versionId).getResultList()) {
      Object[] row = (Object[]) raw;
      walls.add(new MapWall((UUID) row[0], (UUID) row[1], (UUID) row[2],
          ((Number) row[3]).doubleValue(), ((Number) row[4]).doubleValue(),
          ((Number) row[5]).doubleValue(), ((Number) row[6]).doubleValue()));
    }
    List<MapDoor> doors = new java.util.ArrayList<>();
    for (Object raw : entityManager.createNativeQuery("SELECT id, wall_id, from_fraction, to_fraction FROM map_doors WHERE model_version_id = :versionId ORDER BY id")
        .setParameter("versionId", versionId).getResultList()) {
      Object[] row = (Object[]) raw;
      doors.add(new MapDoor((UUID) row[0], (UUID) row[1],
          ((Number) row[2]).doubleValue(), ((Number) row[3]).doubleValue()));
    }
    return Optional.of(new PublishedMapSnapshot(siteId, mapId, versionId,
        plans, nodes, connections, destinations, walls, doors));
  }

  @Override
  @Transactional(readOnly = true)
  public List<PublishedMapSnapshot.Destination> searchDestinations(UUID siteId, String term) {
    String query = DirectorySearchIndex.prefixQuery(term);
    if (query.isBlank()) return List.of();
    String sql = "SELECT d.id, d.model_version_id, d.node_id, n.floor_id, "
        + "d.entry_type, d.name, d.specialty FROM map_destinations d "
        + "JOIN map_nodes n ON n.id = d.node_id AND n.model_version_id = d.model_version_id "
        + "JOIN map_versions v ON v.id = d.model_version_id "
        + "JOIN indoor_maps m ON m.id = v.map_id "
        + "WHERE m.site_id = :siteId AND v.status = 'PUBLISHED' "
        + "AND n.node_type = 'DESTINATION' "
        + "AND to_tsvector('simple'::regconfig, d.search_text) "
        + "@@ to_tsquery('simple'::regconfig, :terms) "
        + "ORDER BY d.name, d.id LIMIT 50";
    List<PublishedMapSnapshot.Destination> found = new java.util.ArrayList<>();
    for (Object result : entityManager.createNativeQuery(sql)
        .setParameter("siteId", siteId).setParameter("terms", query).getResultList()) {
      Object[] row = (Object[]) result;
      found.add(new PublishedMapSnapshot.Destination((UUID) row[0], (UUID) row[1],
          (UUID) row[2], (UUID) row[3], DirectoryEntryType.valueOf((String) row[4]),
          (String) row[5], (String) row[6]));
    }
    return List.copyOf(found);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean existsCurrentNodeOutsideSite(UUID nodeId, UUID siteId) {
    String sql = "SELECT EXISTS (SELECT 1 FROM map_nodes n "
        + "JOIN map_versions v ON v.id = n.model_version_id "
        + "JOIN indoor_maps m ON m.id = v.map_id "
        + "WHERE n.id = :nodeId AND v.status = 'PUBLISHED' "
        + "AND m.site_id <> :siteId)";
    return Boolean.TRUE.equals(entityManager.createNativeQuery(sql)
        .setParameter("nodeId", nodeId).setParameter("siteId", siteId)
        .getSingleResult());
  }
}

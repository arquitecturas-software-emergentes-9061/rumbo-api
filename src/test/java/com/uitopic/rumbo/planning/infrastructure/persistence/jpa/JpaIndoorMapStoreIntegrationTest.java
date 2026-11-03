package com.uitopic.rumbo.planning.infrastructure.persistence.jpa;

import static org.junit.jupiter.api.Assertions.*;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.entities.MapConnection;
import com.uitopic.rumbo.planning.domain.model.entities.MapNode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.SpatialCoordinate;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real PostgreSQL round-trip of a draft graph; set RUMBO_TEST_DB_URL to enable it. */
@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres")
@Import(JpaIndoorMapStore.class)
@EnabledIfEnvironmentVariable(named = "RUMBO_TEST_DB_URL", matches = ".+")
class JpaIndoorMapStoreIntegrationTest {
  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry properties) {
    properties.add("spring.datasource.url", () -> System.getenv("RUMBO_TEST_DB_URL"));
    properties.add("spring.datasource.username", () -> System.getenv("RUMBO_TEST_DB_USER"));
    properties.add("spring.datasource.password", () -> System.getenv("RUMBO_TEST_DB_PASSWORD"));
  }

  @Autowired JpaIndoorMapStore store;
  @Autowired EntityManager entityManager;
  @Autowired JdbcTemplate jdbc;

  @Test
  void savesAndReloadsPlanScaleNodesAndWeightedConnections() {
    UUID versionId = UUID.randomUUID();
    UUID floorId = UUID.randomUUID();
    UUID planVersionId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), versionId);
    PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 100, 80);
    MapScale scale = new MapScale(floorId, planVersionId, 0.25);
    MapNode access = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 10, 20), NodeType.ACCESS, space);
    MapNode destination = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 30, 40), NodeType.DESTINATION, space);
    MapConnection connection = new MapConnection(UUID.randomUUID(), versionId,
        access.id(), destination.id(), ConnectionType.CORRIDOR, 7.1, 9.5, true);
    map.addPlan(space);
    map.setScale(scale);
    map.addNode(access);
    map.addNode(destination);
    map.addConnection(connection);

    store.saveDraft(map);
    entityManager.clear();
    IndoorMap loaded = store.findDraft(map.id(), versionId).orElseThrow();

    assertEquals(map.siteId(), loaded.siteId());
    assertEquals(java.util.List.of(space), loaded.plans());
    assertEquals(java.util.List.of(scale), loaded.scales());
    assertEquals(2, loaded.nodes().size());
    assertEquals(access.position(), loaded.nodes().stream()
        .filter(node -> node.id().equals(access.id())).findFirst().orElseThrow().position());
    assertEquals(1, loaded.connections().size());
    MapConnection reloadedConnection = loaded.connections().getFirst();
    assertEquals(connection.id(), reloadedConnection.id());
    assertEquals(connection.sourceNodeId(), reloadedConnection.sourceNodeId());
    assertEquals(connection.targetNodeId(), reloadedConnection.targetNodeId());
    assertEquals(7.1, reloadedConnection.distanceMeters());
    assertEquals(9.5, reloadedConnection.weight());
    assertTrue(loaded.validateForPublication().isValid());
    assertTrue(store.findDraft(map.id(), UUID.randomUUID()).isEmpty());
  }

  @Test
  void resavesAnEditedDraftWithoutLosingItsVersion() {
    UUID versionId = UUID.randomUUID();
    UUID floorId = UUID.randomUUID();
    UUID planVersionId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), versionId);
    PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 50, 50);
    map.addPlan(space);
    map.setScale(new MapScale(floorId, planVersionId, 0.25));
    MapNode access = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 1, 1), NodeType.ACCESS, space);
    map.addNode(access);
    store.saveDraft(map);
    entityManager.clear();

    IndoorMap edited = store.findDraft(map.id(), versionId).orElseThrow();
    MapNode destination = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 4, 4), NodeType.DESTINATION, space);
    edited.addNode(destination);
    edited.addConnection(new MapConnection(UUID.randomUUID(), versionId,
        access.id(), destination.id(), ConnectionType.DOORWAY, 2, 3, false));
    store.saveDraft(edited);
    entityManager.clear();

    IndoorMap reloaded = store.findDraft(map.id(), versionId).orElseThrow();
    assertEquals(2, reloaded.nodes().size());
    assertEquals(1, reloaded.connections().size());
    assertTrue(reloaded.validateForPublication().isValid());
  }

  @Test
  void databaseRejectsConnectionToNodeOutsideTheStoredVersion() {
    UUID versionId = UUID.randomUUID();
    UUID floorId = UUID.randomUUID();
    UUID planVersionId = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), versionId);
    PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 10, 10);
    map.addPlan(space);
    MapNode access = new MapNode(UUID.randomUUID(), versionId,
        new SpatialCoordinate(floorId, planVersionId, 1, 1), NodeType.ACCESS, space);
    map.addNode(access);
    store.saveDraft(map);

    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "INSERT INTO map_connections (id, model_version_id, source_node_id, target_node_id, "
            + "connection_type, distance_meters, weight, bidirectional) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(), versionId, access.id(), UUID.randomUUID(), "CORRIDOR", 1.0, 1.0, true));
  }
}

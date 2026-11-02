package com.uitopic.rumbo.planning.domain.model.entities;

import static org.junit.jupiter.api.Assertions.*;

import com.uitopic.rumbo.planning.domain.model.valueobjects.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MapGraphModelTest {
  private final UUID floorId = UUID.randomUUID();
  private final UUID planVersionId = UUID.randomUUID();
  private final UUID modelVersionId = UUID.randomUUID();
  private final PlanCoordinateSpace space = new PlanCoordinateSpace(floorId, planVersionId, 100, 80);

  @Test
  void nodeRetainsPlanVersionAndUsesIdentityRatherThanPosition() {
    UUID nodeId = UUID.randomUUID();
    var first = new MapNode(nodeId, modelVersionId,
        new SpatialCoordinate(floorId, planVersionId, 10, 20), NodeType.INTERSECTION, space);
    var relocated = new MapNode(nodeId, modelVersionId,
        new SpatialCoordinate(floorId, planVersionId, 20, 30), NodeType.ACCESS, space);
    assertEquals(first, relocated);
    assertEquals(first.hashCode(), relocated.hashCode());
    assertEquals(planVersionId, first.position().planVersionId());
    assertNotEquals(first, new MapNode(nodeId, UUID.randomUUID(), first.position(), first.type(), space));
  }

  @Test
  void nodeRejectsPositionOutsideItsPlanVersion() {
    assertThrows(IllegalArgumentException.class,
        () -> new MapNode(UUID.randomUUID(), modelVersionId,
            new SpatialCoordinate(floorId, planVersionId, 101, 20), NodeType.ACCESS, space));
    assertThrows(IllegalArgumentException.class,
        () -> new MapNode(UUID.randomUUID(), modelVersionId,
            new SpatialCoordinate(floorId, UUID.randomUUID(), 1, 1), NodeType.ACCESS, space));
    assertThrows(NullPointerException.class,
        () -> new MapNode(UUID.randomUUID(), modelVersionId,
            new SpatialCoordinate(floorId, planVersionId, 1, 1), null, space));
  }

  @Test
  void connectionKeepsPhysicalDistanceSeparateFromRoutingWeight() {
    UUID from = UUID.randomUUID(), to = UUID.randomUUID(), connectionId = UUID.randomUUID();
    var connection = new MapConnection(connectionId, modelVersionId, from, to,
        ConnectionType.CORRIDOR, 5, 8, true);
    assertEquals(5, connection.distanceMeters());
    assertEquals(8, connection.weight());
    assertTrue(connection.bidirectional());
    assertEquals(connection, new MapConnection(connectionId, modelVersionId, from, to,
        ConnectionType.CORRIDOR, 5, 9, true));
  }

  @Test
  void connectionRejectsSelfLinksAndInvalidMetrics() {
    UUID node = UUID.randomUUID(), other = UUID.randomUUID();
    assertThrows(IllegalArgumentException.class,
        () -> new MapConnection(UUID.randomUUID(), modelVersionId, node, node,
            ConnectionType.CORRIDOR, 1, 1, false));
    assertThrows(IllegalArgumentException.class,
        () -> new MapConnection(UUID.randomUUID(), modelVersionId, node, other,
            ConnectionType.CORRIDOR, 0, 1, false));
    assertThrows(IllegalArgumentException.class,
        () -> new MapConnection(UUID.randomUUID(), modelVersionId, node, other,
            ConnectionType.CORRIDOR, 1, Double.POSITIVE_INFINITY, false));
    assertThrows(NullPointerException.class,
        () -> new MapConnection(null, modelVersionId, node, other,
            ConnectionType.CORRIDOR, 1, 1, false));
  }
}

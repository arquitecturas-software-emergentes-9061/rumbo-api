package com.uitopic.rumbo.planning.domain.model.aggregates;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapDoor;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapWall;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapScale;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import com.uitopic.rumbo.planning.domain.exceptions.MapPublicationRejectedException;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapGeometryTest {
  @Test
  void rejectsCollinearOverlapInBothDirectionsButAllowsParallelAndDisjointLinks() {
    double[][] segments = {{20, 50, 80, 50}, {80, 50, 20, 50}, {0, 50, 100, 50}};
    for (double[] segment : segments) {
      IndoorMap map = segmentMap(segment[0], segment[1], segment[2], segment[3]);
      var plan = map.plans().getFirst();
      map.addWall(new MapWall(UUID.randomUUID(), plan.floorId(), plan.planVersionId(), 30, 50, 70, 50));
      var error = assertThrows(InvalidMapGraphException.class, () -> connect(map));
      assertEquals(GraphIssueCode.CONNECTION_CROSSES_WALL, error.issue().code());
      assertTrue(map.connections().isEmpty());
    }
    for (double[] segment : new double[][] {{20, 49, 80, 49}, {0, 50, 30, 50}, {71, 50, 90, 50}}) {
      IndoorMap map = segmentMap(segment[0], segment[1], segment[2], segment[3]);
      var plan = map.plans().getFirst();
      map.addWall(new MapWall(UUID.randomUUID(), plan.floorId(), plan.planVersionId(), 30, 50, 70, 50));
      assertDoesNotThrow(() -> connect(map));
    }
  }

  @Test
  void rejectsDiagonalAndVerticalCollinearOverlap() {
    for (double[] segment : new double[][] {{20, 20, 80, 80}, {50, 20, 50, 80}}) {
      IndoorMap map = segmentMap(segment[0], segment[1], segment[2], segment[3]);
      var plan = map.plans().getFirst();
      map.addWall(new MapWall(UUID.randomUUID(), plan.floorId(), plan.planVersionId(),
          segment[0], segment[1], segment[2], segment[3]));
      assertThrows(InvalidMapGraphException.class, () -> connect(map));
    }
  }

  @Test
  void publicationRechecksWallsAddedAfterConnections() {
    IndoorMap map = segmentMap(20, 50, 80, 50);
    var plan = map.plans().getFirst();
    map.setScale(new MapScale(plan.floorId(), plan.planVersionId(), 1));
    connect(map);
    assertTrue(map.validateForPublication().isValid());
    map.addWall(new MapWall(UUID.randomUUID(), plan.floorId(), plan.planVersionId(), 50, 0, 50, 100));
    assertEquals(GraphIssueCode.CONNECTION_CROSSES_WALL,
        map.validateForPublication().issues().getFirst().code());
    assertThrows(MapPublicationRejectedException.class, map::publish);
    assertEquals("DRAFT", map.status().name());
  }

  private IndoorMap segmentMap(double x1, double y1, double x2, double y2) {
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    UUID floor = UUID.randomUUID(), plan = UUID.randomUUID();
    map.addPlan(new PlanCoordinateSpace(floor, plan, 100, 100));
    map.addNode(UUID.randomUUID(), floor, plan, x1, y1, NodeType.ACCESS);
    map.addNode(UUID.randomUUID(), floor, plan, x2, y2, NodeType.DESTINATION);
    return map;
  }

  private void connect(IndoorMap map) {
    map.addConnection(UUID.randomUUID(), map.nodes().get(0).id(), map.nodes().get(1).id(),
        ConnectionType.CORRIDOR, 60, 60, true);
  }

  @Test
  void onlyDoorwayMayCrossWallThroughItsOpening() {
    UUID floor = UUID.randomUUID(), plan = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    map.addPlan(new PlanCoordinateSpace(floor, plan, 100, 100));
    UUID wallId = UUID.randomUUID(), left = UUID.randomUUID(), right = UUID.randomUUID();
    map.addWall(new MapWall(wallId, floor, plan, 50, 0, 50, 100));
    map.addDoor(new MapDoor(UUID.randomUUID(), wallId, .4, .6));
    map.addNode(left, floor, plan, 20, 50, NodeType.INTERSECTION);
    map.addNode(right, floor, plan, 80, 50, NodeType.INTERSECTION);
    assertThrows(InvalidMapGraphException.class, () -> map.addConnection(UUID.randomUUID(), left, right,
        ConnectionType.CORRIDOR, 60, 60, true));
    map.addConnection(UUID.randomUUID(), left, right, ConnectionType.DOORWAY, 60, 60, true);
    assertEquals(1, map.connections().size());
  }

  @Test
  void rejectsDoorWithoutWallAndWallOutsidePlan() {
    UUID floor = UUID.randomUUID(), plan = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    map.addPlan(new PlanCoordinateSpace(floor, plan, 100, 100));
    assertThrows(InvalidMapGraphException.class, () -> map.addWall(
        new MapWall(UUID.randomUUID(), floor, plan, -1, 0, 50, 50)));
    assertThrows(InvalidMapGraphException.class, () -> map.addDoor(
        new MapDoor(UUID.randomUUID(), UUID.randomUUID(), .2, .4)));
  }

  @Test
  void rejectsDoorwayThatDoesNotMeetAnOpening() {
    UUID floor = UUID.randomUUID(), plan = UUID.randomUUID();
    IndoorMap map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    map.addPlan(new PlanCoordinateSpace(floor, plan, 100, 100));
    UUID wall = UUID.randomUUID(), left = UUID.randomUUID(), right = UUID.randomUUID();
    map.addWall(new MapWall(wall, floor, plan, 50, 0, 50, 100));
    map.addDoor(new MapDoor(UUID.randomUUID(), wall, .1, .2));
    map.addNode(left, floor, plan, 20, 50, NodeType.INTERSECTION);
    map.addNode(right, floor, plan, 80, 50, NodeType.INTERSECTION);
    assertThrows(InvalidMapGraphException.class, () -> map.addConnection(UUID.randomUUID(), left, right,
        ConnectionType.DOORWAY, 60, 60, true));
  }
}

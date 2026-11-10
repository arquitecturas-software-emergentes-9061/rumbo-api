package com.uitopic.rumbo.planning.domain.model.aggregates;

import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ConnectionType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapDoor;
import com.uitopic.rumbo.planning.domain.model.valueobjects.MapWall;
import com.uitopic.rumbo.planning.domain.model.valueobjects.NodeType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MapGeometryTest {
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
}

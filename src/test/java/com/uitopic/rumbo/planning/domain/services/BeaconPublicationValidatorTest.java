package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PlanCoordinateSpace;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BeaconPublicationValidatorTest {
  @Test void acceptsActiveBeaconsInsideThePublishedFloorAndIgnoresInactiveInventory() {
    var map = map();
    var floor = map.plans().getFirst().floorId();
    assertTrue(BeaconPublicationValidator.validate(map, List.of(
        new Beacon(UUID.randomUUID(), map.siteId(), floor, "inside", 100, 100, true),
        new Beacon(UUID.randomUUID(), map.siteId(), UUID.randomUUID(), "inactive", 200, 200, false))).isValid());
  }

  @Test void identifiesEachActiveBeaconOutsideTheMapOrItsBounds() {
    var map = map();
    var floor = map.plans().getFirst().floorId();
    var beacons = List.of(
        new Beacon(UUID.randomUUID(), map.siteId(), floor, "outside", 101, 100, true),
        new Beacon(UUID.randomUUID(), map.siteId(), UUID.randomUUID(), "missing-floor", 1, 1, true),
        new Beacon(UUID.randomUUID(), UUID.randomUUID(), floor, "foreign-site", 1, 1, true));
    var result = BeaconPublicationValidator.validate(map, beacons);
    assertEquals(3, result.issues().size());
    for (int i = 0; i < beacons.size(); i++) {
      assertEquals(GraphIssueCode.INVALID_BEACON_PLACEMENT, result.issues().get(i).code());
      assertEquals(beacons.get(i).id(), result.issues().get(i).elementId());
    }
  }

  private IndoorMap map() {
    var map = new IndoorMap(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    map.addPlan(new PlanCoordinateSpace(UUID.randomUUID(), UUID.randomUUID(), 100, 100));
    return map;
  }
}

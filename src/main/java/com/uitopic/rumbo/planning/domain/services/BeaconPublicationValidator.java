package com.uitopic.rumbo.planning.domain.services;

import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphElementType;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssue;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphIssueCode;
import com.uitopic.rumbo.planning.domain.model.valueobjects.GraphValidationResult;
import java.util.ArrayList;

/** Checks active inventory against the exact floor-plan bounds being published. */
public final class BeaconPublicationValidator {
  private BeaconPublicationValidator() {}

  public static GraphValidationResult validate(IndoorMap map, Iterable<Beacon> beacons) {
    var issues = new ArrayList<GraphIssue>();
    for (Beacon beacon : beacons) {
      if (!beacon.active()) continue;
      var plans = map.plans().stream().filter(plan -> plan.floorId().equals(beacon.floorId())).toList();
      if (!map.siteId().equals(beacon.siteId()) || !beacon.placed() || plans.size() != 1
          || beacon.x() > plans.getFirst().widthUnits() || beacon.y() > plans.getFirst().heightUnits()) {
        issues.add(new GraphIssue(GraphIssueCode.INVALID_BEACON_PLACEMENT, GraphElementType.BEACON,
            beacon.id(), "Active beacon must belong to a floor and position in this map version"));
      }
    }
    return new GraphValidationResult(issues);
  }
}

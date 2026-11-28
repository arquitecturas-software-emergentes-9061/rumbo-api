package com.uitopic.rumbo.analytics.domain.model.valueobjects;

import java.util.List;
import java.util.UUID;

/**
 * Where visitors get lost or report problems (WI-US25-08), ready for the incident map of the panel.
 *
 * @param zones nodes with deviations, recalculations or reports, the busiest first
 * @param hotspots reports with a position, grouped in square cells of {@code cellSize} plan units
 * @param incidentsWithoutLocation reports with neither node nor position, which no map can place
 */
public record IncidentZones(List<Zone> zones, double cellSize, List<Hotspot> hotspots,
                            long incidentsWithoutLocation) {
  public IncidentZones {
    zones = List.copyOf(zones);
    hotspots = List.copyOf(hotspots);
  }

  public boolean hasData() { return !zones.isEmpty() || !hotspots.isEmpty() || incidentsWithoutLocation > 0; }

  /**
   * A node of a floor.
   *
   * @param topCategory most reported category, or {@code null} without reports
   */
  public record Zone(UUID floorId, String nodeReference, long deviations, long recalculations, long incidents,
                     IncidentCategory topCategory) { }

  /** Centre of a cell of a floor and the reports that fell in it. */
  public record Hotspot(UUID floorId, double x, double y, long incidents) { }
}

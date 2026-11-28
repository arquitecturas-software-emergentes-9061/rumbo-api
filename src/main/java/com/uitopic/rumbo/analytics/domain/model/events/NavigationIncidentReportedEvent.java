package com.uitopic.rumbo.analytics.domain.model.events;

import com.uitopic.rumbo.analytics.domain.model.valueobjects.IncidentCategory;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A visitor reported a problem on the way (TS-31). The registration of the report publishes it once the report is
 * stored. Analytics consolidates it for the dashboard and the incident map, and Communications warns the
 * administrator of the site. The free-text comment stays in the report: it is not part of the event.
 *
 * @param reportId identifier of the stored report, used to discard a repeated event
 * @param navigationSessionId session in which it happened, or {@code null} if it was reported outside a trip
 * @param siteId site where it happened
 * @param floorId floor, or {@code null} if unknown
 * @param nodeReference nearest node of the map, or {@code null} if unknown
 * @param category kind of problem
 * @param x horizontal coordinate on the floor plan, or {@code null} without position
 * @param y vertical coordinate on the floor plan, or {@code null} without position
 * @param reportedAt instant of the report
 */
public record NavigationIncidentReportedEvent(UUID reportId, UUID navigationSessionId, UUID siteId, UUID floorId,
                                              String nodeReference, IncidentCategory category, Double x, Double y,
                                              Instant reportedAt) {
  public NavigationIncidentReportedEvent {
    Objects.requireNonNull(reportId, "reportId is required");
    Objects.requireNonNull(siteId, "siteId is required");
    Objects.requireNonNull(category, "category is required");
    Objects.requireNonNull(reportedAt, "reportedAt is required");
    if ((x == null) != (y == null)) throw new IllegalArgumentException("A position needs both x and y");
    if (x != null && (!Double.isFinite(x) || !Double.isFinite(y))) {
      throw new IllegalArgumentException("The position must be finite");
    }
    if (x != null && floorId == null) throw new IllegalArgumentException("A position needs its floor");
    nodeReference = nodeReference == null || nodeReference.isBlank() ? null : nodeReference.trim();
  }

  public boolean hasPosition() { return x != null; }
}

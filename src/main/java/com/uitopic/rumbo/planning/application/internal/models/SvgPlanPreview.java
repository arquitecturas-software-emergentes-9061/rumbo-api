package com.uitopic.rumbo.planning.application.internal.models;

import java.util.List;

/** Read-only candidates extracted from a plan; none of these values publishes a map. */
public final class SvgPlanPreview {
  private SvgPlanPreview() {}

  public record Segment(double x1, double y1, double x2, double y2, String source) {}
  public record Label(double x, double y, String text, String category) {}
  public record ViewBox(double x, double y, double width, double height) {}
  public record Geometry(ViewBox viewBox, List<Segment> wallCandidates,
                         List<Label> roomLabels, List<String> warnings) {}
  public record PlanPoint(double x, double y) {}
  public record PlanPolygon(List<PlanPoint> outer, List<List<PlanPoint>> holes) {
    public PlanPolygon {
      outer = List.copyOf(outer);
      holes = holes.stream().map(List::copyOf).toList();
    }
  }
  public record Barrier(String kind, PlanPolygon polygon) {}
  public record Zone(String id, String name, String category,
                     double x, double y, double width, double height,
                     PlanPolygon polygon, PlanPoint labelPosition) {
    public Zone(String id, String name, String category, double x, double y, double width, double height) {
      this(id, name, category, x, y, width, height, null, new PlanPoint(x + width / 2, y + height / 2));
    }
  }
  public record Door(String id, double x1, double y1, double x2, double y2) {}
  public record CandidateNode(String id, String role, String label, double x, double y) {}
  public record CandidateLink(String id, String fromNodeId, String toNodeId,
                              String kind, double distanceUnits, boolean bidirectional) {}
  public record Topology(List<Zone> zones, List<Door> doors,
                         List<CandidateNode> nodes, List<CandidateLink> connections,
                         List<Segment> wallCandidates, List<String> warnings,
                         List<Barrier> barriers, List<PlanPolygon> walkable) {
    public Topology(List<Zone> zones, List<Door> doors, List<CandidateNode> nodes,
                    List<CandidateLink> connections, List<Segment> wallCandidates, List<String> warnings) {
      this(zones, doors, nodes, connections, wallCandidates, warnings, List.of(), List.of());
    }
  }
}

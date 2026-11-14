package com.uitopic.rumbo.planning.domain.services;

import java.util.ArrayList;
import java.util.List;

/** Geometry rules for routes inferred from an original plan. */
public final class CandidateRouteGeometry {
  private static final double EPS = 1e-6;

  public record Point(double x, double y) {}
  public record Segment(Point from, Point to) {}
  public record Polygon(List<Point> outer, List<List<Point>> holes) {
    public Polygon {
      outer = List.copyOf(outer);
      holes = holes.stream().map(List::copyOf).toList();
    }
  }
  public record Decision(String kind, String warning) {
    public boolean accepted() { return warning == null; }
  }

  private final List<Segment> walls;
  private final List<Segment> openings;
  private final List<Polygon> barriers;
  private final List<Polygon> traversable;
  private final boolean verifiedWithoutWalkable;

  public CandidateRouteGeometry(List<Segment> walls, List<Segment> openings,
      List<Polygon> barriers, List<Polygon> traversable, boolean verifiedWithoutWalkable) {
    this.walls = List.copyOf(walls);
    this.openings = List.copyOf(openings);
    this.barriers = List.copyOf(barriers);
    this.traversable = List.copyOf(traversable);
    this.verifiedWithoutWalkable = verifiedWithoutWalkable;
  }

  /** An opening must lie along one recognized wall before a wall crossing is proposed. */
  public static boolean openingFitsWall(Segment opening, List<Segment> walls) {
    return alignOpeningToWall(opening, walls) != null;
  }

  /** Projects a noisy opening onto the nearest wall that actually contains its full span. */
  public static Segment alignOpeningToWall(Segment opening, List<Segment> walls) {
    double tolerance = Math.max(.25, Math.min(length(opening) * .05, 2));
    Segment best = null;
    double bestError = Double.POSITIVE_INFINITY;
    for (Segment wall : walls) {
      double dx = wall.to().x() - wall.from().x(), dy = wall.to().y() - wall.from().y();
      double squared = dx * dx + dy * dy;
      if (squared <= EPS) continue;
      double first = ((opening.from().x() - wall.from().x()) * dx
        + (opening.from().y() - wall.from().y()) * dy) / squared;
      double second = ((opening.to().x() - wall.from().x()) * dx
        + (opening.to().y() - wall.from().y()) * dy) / squared;
      if (Math.min(first, second) < -EPS || Math.max(first, second) > 1 + EPS) continue;
      Point projectedFirst = pointAt(wall, first), projectedSecond = pointAt(wall, second);
      double error = Math.max(distance(projectedFirst, opening.from()), distance(projectedSecond, opening.to()));
      if (error <= tolerance && error < bestError && distance(projectedFirst, projectedSecond) > EPS) {
        best = new Segment(projectedFirst, projectedSecond);
        bestError = error;
      }
    }
    return best;
  }

  /** Removes a small skew only when the straightened axis still spans a verified free gap. */
  public static Segment straightenGap(Segment opening, List<Segment> walls, List<Polygon> barriers,
      double pixelSize) {
    Point middle = pointAt(opening, .5);
    double dx = (opening.to().x() - opening.from().x()) / 2;
    double dy = (opening.to().y() - opening.from().y()) / 2;
    if (Math.min(Math.abs(dx), Math.abs(dy)) > pixelSize * 1.5
        || Math.min(Math.abs(dx), Math.abs(dy)) > length(opening) * .035) return opening;
    Segment straight = Math.abs(dx) > Math.abs(dy)
      ? new Segment(new Point(middle.x() - dx, middle.y()), new Point(middle.x() + dx, middle.y()))
      : new Segment(new Point(middle.x(), middle.y() - dy), new Point(middle.x(), middle.y() + dy));
    return openingBridgesWallGap(straight, walls, barriers) ? straight : opening;
  }

  public static Point projectOnSegment(Point point, Segment segment) {
    double dx = segment.to().x() - segment.from().x(), dy = segment.to().y() - segment.from().y();
    double t = ((point.x() - segment.from().x()) * dx + (point.y() - segment.from().y()) * dy) / (dx * dx + dy * dy);
    return pointAt(segment, Math.clamp(t, 0, 1));
  }

  public static boolean touches(Segment first, Segment second) {
    return intersection(first, second) != null || collinearOverlap(first, second);
  }

  public List<Segment> crossedOpenings(Point from, Point to) {
    var route = new Segment(from, to);
    return openings.stream().filter(opening -> intersection(route, opening) != null).toList();
  }

  private static double distance(Point a, Point b) {
    return Math.hypot(a.x() - b.x(), a.y() - b.y());
  }

  /** A real doorway may be a gap between wall ends rather than an opening on one continuous wall segment. */
  public static boolean openingFitsWallGap(Segment opening, List<Segment> walls) {
    if (length(opening) <= EPS || walls.size() < 2) return false;
    double tolerance = Math.max(.5, Math.min(length(opening) * .15, 2));
    for (int first = 0; first < walls.size(); first++) {
      if (!alignedWallEnd(opening.from(), opening, walls.get(first), tolerance)) continue;
      for (int second = 0; second < walls.size(); second++) {
        if (first != second && alignedWallEnd(opening.to(), opening, walls.get(second), tolerance)) return true;
      }
    }
    return false;
  }

  /** A learned opening may span a real gap whose polygon corners are not parallel to its axis. */
  public static boolean openingBridgesWallGap(Segment opening, List<Segment> walls, List<Polygon> barriers) {
    if (length(opening) <= EPS || walls.size() < 2) return false;
    double tolerance = Math.max(.5, Math.min(length(opening) * .2, 6));
    if (barriers.stream().anyMatch(barrier -> insideBeyondEndCaps(opening, barrier, tolerance))) return false;
    for (int first = 0; first < walls.size(); first++) {
      if (!nearEnd(opening.from(), walls.get(first), tolerance)) continue;
      for (int second = 0; second < walls.size(); second++) {
        if (first != second && nearEnd(opening.to(), walls.get(second), tolerance)) return true;
      }
    }
    return false;
  }

  private static boolean nearEnd(Point point, Segment wall, double tolerance) {
    return Math.hypot(point.x() - wall.from().x(), point.y() - wall.from().y()) <= tolerance
      || Math.hypot(point.x() - wall.to().x(), point.y() - wall.to().y()) <= tolerance;
  }

  private static boolean insideBeyondEndCaps(Segment opening, Polygon barrier, double capLength) {
    List<Double> boundaries = cuts(opening, List.of(barrier));
    double cap = Math.min(capLength / length(opening), .25);
    for (int i = 1; i < boundaries.size(); i++) {
      double from = Math.max(boundaries.get(i - 1), cap);
      double to = Math.min(boundaries.get(i), 1 - cap);
      if (to - from > EPS && contains(barrier, pointAt(opening, (from + to) / 2))) return true;
    }
    return false;
  }

  private static boolean alignedWallEnd(Point end, Segment opening, Segment wall, double tolerance) {
    double wallLength = length(wall);
    if (wallLength <= EPS) return false;
    double sine = Math.abs(cross(opening.from(), opening.to(), wall.to())
      - cross(opening.from(), opening.to(), wall.from())) / (length(opening) * wallLength);
    return sine <= .2 && (Math.hypot(end.x() - wall.from().x(), end.y() - wall.from().y()) <= tolerance
      || Math.hypot(end.x() - wall.to().x(), end.y() - wall.to().y()) <= tolerance);
  }

  public Decision review(Point from, Point to, String proposedKind) {
    Segment route = new Segment(from, to);
    if (length(route) <= EPS) return reject("la conexión no tiene longitud");
    if (traversable.isEmpty() && !verifiedWithoutWalkable)
      return reject("no se confirmó ningún espacio transitable");
    if (!traversable.isEmpty() && !coveredBy(route, traversable))
      return reject("la conexión sale de los espacios transitables reconocidos");
    for (Polygon barrier : barriers) {
      if (insideForAnyPart(route, barrier)) return reject("la conexión atraviesa un muro u obstáculo reconocido");
    }
    boolean throughOpening = openings.stream().anyMatch(opening -> intersection(route, opening) != null);
    for (Segment wall : walls) {
      if (collinearOverlap(route, wall)) return reject("la conexión coincide con un muro reconocido");
      Crossing crossing = intersection(route, wall);
      if (crossing == null || crossing.alongWall() <= EPS || crossing.alongWall() >= 1 - EPS) continue;
      boolean atOpening = openings.stream().anyMatch(opening -> {
        Crossing door = intersection(route, opening);
        return door != null && Math.abs(door.alongRoute() - crossing.alongRoute()) <= EPS;
      });
      if (!atOpening) return reject("la conexión atraviesa un muro fuera de una puerta reconocida");
      throughOpening = true;
    }
    return new Decision(throughOpening ? "DOORWAY" : "CORRIDOR", null);
  }

  private static Decision reject(String reason) { return new Decision(null, reason); }

  private static double length(Segment segment) {
    return Math.hypot(segment.to().x() - segment.from().x(), segment.to().y() - segment.from().y());
  }

  private static boolean collinearOverlap(Segment route, Segment wall) {
    if (Math.abs(cross(route.from(), route.to(), wall.from())) > EPS
        || Math.abs(cross(route.from(), route.to(), wall.to())) > EPS) return false;
    double dx = route.to().x() - route.from().x(), dy = route.to().y() - route.from().y();
    double squared = dx * dx + dy * dy;
    double first = ((wall.from().x() - route.from().x()) * dx + (wall.from().y() - route.from().y()) * dy) / squared;
    double second = ((wall.to().x() - route.from().x()) * dx + (wall.to().y() - route.from().y()) * dy) / squared;
    return Math.min(1, Math.max(first, second)) - Math.max(0, Math.min(first, second)) > EPS;
  }

  private static boolean coveredBy(Segment route, List<Polygon> regions) {
    List<Double> cuts = cuts(route, regions);
    for (int i = 1; i < cuts.size(); i++) {
      if (cuts.get(i) - cuts.get(i - 1) <= EPS) continue;
      Point middle = pointAt(route, (cuts.get(i) + cuts.get(i - 1)) / 2);
      if (regions.stream().noneMatch(region -> contains(region, middle))) return false;
    }
    return true;
  }

  private static boolean insideForAnyPart(Segment route, Polygon region) {
    List<Double> cuts = cuts(route, List.of(region));
    for (int i = 1; i < cuts.size(); i++) {
      if (cuts.get(i) - cuts.get(i - 1) > EPS
          && contains(region, pointAt(route, (cuts.get(i) + cuts.get(i - 1)) / 2))) return true;
    }
    return false;
  }

  private static List<Double> cuts(Segment route, List<Polygon> regions) {
    List<Double> cuts = new ArrayList<>(List.of(0.0, 1.0));
    for (Polygon region : regions) {
      addCuts(route, region.outer(), cuts);
      for (List<Point> hole : region.holes()) addCuts(route, hole, cuts);
    }
    return cuts.stream().sorted().distinct().toList();
  }

  private static void addCuts(Segment route, List<Point> ring, List<Double> cuts) {
    for (int i = 0; i < ring.size(); i++) {
      Crossing crossing = intersection(route, new Segment(ring.get(i), ring.get((i + 1) % ring.size())));
      if (crossing != null) cuts.add(crossing.alongRoute());
    }
  }

  private static Point pointAt(Segment segment, double t) {
    return new Point(segment.from().x() + (segment.to().x() - segment.from().x()) * t,
      segment.from().y() + (segment.to().y() - segment.from().y()) * t);
  }

  private static boolean contains(Polygon polygon, Point point) {
    if (!inRing(polygon.outer(), point)) return false;
    return polygon.holes().stream().noneMatch(hole -> inRing(hole, point));
  }

  private static boolean inRing(List<Point> ring, Point point) {
    boolean inside = false;
    for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
      Point a = ring.get(i), b = ring.get(j);
      if (Math.abs(cross(a, b, point)) <= EPS && between(a, b, point)) return true;
      if ((a.y() > point.y()) != (b.y() > point.y())
          && point.x() < (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x()) inside = !inside;
    }
    return inside;
  }

  private static boolean between(Point a, Point b, Point p) {
    return p.x() >= Math.min(a.x(), b.x()) - EPS && p.x() <= Math.max(a.x(), b.x()) + EPS
      && p.y() >= Math.min(a.y(), b.y()) - EPS && p.y() <= Math.max(a.y(), b.y()) + EPS;
  }

  private static double cross(Point a, Point b, Point p) {
    return (b.x() - a.x()) * (p.y() - a.y()) - (b.y() - a.y()) * (p.x() - a.x());
  }

  private record Crossing(double alongRoute, double alongWall) {}

  private static Crossing intersection(Segment route, Segment wall) {
    double ax = route.to().x() - route.from().x(), ay = route.to().y() - route.from().y();
    double bx = wall.to().x() - wall.from().x(), by = wall.to().y() - wall.from().y();
    double det = ax * by - ay * bx;
    if (Math.abs(det) <= EPS) return null;
    double rx = wall.from().x() - route.from().x(), ry = wall.from().y() - route.from().y();
    double t = (rx * by - ry * bx) / det, u = (rx * ay - ry * ax) / det;
    return t >= -EPS && t <= 1 + EPS && u >= -EPS && u <= 1 + EPS ? new Crossing(t, u) : null;
  }
}

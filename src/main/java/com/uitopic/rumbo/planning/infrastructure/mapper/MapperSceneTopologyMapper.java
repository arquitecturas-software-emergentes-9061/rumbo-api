package com.uitopic.rumbo.planning.infrastructure.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.uitopic.rumbo.planning.application.internal.models.GeneratedTopologySimplifier;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateLink;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateNode;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Door;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Segment;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Topology;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Zone;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.Barrier;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.PlanPoint;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.PlanPolygon;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import com.uitopic.rumbo.planning.domain.services.CandidateRouteGeometry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates the scene the structural mapper returns, which is measured in the pixels of the image it analyzed, into
 * the proposal the editor reviews, measured in the units of the stored original.
 *
 * <p>Conventions the editor relies on: a space and the node that stands for it share the same identifier, so the
 * editor knows which nodes are rooms. Bounding boxes are retained for older clients; polygons are authoritative.
 */
final class MapperSceneTopologyMapper {
  static final int MAX_WALL_SEGMENTS = 5000;
  private static final Logger log = LoggerFactory.getLogger(MapperSceneTopologyMapper.class);
  private static final Set<String> REVIEWED_GRAPH_ISSUES = Set.of("dangling-edge", "edge-crosses-wall",
    "edge-outside-walkable", "isolated-node", "disconnected-graph", "no-entrance", "room-unreachable",
    "space-without-access", "opening-without-node");
  private static final int MIN_REPORTED_SEVERITY_RANK = 1; // warnings and errors; "info" is noise for the editor

  private MapperSceneTopologyMapper() {}

  static Topology toTopology(JsonNode scene, OriginalPlanBounds bounds, boolean scaleCalibrated) {
    long started = System.nanoTime();
    double imageWidth = scene.path("image_size").path("width").asDouble();
    if (imageWidth <= 0) {
      throw new IllegalArgumentException("The mapper scene has no image size");
    }
    double k = bounds.width() / imageWidth;
    List<String> warnings = new ArrayList<>();

    Map<String, Integer> counters = new HashMap<>();
    List<Zone> zones = new ArrayList<>();
    Map<String, Zone> zoneBySpace = new HashMap<>();
    for (JsonNode space : scene.path("spaces")) {
      String category = category(space);
      if (category == null) continue;
      double[] box = box(space.path("polygon").path("outer"), k);
      if (box == null) continue;
      String id = space.path("id").asText();
      String label = space.path("label").isNull() ? "" : space.path("label").asText("").strip();
      String name = label.isEmpty() ? category + " " + counters.merge(category, 1, Integer::sum) : label;
      JsonNode anchor = space.path("label_position");
      PlanPoint labelPosition = anchor.isArray() && anchor.size() == 2
        ? point(anchor, k) : new PlanPoint((box[0] + box[2]) / 2, (box[1] + box[3]) / 2);
      Zone zone = new Zone(id, name, category, box[0], box[1], box[2] - box[0], box[3] - box[1],
        polygon(space.path("polygon"), k), labelPosition);
      zones.add(zone);
      zoneBySpace.put(id, zone);
    }

    List<Segment> walls = new ArrayList<>();
    List<List<Segment>> wallRings = new ArrayList<>();
    boolean truncated = false;
    for (JsonNode wall : scene.path("walls")) {
      truncated |= !addRing(walls, wallRings, wall.path("outer"), k);
      for (JsonNode hole : wall.path("holes")) truncated |= !addRing(walls, wallRings, hole, k);
    }
    List<Barrier> barriers = new ArrayList<>();
    for (String field : List.of("walls", "obstacles", "windows")) {
      String kind = switch (field) { case "walls" -> "WALL"; case "obstacles" -> "OBSTACLE"; default -> "WINDOW"; };
      for (JsonNode element : scene.path(field)) {
        PlanPolygon shape = polygon(element, k);
        if (shape != null) barriers.add(new Barrier(kind, shape));
      }
    }
    List<PlanPolygon> walkable = new ArrayList<>();
    for (JsonNode area : scene.path("walkable")) {
      PlanPolygon shape = polygon(area.path("polygon"), k);
      if (shape != null) walkable.add(shape);
    }
    List<CandidateRouteGeometry.Segment> reliableOpenings = new ArrayList<>();
    List<CandidateRouteGeometry.Segment> routeWalls = new ArrayList<>(walls.stream()
      .map(wall -> segment(wall.x1(), wall.y1(), wall.x2(), wall.y2())).toList());
    List<CandidateRouteGeometry.Segment> recognizedWalls = List.copyOf(routeWalls);
    List<CandidateRouteGeometry.Polygon> solidBarriers = barriers.stream()
      .map(barrier -> routePolygon(barrier.polygon())).toList();
    Map<String, Integer> omittedDoors = new LinkedHashMap<>();
    Map<String, CandidateRouteGeometry.Segment> openingsById = new LinkedHashMap<>();
    List<Door> doors = new ArrayList<>();
    List<Segment> gapWalls = new ArrayList<>();
    Set<Segment> openingHosts = new HashSet<>();
    for (JsonNode opening : scene.path("openings")) {
      Door door = door(opening, k);
      if (door == null) {
        omittedDoors.merge("geometría no interpretable", 1, Integer::sum);
        continue;
      }
      if (opening.path("review_required").asBoolean(false)) {
        String reason = opening.path("review_reason").asText("su asociación con los espacios es incierta");
        omittedDoors.merge("asociación incierta con los espacios", 1, Integer::sum);
        log.debug("Excluded candidate door {}: {}", door.id(), reason);
        continue;
      }
      var openingSegment = segment(door.x1(), door.y1(), door.x2(), door.y2());
      boolean confirmedSides = recognizedSides(opening, zoneBySpace);
      var fitted = CandidateRouteGeometry.alignOpeningToWall(openingSegment, recognizedWalls);
      boolean wallFit = fitted != null;
      boolean confirmedGap = confirmedSides && CandidateRouteGeometry.openingFitsWallGap(openingSegment, recognizedWalls);
      boolean learnedGap = confirmedSides && hasSource(opening, "ml")
        && CandidateRouteGeometry.openingBridgesWallGap(openingSegment, recognizedWalls, solidBarriers);
      if ("entrance".equals(opening.path("kind").asText()) && confirmedSides
          && !wallFit && !confirmedGap && !learnedGap) {
        // An open exterior entrance is an access node, not an editable door in a wall.
        continue;
      }
      if (opening.path("confidence").asDouble(1) < 0.7 && !(confirmedSides && (wallFit || confirmedGap || learnedGap))) {
        omittedDoors.merge("detección incierta", 1, Integer::sum);
        log.debug("Excluded candidate door {}: low confidence", door.id());
        continue;
      }
      if (!wallFit && !confirmedGap && !learnedGap) {
        omittedDoors.merge("muro no compatible", 1, Integer::sum);
        log.debug("Excluded candidate door {}: no compatible wall", door.id());
        continue;
      }
      if (!wallFit && walls.size() + gapWalls.size() >= MAX_WALL_SEGMENTS) {
        omittedDoors.merge("interpretación de muros incompleta", 1, Integer::sum);
        continue;
      }
      openingSegment = wallFit ? fitted : CandidateRouteGeometry.straightenGap(openingSegment,
        recognizedWalls, solidBarriers, k);
      if (openingsById.containsKey(door.id()) || duplicateOpening(openingSegment, reliableOpenings, k)) continue;
      if (wallFit) {
        var aligned = openingSegment;
        walls.stream().filter(wall -> CandidateRouteGeometry.openingFitsWall(aligned,
          List.of(segment(wall.x1(), wall.y1(), wall.x2(), wall.y2())))).forEach(openingHosts::add);
      } else {
        // An explicit carrier gives the gap the same editable geometry in the API and editor.
        gapWalls.add(new Segment(openingSegment.from().x(), openingSegment.from().y(),
          openingSegment.to().x(), openingSegment.to().y(), "mapper"));
        routeWalls.add(openingSegment);
      }
      reliableOpenings.add(openingSegment);
      openingsById.put(door.id(), openingSegment);
      doors.add(new Door(door.id(), openingSegment.from().x(), openingSegment.from().y(),
        openingSegment.to().x(), openingSegment.to().y()));
    }
    if (reliableOpenings.isEmpty() && scene.path("walls").size() > 0)
      warnings.add("No se confirmaron puertas en los muros reconocidos; no se crearán rutas que los atraviesen");
    List<CandidateRouteGeometry.Polygon> traversable = new ArrayList<>();
    walkable.stream().map(MapperSceneTopologyMapper::routePolygon).forEach(traversable::add);
    zones.stream().filter(zone -> zone.polygon() != null)
      .map(zone -> routePolygon(zone.polygon())).forEach(traversable::add);
    var geometry = new CandidateRouteGeometry(routeWalls,
      reliableOpenings,
      solidBarriers,
      traversable, "ok".equals(scene.path("validation").path("status").asText()));
    long geometryReady = System.nanoTime();

    Map<String, String> nodeIds = new HashMap<>();
    Map<String, CandidateNode> nodesById = new HashMap<>();
    List<CandidateNode> nodes = new ArrayList<>();
    for (JsonNode node : scene.path("graph").path("nodes")) {
      String original = node.path("id").asText();
      String role = node.path("kind").asText("waypoint").toUpperCase(Locale.ROOT);
      String spaceId = node.path("space_id").isNull() ? null : node.path("space_id").asText(null);
      boolean standsForSpace = spaceId != null && zoneBySpace.containsKey(spaceId)
        && (role.equals("ROOM") || role.equals("CONNECTOR")) && !nodeIds.containsValue(spaceId);
      String id = standsForSpace ? spaceId : original;
      nodeIds.put(original, id);
      String label = standsForSpace ? zoneBySpace.get(spaceId).name() : nodeLabel(node, role);
      var position = new CandidateRouteGeometry.Point(node.path("x").asDouble() * k, node.path("y").asDouble() * k);
      var recognizedOpening = openingsById.get(node.path("opening_id").asText());
      if (recognizedOpening != null && (role.equals("DOOR") || role.equals("ENTRANCE"))) {
        position = CandidateRouteGeometry.projectOnSegment(position, recognizedOpening);
      }
      CandidateNode candidate = new CandidateNode(id, role, label, position.x(), position.y());
      if (nodesById.putIfAbsent(id, candidate) == null) nodes.add(candidate);
    }

    List<CandidateLink> connections = new ArrayList<>();
    Set<String> reviewedLinks = new HashSet<>();
    Map<String, Integer> excluded = new LinkedHashMap<>();
    long validationNanos = 0;
    for (JsonNode edge : scene.path("graph").path("edges")) {
      String from = nodeIds.get(edge.path("from").asText()), to = nodeIds.get(edge.path("to").asText());
      if (from == null || to == null || from.equals(to)) {
        excluded.merge("extremos inconsistentes", 1, Integer::sum);
        log.debug("Excluded generated route {}–{}: inconsistent endpoints", edge.path("from").asText(), edge.path("to").asText());
        continue;
      }
      CandidateNode source = nodesById.get(from), target = nodesById.get(to);
      if (source == null || target == null) continue;
      String key = from.compareTo(to) < 0 ? from + "|" + to : to + "|" + from;
      if (!reviewedLinks.add(key)) continue;
      long validationStarted = System.nanoTime();
      var decision = geometry.review(new CandidateRouteGeometry.Point(source.x(), source.y()),
        new CandidateRouteGeometry.Point(target.x(), target.y()),
        edge.path("kind").asText("corridor").toUpperCase(Locale.ROOT));
      validationNanos += System.nanoTime() - validationStarted;
      if (truncated || !decision.accepted()) {
        String reason = truncated ? "interpretación de muros incompleta" : decision.warning();
        excluded.merge(reason, 1, Integer::sum);
        log.debug("Excluded generated route {}–{}: {}", edge.path("from").asText(), edge.path("to").asText(), reason);
        continue;
      }
      double measured = edge.path("length_px").asDouble() * k;
      if (!Double.isFinite(measured) || measured <= 0)
        measured = Math.hypot(target.x() - source.x(), target.y() - source.y());
      connections.add(new CandidateLink("link-" + connections.size(), from, to,
        decision.kind(), measured, true));
    }

    var routes = connections.stream().map(link -> segment(nodesById.get(link.fromNodeId()).x(),
      nodesById.get(link.fromNodeId()).y(), nodesById.get(link.toNodeId()).x(), nodesById.get(link.toNodeId()).y())).toList();
    walls = simplifyWalls(wallRings, openingHosts, gapWalls, reliableOpenings, routes, k, truncated);
    var editableGeometry = new CandidateRouteGeometry(walls.stream().map(MapperSceneTopologyMapper::routeSegment).toList(),
      reliableOpenings, List.of(), List.of(), true);
    var compact = GeneratedTopologySimplifier.compact(nodes, connections, geometry, editableGeometry, k);
    nodes = compact.nodes();
    connections = compact.connections();
    if (!omittedDoors.isEmpty()) {
      int total = omittedDoors.values().stream().mapToInt(Integer::intValue).sum();
      String summary = "Se omitieron " + total + " posibles puertas no verificadas: "
        + omittedDoors.entrySet().stream().map(entry -> entry.getValue() + " por " + entry.getKey())
          .collect(java.util.stream.Collectors.joining("; ")) + ".";
      log.debug("{}", summary);
      if (!coversDestinations(zones, nodes, connections)) warnings.add(summary);
    }

    if (zones.isEmpty()) {
      warnings.add("No se reconocieron ambientes en el plano; revisa que sea legible y de una sola planta");
    }
    if (truncated) {
      warnings.add("El plano tiene más de " + MAX_WALL_SEGMENTS + " tramos de pared; solo se muestran los primeros");
    }
    if (!excluded.isEmpty()) {
      int total = excluded.values().stream().mapToInt(Integer::intValue).sum();
      warnings.add("Se excluyeron " + total + " conexiones candidatas incompatibles: "
        + excluded.entrySet().stream().map(entry -> entry.getValue() + " por " + entry.getKey())
          .collect(java.util.stream.Collectors.joining("; ")) + ".");
    }
    Map<String, String> remainingIssues = new LinkedHashMap<>();
    for (JsonNode issue : scene.path("validation").path("issues")) {
      if (severityRank(issue.path("severity").asText()) >= MIN_REPORTED_SEVERITY_RANK) {
        String code = issue.path("code").asText();
        // Mapper graph diagnostics refer to unreviewed edges. The reviewed graph is validated in the editor.
        if (REVIEWED_GRAPH_ISSUES.contains(code)) {
          log.debug("Superseded mapper graph diagnostic {}: {}", code, issue.path("message").asText());
          continue;
        }
        remainingIssues.putIfAbsent(code, describe(issue));
        log.debug("Mapper diagnostic {}: {}", code, issue.path("message").asText());
      }
    }
    warnings.addAll(remainingIssues.values());
    long finished = System.nanoTime();
    log.debug("Model proposal timing: geometry={}ms, graph={}ms, validation={}ms, total={}ms",
      (geometryReady - started) / 1_000_000,
      (finished - geometryReady - validationNanos) / 1_000_000,
      validationNanos / 1_000_000, (finished - started) / 1_000_000);
    return new Topology(zones, doors, nodes, connections, walls, warnings, barriers, walkable);
  }

  private static boolean coversDestinations(List<Zone> zones, List<CandidateNode> nodes, List<CandidateLink> links) {
    Map<String, List<String>> neighbors = new HashMap<>();
    for (var link : links) {
      neighbors.computeIfAbsent(link.fromNodeId(), key -> new ArrayList<>()).add(link.toNodeId());
      neighbors.computeIfAbsent(link.toNodeId(), key -> new ArrayList<>()).add(link.fromNodeId());
    }
    var reachable = new HashSet<String>();
    var queue = new ArrayList<String>();
    nodes.stream().filter(node -> node.role().equals("ENTRANCE")).findFirst()
      .ifPresent(node -> queue.add(node.id()));
    for (int i = 0; i < queue.size(); i++) {
      String id = queue.get(i);
      if (reachable.add(id)) queue.addAll(neighbors.getOrDefault(id, List.of()));
    }
    return !links.isEmpty() && nodes.stream().allMatch(node -> reachable.contains(node.id()))
      && zones.stream().filter(zone -> !zone.category().equals("CORRIDOR")).allMatch(zone -> reachable.contains(zone.id()));
  }

  /** The validator speaks English; the editor shows Spanish. Unknown codes keep the validator's own text. */
  private static String describe(JsonNode issue) {
    int count = issue.path("subjects").size();
    String subjects = count == 0 ? "Hay" : String.valueOf(count);
    return switch (issue.path("code").asText()) {
      case "dangling-edge" -> "Hay conexiones que apuntan a un nodo inexistente";
      case "edge-crosses-wall" -> subjects + " conexiones cruzan una pared u obstáculo: revísalas";
      case "edge-outside-walkable" -> subjects + " conexiones salen del espacio transitable: revísalas";
      case "isolated-node" -> subjects + " nodos no tienen conexiones";
      case "disconnected-graph" -> subjects + " nodos no se alcanzan desde la red principal: la red quedó en varias partes";
      case "no-entrance" -> "No se detectó ninguna entrada: márcala a mano para que las rutas puedan empezar";
      case "room-unreachable" -> subjects + " ambientes no se alcanzan desde ninguna entrada";
      case "space-without-access" -> subjects + " ambientes no tienen puerta detectada: agrega una apertura si son accesibles";
      case "opening-without-node" -> subjects + " aperturas no están representadas como tránsito";
      case "low-confidence-opening" -> subjects + " aperturas tienen baja confianza: verifícalas";
      default -> issue.path("message").asText(issue.path("code").asText());
    };
  }

  private static String category(JsonNode space) {
    return switch (space.path("kind").asText()) {
      case "room" -> "ROOM";
      case "circulation" -> "CORRIDOR";
      case "vertical" -> "elevator".equals(space.path("connector_kind").asText()) ? "ELEVATOR" : "STAIRS";
      default -> null; // "void" and anything new are not part of the building
    };
  }

  private static boolean recognizedSides(JsonNode opening, Map<String, Zone> zones) {
    JsonNode sides = opening.path("spaces");
    if (!sides.isArray() || sides.size() != 2) return false;
    String first = sides.get(0).isNull() ? null : sides.get(0).asText();
    String second = sides.get(1).isNull() ? null : sides.get(1).asText();
    if (first != null && !zones.containsKey(first) || second != null && !zones.containsKey(second)) return false;
    if ("entrance".equals(opening.path("kind").asText())) return (first == null) != (second == null);
    return first != null && second != null && !first.equals(second);
  }

  private static boolean hasSource(JsonNode opening, String source) {
    for (JsonNode value : opening.path("sources")) if (source.equals(value.asText())) return true;
    return false;
  }

  private static String nodeLabel(JsonNode node, String role) {
    JsonNode label = node.path("label");
    if (!label.isNull() && !label.asText("").isBlank()) return label.asText().strip();
    return switch (role) {
      case "DOOR" -> "Acceso";
      case "ENTRANCE" -> "Entrada";
      case "CONNECTOR" -> "Conector";
      case "DECISION" -> "Cruce";
      default -> "Recorrido";
    };
  }

  private static CandidateRouteGeometry.Segment segment(double x1, double y1, double x2, double y2) {
    return new CandidateRouteGeometry.Segment(new CandidateRouteGeometry.Point(x1, y1),
      new CandidateRouteGeometry.Point(x2, y2));
  }

  private static CandidateRouteGeometry.Segment routeSegment(Segment wall) {
    return segment(wall.x1(), wall.y1(), wall.x2(), wall.y2());
  }

  private static boolean duplicateOpening(CandidateRouteGeometry.Segment opening,
      List<CandidateRouteGeometry.Segment> existing, double pixelSize) {
    for (var other : existing) {
      if (near(opening.from(), other.from(), pixelSize) && near(opening.to(), other.to(), pixelSize)
          || near(opening.from(), other.to(), pixelSize) && near(opening.to(), other.from(), pixelSize)) return true;
    }
    return false;
  }

  private static boolean near(CandidateRouteGeometry.Point a, CandidateRouteGeometry.Point b, double tolerance) {
    return Math.hypot(a.x() - b.x(), a.y() - b.y()) <= tolerance;
  }

  private static List<Segment> simplifyWalls(List<List<Segment>> rings, Set<Segment> hosts,
      List<Segment> gapWalls, List<CandidateRouteGeometry.Segment> openings,
      List<CandidateRouteGeometry.Segment> routes, double pixelSize, boolean truncated) {
    List<Segment> simplified = new ArrayList<>();
    for (var ring : rings) {
      List<Segment> outline = new ArrayList<>();
      for (int first = 0; first < ring.size();) {
        Segment best = ring.get(first);
        int next = first + 1;
        if (!truncated && !hosts.contains(best)) {
          for (int end = next; end < ring.size() && end - first < 32 && !hosts.contains(ring.get(end)); end++) {
            Segment last = ring.get(end);
            Segment combined = new Segment(best.x1(), best.y1(), last.x2(), last.y2(), "mapper");
            var chord = routeSegment(combined);
            if (near(chord.from(), chord.to(), pixelSize)
                || !followsContour(ring, first, end, chord, pixelSize * 3)) break;
            if (routes.stream().noneMatch(route -> CandidateRouteGeometry.touches(route, chord))
                && openings.stream().noneMatch(opening -> CandidateRouteGeometry.touches(opening, chord))) {
              best = combined;
              next = end + 1;
            }
          }
        }
        outline.add(best);
        first = next;
      }
      // A filled polygon must keep a closed outline, including small obstacles.
      simplified.addAll(ring.size() >= 3 && outline.size() < 3 ? ring : outline);
    }
    simplified.addAll(gapWalls);
    return simplified;
  }

  private static boolean followsContour(List<Segment> ring, int first, int last,
      CandidateRouteGeometry.Segment chord, double tolerance) {
    double previous = 0;
    for (int i = first; i < last; i++) {
      var vertex = new CandidateRouteGeometry.Point(ring.get(i).x2(), ring.get(i).y2());
      var projected = CandidateRouteGeometry.projectOnSegment(vertex, chord);
      double distance = Math.hypot(projected.x() - chord.from().x(), projected.y() - chord.from().y());
      if (distance < previous || !near(vertex, projected, tolerance)) return false;
      previous = distance;
    }
    return true;
  }

  private static CandidateRouteGeometry.Polygon routePolygon(PlanPolygon polygon) {
    return new CandidateRouteGeometry.Polygon(
      polygon.outer().stream().map(point -> new CandidateRouteGeometry.Point(point.x(), point.y())).toList(),
      polygon.holes().stream().map(hole -> hole.stream()
        .map(point -> new CandidateRouteGeometry.Point(point.x(), point.y())).toList()).toList());
  }

  /** v4 supplies the exact opening axis; retain the box fallback for older mapper deployments. */
  private static Door door(JsonNode opening, double k) {
    JsonNode axis = opening.path("axis");
    if (axis.isArray() && axis.size() == 2 && validPoint(axis.get(0)) && validPoint(axis.get(1))) {
      PlanPoint from = point(axis.get(0), k), to = point(axis.get(1), k);
      if (Math.hypot(from.x() - to.x(), from.y() - to.y()) > 0)
        return new Door(opening.path("id").asText(), from.x(), from.y(), to.x(), to.y());
    }
    double[] box = box(opening.path("polygon").path("outer"), k);
    if (box == null || opening.path("width_px").asDouble() <= 0) return null;
    double cx = (box[0] + box[2]) / 2, cy = (box[1] + box[3]) / 2;
    double half = Math.max(opening.path("width_px").asDouble() * k, 0) / 2;
    boolean horizontal = (box[2] - box[0]) >= (box[3] - box[1]);
    String id = opening.path("id").asText();
    return horizontal ? new Door(id, cx - half, cy, cx + half, cy) : new Door(id, cx, cy - half, cx, cy + half);
  }

  private static boolean validPoint(JsonNode point) {
    return point.isArray() && point.size() == 2 && point.get(0).isNumber() && point.get(1).isNumber()
      && Double.isFinite(point.get(0).asDouble()) && Double.isFinite(point.get(1).asDouble());
  }

  private static PlanPoint point(JsonNode point, double k) {
    return new PlanPoint(point.get(0).asDouble() * k, point.get(1).asDouble() * k);
  }

  private static List<PlanPoint> ring(JsonNode ring, double k) {
    List<PlanPoint> points = new ArrayList<>();
    for (JsonNode vertex : ring) points.add(point(vertex, k));
    return points;
  }

  private static PlanPolygon polygon(JsonNode polygon, double k) {
    if (!polygon.path("outer").isArray() || polygon.path("outer").size() < 3) return null;
    List<List<PlanPoint>> holes = new ArrayList<>();
    for (JsonNode hole : polygon.path("holes")) {
      if (hole.isArray() && hole.size() >= 3) holes.add(ring(hole, k));
    }
    return new PlanPolygon(ring(polygon.path("outer"), k), holes);
  }

  /** @return {minX, minY, maxX, maxY} in plan units, or null when the ring has no points */
  private static double[] box(JsonNode ring, double k) {
    if (!ring.isArray() || ring.isEmpty()) return null;
    double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
    for (JsonNode point : ring) {
      double x = point.get(0).asDouble() * k, y = point.get(1).asDouble() * k;
      minX = Math.min(minX, x); minY = Math.min(minY, y); maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
    }
    return new double[]{minX, minY, maxX, maxY};
  }

  /** @return false when the cap on segments stopped it from adding the whole ring */
  private static boolean addRing(List<Segment> walls, List<List<Segment>> rings, JsonNode ring, double k) {
    int n = ring.size();
    if (n < 2) return true;
    List<Segment> segments = new ArrayList<>();
    rings.add(segments);
    for (int i = 0; i < (n == 2 ? 1 : n); i++) {
      JsonNode a = ring.get(i), b = ring.get((i + 1) % n);
      if (a.get(0).asDouble() == b.get(0).asDouble() && a.get(1).asDouble() == b.get(1).asDouble()) continue;
      if (walls.size() >= MAX_WALL_SEGMENTS) return false;
      var segment = new Segment(a.get(0).asDouble() * k, a.get(1).asDouble() * k, b.get(0).asDouble() * k,
        b.get(1).asDouble() * k, "mapper");
      walls.add(segment);
      segments.add(segment);
    }
    return true;
  }

  private static int severityRank(String severity) {
    return switch (severity) {
      case "error" -> 2;
      case "warning" -> 1;
      default -> 0;
    };
  }
}

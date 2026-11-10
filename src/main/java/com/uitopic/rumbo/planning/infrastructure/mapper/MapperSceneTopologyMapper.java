package com.uitopic.rumbo.planning.infrastructure.mapper;

import com.fasterxml.jackson.databind.JsonNode;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Translates the scene the structural mapper returns, which is measured in the pixels of the image it analyzed, into
 * the proposal the editor reviews, measured in the units of the stored original.
 *
 * <p>Conventions the editor relies on: a space and the node that stands for it share the same identifier, so the
 * editor knows which nodes are rooms. Bounding boxes are retained for older clients; polygons are authoritative.
 */
final class MapperSceneTopologyMapper {
  static final int MAX_WALL_SEGMENTS = 5000;
  private static final int MIN_REPORTED_SEVERITY_RANK = 1; // warnings and errors; "info" is noise for the editor

  private MapperSceneTopologyMapper() {}

  static Topology toTopology(JsonNode scene, OriginalPlanBounds bounds, boolean scaleCalibrated) {
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

    List<Door> doors = new ArrayList<>();
    for (JsonNode opening : scene.path("openings")) {
      Door door = door(opening, k);
      if (door != null) doors.add(door);
      else warnings.add("Una puerta detectada no tiene una geometría interpretable: revisa el plano antes de trazar rutas por ella");
    }
    if (doors.isEmpty() && (!zones.isEmpty() || scene.path("walls").size() > 0))
      warnings.add("No se detectaron puertas con geometría visible; revisa las aberturas del plano y agrégalas manualmente si corresponde");

    List<Segment> walls = new ArrayList<>();
    boolean truncated = false;
    for (JsonNode wall : scene.path("walls")) {
      truncated |= !addRing(walls, wall.path("outer"), k);
      for (JsonNode hole : wall.path("holes")) truncated |= !addRing(walls, hole, k);
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
    List<CandidateRouteGeometry.Segment> routeWalls = walls.stream()
      .map(wall -> segment(wall.x1(), wall.y1(), wall.x2(), wall.y2())).toList();
    for (JsonNode opening : scene.path("openings")) {
      Door door = door(opening, k);
      if (door == null) continue;
      if (opening.path("review_required").asBoolean(false)) {
        String reason = opening.path("review_reason").asText("su asociación con los espacios es incierta");
        warnings.add("Puerta " + door.id() + ": " + reason + "; se conserva para revisión sin proponer rutas por ella");
        continue;
      }
      if (opening.path("confidence").asDouble(0) < 0.7) {
        warnings.add("Puerta " + door.id() + " con detección incierta: se conserva para revisión, sin proponer rutas por ella");
        continue;
      }
      var openingSegment = segment(door.x1(), door.y1(), door.x2(), door.y2());
      boolean confirmedGap = recognizedSides(opening, zoneBySpace)
        && CandidateRouteGeometry.openingFitsWallGap(openingSegment, routeWalls);
      if (!CandidateRouteGeometry.openingFitsWall(openingSegment, routeWalls) && !confirmedGap) {
        warnings.add("Puerta " + door.id() + " sin un muro compatible: se conserva para revisión, sin proponer rutas por ella");
        continue;
      }
      reliableOpenings.add(openingSegment);
    }
    List<CandidateRouteGeometry.Polygon> traversable = new ArrayList<>();
    walkable.stream().map(MapperSceneTopologyMapper::routePolygon).forEach(traversable::add);
    zones.stream().filter(zone -> zone.polygon() != null)
      .map(zone -> routePolygon(zone.polygon())).forEach(traversable::add);
    var geometry = new CandidateRouteGeometry(routeWalls,
      reliableOpenings,
      barriers.stream().map(barrier -> routePolygon(barrier.polygon())).toList(),
      traversable, "ok".equals(scene.path("validation").path("status").asText()));

    Map<String, String> nodeIds = new HashMap<>();
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
      nodes.add(new CandidateNode(id, role, label, node.path("x").asDouble() * k, node.path("y").asDouble() * k));
    }

    List<CandidateLink> connections = new ArrayList<>();
    for (JsonNode edge : scene.path("graph").path("edges")) {
      String from = nodeIds.get(edge.path("from").asText()), to = nodeIds.get(edge.path("to").asText());
      if (from == null || to == null || from.equals(to)) {
        warnings.add("Conexión candidata con extremos inconsistentes: revisa los nodos reconocidos");
        continue;
      }
      CandidateNode source = nodes.stream().filter(node -> node.id().equals(from)).findFirst().orElseThrow();
      CandidateNode target = nodes.stream().filter(node -> node.id().equals(to)).findFirst().orElseThrow();
      var decision = geometry.review(new CandidateRouteGeometry.Point(source.x(), source.y()),
        new CandidateRouteGeometry.Point(target.x(), target.y()),
        edge.path("kind").asText("corridor").toUpperCase(Locale.ROOT));
      if (truncated || !decision.accepted()) {
        warnings.add("Conexión candidata " + edge.path("from").asText() + "–" + edge.path("to").asText()
          + ": " + (truncated ? "la interpretación de muros está incompleta" : decision.warning())
          + "; revisa el plano antes de trazar esa ruta");
        continue;
      }
      connections.add(new CandidateLink("link-" + connections.size(), from, to,
        decision.kind(), edge.path("length_px").asDouble() * k, true));
    }

    if (zones.isEmpty()) {
      warnings.add("No se reconocieron ambientes en el plano; revisa que sea legible y de una sola planta");
    }
    if (truncated) {
      warnings.add("El plano tiene más de " + MAX_WALL_SEGMENTS + " tramos de pared; solo se muestran los primeros");
    }
    for (JsonNode issue : scene.path("validation").path("issues")) {
      if (severityRank(issue.path("severity").asText()) >= MIN_REPORTED_SEVERITY_RANK) {
        warnings.add(describe(issue));
      }
    }
    if (!scaleCalibrated) {
      warnings.add("La escala se estimó automáticamente: calibra una distancia real antes de aplicar la propuesta");
    }
    warnings.add("Propuesta automática no publicada: revisa paredes, puertas, nombres, accesibilidad y conexiones");
    return new Topology(zones, doors, nodes, connections, walls, warnings, barriers, walkable);
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
  private static boolean addRing(List<Segment> walls, JsonNode ring, double k) {
    int n = ring.size();
    if (n < 2) return true;
    for (int i = 0; i < n; i++) {
      JsonNode a = ring.get(i), b = ring.get((i + 1) % n);
      if (a.get(0).asDouble() == b.get(0).asDouble() && a.get(1).asDouble() == b.get(1).asDouble()) continue;
      if (walls.size() >= MAX_WALL_SEGMENTS) return false;
      walls.add(new Segment(a.get(0).asDouble() * k, a.get(1).asDouble() * k, b.get(0).asDouble() * k,
        b.get(1).asDouble() * k, "mapper"));
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

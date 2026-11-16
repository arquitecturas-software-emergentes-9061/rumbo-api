package com.uitopic.rumbo.planning.application.internal.models;

import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateLink;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview.CandidateNode;
import com.uitopic.rumbo.planning.domain.services.CandidateRouteGeometry;
import com.uitopic.rumbo.planning.domain.services.CandidateRouteGeometry.Point;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Compacts a fresh mapper proposal while keeping spaces, entrances and vertical anchors. */
public final class GeneratedTopologySimplifier {
  public record Result(List<CandidateNode> nodes, List<CandidateLink> connections) {}

  private GeneratedTopologySimplifier() {}

  public static Result compact(List<CandidateNode> nodes, List<CandidateLink> connections,
      CandidateRouteGeometry original, CandidateRouteGeometry editable, double pixelSize) {
    Map<String, CandidateNode> remaining = new LinkedHashMap<>();
    Map<String, List<CandidateLink>> incident = new LinkedHashMap<>();
    for (var node : nodes) {
      remaining.put(node.id(), node);
      incident.put(node.id(), new ArrayList<>());
    }
    for (var link : connections) add(incident, link);
    List<CandidateLink> ordered = new ArrayList<>(connections);
    int nextId = 0;
    for (var node : nodes) {
      if (!auxiliary(node) || !remaining.containsKey(node.id())) continue;
      var duplicate = remaining.values().stream()
        .filter(other -> auxiliary(other) && other != node && node.x() == other.x() && node.y() == other.y())
        .findFirst();
      if (duplicate.isPresent()) nextId = merge(node, duplicate.get(), remaining, incident, ordered, nextId);
    }
    var semanticNetwork = new HashSet<String>();
    var queue = new ArrayList<String>();
    remaining.values().stream().filter(node -> !auxiliary(node)).forEach(node -> queue.add(node.id()));
    for (int i = 0; i < queue.size(); i++) {
      var node = remaining.get(queue.get(i));
      if (!semanticNetwork.add(node.id())) continue;
      for (var link : incident.get(node.id())) queue.add(otherEnd(link, node));
    }
    boolean changed;
    do {
      changed = false;
      for (var node : List.copyOf(remaining.values())) {
        List<CandidateLink> links = incident.get(node.id());
        boolean door = node.role().equals("DOOR");
        if (!auxiliary(node) && !door || links.isEmpty()) continue;
        if (links.size() == 1) {
          var link = links.getFirst();
          var neighbor = remaining.get(otherEnd(link, node));
          if (!door && incident.get(neighbor.id()).size() > 1 && link.kind().equals("CORRIDOR")
              && (semanticNetwork.contains(node.id()) || distance(node, neighbor) <= pixelSize * 24)) {
            remove(incident, link);
            remaining.remove(node.id());
            changed = true;
          }
          continue;
        }
        if (auxiliary(node)) {
          var neighbor = nearbyJunction(node, links, remaining, incident, original, editable, pixelSize * 18);
          if (neighbor != null) {
            nextId = merge(node, neighbor, remaining, incident, ordered, nextId);
            changed = true;
            continue;
          }
        }
        if (links.size() == 2) {
          var first = links.get(0);
          var second = links.get(1);
          var from = remaining.get(otherEnd(first, node));
          var to = remaining.get(otherEnd(second, node));
          if (incident.get(from.id()).stream().anyMatch(link -> otherEnd(link, from).equals(to.id()))) continue;
          String kind = first.kind().equals("DOORWAY") || second.kind().equals("DOORWAY") ? "DOORWAY" : "CORRIDOR";
          if (door && !kind.equals("DOORWAY")) continue;
          if (kind.equals("DOORWAY") && !sameOpenings(from, node, to, original)) continue;
          if (!safe(from, to, kind, original, editable)) continue;
          remove(incident, first);
          remove(incident, second);
          remaining.remove(node.id());
          var replacement = new CandidateLink("compact-" + nextId++, from.id(), to.id(), kind, distance(from, to), true);
          add(incident, replacement);
          ordered.add(replacement);
          changed = true;
        }
      }
    } while (changed);
    if (incident.values().stream().anyMatch(links -> !links.isEmpty())) {
      remaining.values().removeIf(node -> auxiliary(node) && incident.get(node.id()).isEmpty());
    }
    return new Result(List.copyOf(remaining.values()), ordered.stream()
      .filter(link -> incident.get(link.fromNodeId()).contains(link)).toList());
  }

  private static int merge(CandidateNode node, CandidateNode keep, Map<String, CandidateNode> nodes,
      Map<String, List<CandidateLink>> incident, List<CandidateLink> ordered, int nextId) {
    for (var link : List.copyOf(incident.get(node.id()))) {
      var endpoint = nodes.get(otherEnd(link, node));
      remove(incident, link);
      if (endpoint == keep || incident.get(keep.id()).stream().anyMatch(edge -> otherEnd(edge, keep).equals(endpoint.id()))) continue;
      var replacement = new CandidateLink("compact-" + nextId++, keep.id(), endpoint.id(),
        link.kind(), distance(keep, endpoint), true);
      add(incident, replacement);
      ordered.add(replacement);
    }
    nodes.remove(node.id());
    return nextId;
  }

  private static boolean auxiliary(CandidateNode node) {
    return node.role().equals("WAYPOINT") || node.role().equals("DECISION");
  }

  private static boolean sameOpenings(CandidateNode from, CandidateNode through, CandidateNode to,
      CandidateRouteGeometry geometry) {
    var a = new Point(from.x(), from.y());
    var b = new Point(through.x(), through.y());
    var c = new Point(to.x(), to.y());
    var previous = new HashSet<>(geometry.crossedOpenings(a, b));
    previous.addAll(geometry.crossedOpenings(b, c));
    // The graph has one DOORWAY edge type. Keep separate segments when several openings are involved.
    return previous.size() == 1 && previous.containsAll(geometry.crossedOpenings(a, c));
  }

  private static CandidateNode nearbyJunction(CandidateNode node, List<CandidateLink> links,
      Map<String, CandidateNode> nodes, Map<String, List<CandidateLink>> incident,
      CandidateRouteGeometry original, CandidateRouteGeometry editable, double tolerance) {
    for (var joining : links) {
      var neighbor = nodes.get(otherEnd(joining, node));
      if (!auxiliary(neighbor) || !joining.kind().equals("CORRIDOR") || distance(node, neighbor) > tolerance) continue;
      var existing = incident.get(neighbor.id());
      boolean safeToMerge = links.stream().filter(link -> link != joining).allMatch(link -> {
        var endpoint = nodes.get(otherEnd(link, node));
        var parallel = existing.stream().filter(edge -> otherEnd(edge, neighbor).equals(endpoint.id())).findFirst();
        if (parallel.isPresent()) return parallel.get().kind().equals(link.kind())
          && (!link.kind().equals("DOORWAY") || sameOpenings(neighbor, node, endpoint, original));
        if (!safe(neighbor, endpoint, link.kind(), original, editable)) return false;
        return !link.kind().equals("DOORWAY") || sameOpenings(neighbor, node, endpoint, original);
      });
      if (safeToMerge) return neighbor;
    }
    return null;
  }

  private static boolean safe(CandidateNode from, CandidateNode to, String kind,
      CandidateRouteGeometry original, CandidateRouteGeometry editable) {
    Point a = new Point(from.x(), from.y()), b = new Point(to.x(), to.y());
    return kind.equals(original.review(a, b, kind).kind()) && kind.equals(editable.review(a, b, kind).kind());
  }

  private static String otherEnd(CandidateLink link, CandidateNode node) {
    return link.fromNodeId().equals(node.id()) ? link.toNodeId() : link.fromNodeId();
  }

  private static void add(Map<String, List<CandidateLink>> incident, CandidateLink link) {
    incident.get(link.fromNodeId()).add(link);
    incident.get(link.toNodeId()).add(link);
  }

  private static void remove(Map<String, List<CandidateLink>> incident, CandidateLink link) {
    incident.get(link.fromNodeId()).remove(link);
    incident.get(link.toNodeId()).remove(link);
  }

  private static double distance(CandidateNode from, CandidateNode to) {
    return Math.hypot(from.x() - to.x(), from.y() - to.y());
  }
}

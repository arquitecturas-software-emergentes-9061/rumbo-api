package com.uitopic.rumbo.planning.infrastructure.svg;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * PR-142 / PR-143 / PR-159: editable topology candidate for annotated vector plans.
 *
 * The SVG can explicitly mark rectangular zones with data-rumbo-role
 * (room, corridor, service) and line-based apertures with role door.
 * Connections are only proposed where a door lies on the shared boundary
 * of exactly two zones. The service does not infer doors from wall gaps and
 * never publishes a real navigable graph.
 */
public final class SvgRoomTopologyProposalService {
  private static final double EPS = 1e-6;
  private static final Set<String> ZONE_ROLES = Set.of("room", "corridor", "service");

  public record Zone(String id, String name, String category,
                     double x, double y, double width, double height) {}
  public record Door(String id, double x1, double y1, double x2, double y2) {}
  public record CandidateNode(String id, String role, String label, double x, double y) {}
  public record CandidateLink(String id, String fromNodeId, String toNodeId,
                              String kind, double distanceUnits, boolean bidirectional) {}
  public record Proposal(SvgGeometryInterpreter.ViewBox viewBox, List<Zone> zones, List<Door> doors,
                         List<CandidateNode> nodes, List<CandidateLink> connections,
                         List<SvgGeometryInterpreter.Segment> wallCandidates,
                         List<String> warnings) {
    public Proposal {
      zones = List.copyOf(zones); doors = List.copyOf(doors);
      nodes = List.copyOf(nodes); connections = List.copyOf(connections);
      wallCandidates = List.copyOf(wallCandidates); warnings = List.copyOf(warnings);
    }
  }

  private final SvgGeometryInterpreter base = new SvgGeometryInterpreter();

  public Proposal propose(String svg) {
    return propose(SvgGeometryInterpreter.readXml(svg));
  }

  Proposal propose(Document doc) {
    var interpretation = base.interpret(doc); // Validates transforms and geometry.
    NodeList elements = doc.getDocumentElement().getElementsByTagName("*");
    var zones = new ArrayList<Zone>();
    var doors = new ArrayList<Door>();
    var warnings = new ArrayList<String>(interpretation.warnings());

    for (int i = 0; i < elements.getLength(); i++) {
      Element element = (Element) elements.item(i);
      String role = element.getAttribute("data-rumbo-role").strip().toLowerCase(Locale.ROOT);
      String tag = SvgGeometryInterpreter.localName(element);
      if ("rect".equals(tag) && ZONE_ROLES.contains(role)) {
        double x = SvgGeometryInterpreter.number(element, "x", 0);
        double y = SvgGeometryInterpreter.number(element, "y", 0);
        double w = SvgGeometryInterpreter.number(element, "width", 0);
        double h = SvgGeometryInterpreter.number(element, "height", 0);
        if (w <= 0 || h <= 0 || !Double.isFinite(x + w) || !Double.isFinite(y + h))
          throw new SvgGeometryInterpreter.InvalidSvg("Área de recinto inválida");
        String name = nearestLabel(interpretation.roomLabels(), x, y, w, h);
        String category = name == null ? role.toUpperCase(Locale.ROOT) :
          SvgGeometryInterpreter.categorize(name);
        if ("UNCLASSIFIED".equals(category)) category = role.toUpperCase(Locale.ROOT);
        if (name == null) {
          name = role.toUpperCase(Locale.ROOT) + " " + (zones.size() + 1);
          warnings.add("Zona " + name + " sin etiqueta: revisar clasificación antes de publicar");
        }
        zones.add(new Zone("zone-" + zones.size(), name, category, x, y, w, h));
      } else if ("line".equals(tag) && "door".equals(role)) {
        double x1 = SvgGeometryInterpreter.number(element, "x1", 0);
        double y1 = SvgGeometryInterpreter.number(element, "y1", 0);
        double x2 = SvgGeometryInterpreter.number(element, "x2", 0);
        double y2 = SvgGeometryInterpreter.number(element, "y2", 0);
        if (Math.hypot(x2 - x1, y2 - y1) <= EPS)
          throw new SvgGeometryInterpreter.InvalidSvg("Puerta sin longitud");
        doors.add(new Door("door-" + doors.size(), x1, y1, x2, y2));
      }
    }

    var nodes = new ArrayList<CandidateNode>();
    var links = new ArrayList<CandidateLink>();
    for (Zone zone : zones) {
      nodes.add(new CandidateNode(zone.id(), zone.category(), zone.name(),
        zone.x() + zone.width() / 2, zone.y() + zone.height() / 2));
    }
    for (Door door : doors) {
      double x = (door.x1() + door.x2()) / 2;
      double y = (door.y1() + door.y2()) / 2;
      var touching = zones.stream().filter(zone -> touchesBoundary(zone, door)).toList();
      if (touching.size() != 2) {
        warnings.add("Puerta " + door.id() + " conecta " + touching.size()
          + " zonas; no se generó una conexión ambigua");
        continue;
      }
      CandidateNode entry = new CandidateNode(door.id(), "DOOR", "Acceso", x, y);
      nodes.add(entry);
      for (Zone zone : touching) {
        double distance = Math.hypot(zone.x() + zone.width() / 2 - x,
          zone.y() + zone.height() / 2 - y);
        if (distance <= EPS) {
          warnings.add("Zona " + zone.id() + " con centro coincidente con acceso");
          continue;
        }
        links.add(new CandidateLink("link-" + links.size(), zone.id(),
          door.id(), "door", distance, true));
      }
    }
    if (zones.isEmpty()) warnings.add("No hay zonas semánticas; un SVG sin anotaciones no permite deducir recintos con certeza");
    if (!zones.isEmpty() && links.isEmpty())
      warnings.add("No hay conexiones verificables entre zonas; no inventar rutas por proximidad");
    warnings.add("Propuesta no publicada: revisar paredes, anchos de puertas, accesibilidad, escala y topología");
    return new Proposal(interpretation.viewBox(), zones, doors, nodes, links, interpretation.wallCandidates(), warnings);
  }

  private static String nearestLabel(List<SvgGeometryInterpreter.Label> labels,
                                     double x, double y, double w, double h) {
    double cx = x + w / 2, cy = y + h / 2;
    return labels.stream()
      .filter(label -> label.x() >= x && label.x() <= x + w
        && label.y() >= y && label.y() <= y + h)
      .min(Comparator.comparingDouble(label -> Math.hypot(label.x() - cx, label.y() - cy)))
      .map(SvgGeometryInterpreter.Label::text).orElse(null);
  }

  private static boolean touchesBoundary(Zone zone, Door door) {
    double minX = zone.x(), maxX = zone.x() + zone.width();
    double minY = zone.y(), maxY = zone.y() + zone.height();
    boolean vertical = Math.abs(door.x1() - door.x2()) <= EPS;
    boolean horizontal = Math.abs(door.y1() - door.y2()) <= EPS;
    if (vertical) {
      boolean onSide = Math.abs(door.x1() - minX) <= EPS || Math.abs(door.x1() - maxX) <= EPS;
      return onSide && door.y1() >= minY - EPS && door.y1() <= maxY + EPS
        && door.y2() >= minY - EPS && door.y2() <= maxY + EPS;
    }
    if (horizontal) {
      boolean onSide = Math.abs(door.y1() - minY) <= EPS || Math.abs(door.y1() - maxY) <= EPS;
      return onSide && door.x1() >= minX - EPS && door.x1() <= maxX + EPS
        && door.x2() >= minX - EPS && door.x2() <= maxX + EPS;
    }
    return false;
  }
}

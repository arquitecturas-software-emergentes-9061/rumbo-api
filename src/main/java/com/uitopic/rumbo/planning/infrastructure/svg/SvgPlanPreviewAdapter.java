package com.uitopic.rumbo.planning.infrastructure.svg;

import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidSvgPlanException;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.application.internal.outboundservices.SvgPlanPreviewPort;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Translates the SVG parser's output into Planning's application preview contract. */
@Component
public class SvgPlanPreviewAdapter implements SvgPlanPreviewPort {
  private final SvgGeometryInterpreter geometry = new SvgGeometryInterpreter();
  private final SvgRoomTopologyProposalService topology = new SvgRoomTopologyProposalService();

  @Override
  public SvgPlanPreview.Geometry interpret(String svg) {
    try {
      var result = geometry.interpret(svg);
      var box = result.viewBox() == null ? null : new SvgPlanPreview.ViewBox(
          result.viewBox().x(), result.viewBox().y(), result.viewBox().width(), result.viewBox().height());
      return new SvgPlanPreview.Geometry(box, segments(result.wallCandidates()),
          result.roomLabels().stream().map(label -> new SvgPlanPreview.Label(
              label.x(), label.y(), label.text(), label.category())).toList(), result.warnings());
    } catch (SvgGeometryInterpreter.InvalidSvg error) {
      throw new InvalidSvgPlanException(error.getMessage());
    }
  }

  @Override
  public SvgPlanPreview.Topology proposeTopology(String svg) {
    try {
      return mapped(topology.propose(svg), Projection.IDENTITY);
    } catch (SvgGeometryInterpreter.InvalidSvg error) {
      throw new InvalidSvgPlanException(error.getMessage());
    }
  }

  @Override
  public SvgPlanPreview.Topology proposeStoredTopology(byte[] svg, OriginalPlanBounds bounds) {
    try {
      var document = SvgGeometryInterpreter.readXml(svg);
      var root = document.getDocumentElement();
      if (root.getElementsByTagNameNS("*", "svg").getLength() > 0) {
        throw new SvgGeometryInterpreter.InvalidSvg("La propuesta requiere un solo espacio de coordenadas SVG");
      }
      var result = topology.propose(document);
      return mapped(result, Projection.from(result.viewBox(), bounds,
        root.getAttribute("preserveAspectRatio")));
    } catch (SvgGeometryInterpreter.InvalidSvg error) {
      throw new InvalidSvgPlanException(error.getMessage());
    }
  }

  private static SvgPlanPreview.Topology mapped(SvgRoomTopologyProposalService.Proposal result,
      Projection projection) {
    var nodes = result.nodes().stream().map(node -> new SvgPlanPreview.CandidateNode(node.id(),
      node.role(), node.label(), projection.x(node.x()), projection.y(node.y()))).toList();
    var byId = nodes.stream().collect(Collectors.toMap(SvgPlanPreview.CandidateNode::id, Function.identity()));
    var links = result.connections().stream().map(link -> {
      var from = byId.get(link.fromNodeId());
      var to = byId.get(link.toNodeId());
      double distance = Math.hypot(to.x() - from.x(), to.y() - from.y());
      return new SvgPlanPreview.CandidateLink(link.id(), link.fromNodeId(), link.toNodeId(),
        link.kind(), distance, link.bidirectional());
    }).toList();
    return new SvgPlanPreview.Topology(
      result.zones().stream().map(zone -> new SvgPlanPreview.Zone(zone.id(), zone.name(), zone.category(),
        projection.x(zone.x()), projection.y(zone.y()), zone.width() * projection.sx(),
        zone.height() * projection.sy())).toList(),
      result.doors().stream().map(door -> new SvgPlanPreview.Door(door.id(), projection.x(door.x1()),
        projection.y(door.y1()), projection.x(door.x2()), projection.y(door.y2()))).toList(),
      nodes, links,
      result.wallCandidates().stream().map(line -> new SvgPlanPreview.Segment(
        projection.x(line.x1()), projection.y(line.y1()), projection.x(line.x2()),
        projection.y(line.y2()), line.source())).toList(),
      result.warnings());
  }

  private record Projection(double sx, double sy, double tx, double ty) {
    static final Projection IDENTITY = new Projection(1, 1, 0, 0);

    double x(double x) { return x * sx + tx; }
    double y(double y) { return y * sy + ty; }

    static Projection from(SvgGeometryInterpreter.ViewBox box, OriginalPlanBounds bounds, String aspect) {
      if (box == null) return IDENTITY;
      String alignment = aspect.isBlank() ? "xMidYMid" : aspect.strip();
      double sx = bounds.width() / box.width(), sy = bounds.height() / box.height();
      if (!Double.isFinite(sx) || !Double.isFinite(sy) || sx > 1e12 || sy > 1e12) {
        throw new SvgGeometryInterpreter.InvalidSvg("El viewBox SVG tiene una escala fuera de rango");
      }
      if (alignment.equals("none")) return new Projection(sx, sy, -box.x() * sx, -box.y() * sy);
      if (!alignment.matches("x(Min|Mid|Max)Y(Min|Mid|Max)(\\s+(meet|slice))?")) {
        throw new SvgGeometryInterpreter.InvalidSvg("Relación de aspecto SVG no compatible");
      }
      double scale = alignment.endsWith("slice") ? Math.max(sx, sy) : Math.min(sx, sy);
      double tx = (bounds.width() - box.width() * scale) * align(alignment.substring(1, 4));
      double ty = (bounds.height() - box.height() * scale) * align(alignment.substring(5, 8));
      return new Projection(scale, scale, tx - box.x() * scale, ty - box.y() * scale);
    }

    private static double align(String alignment) {
      return switch (alignment) {
        case "Min" -> 0;
        case "Mid" -> .5;
        default -> 1;
      };
    }
  }

  private static List<SvgPlanPreview.Segment> segments(List<SvgGeometryInterpreter.Segment> segments) {
    return segments.stream().map(segment -> new SvgPlanPreview.Segment(segment.x1(),
        segment.y1(), segment.x2(), segment.y2(), segment.source())).toList();
  }
}

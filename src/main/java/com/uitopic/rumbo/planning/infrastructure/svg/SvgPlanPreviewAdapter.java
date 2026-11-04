package com.uitopic.rumbo.planning.infrastructure.svg;

import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidSvgPlanException;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.application.internal.outboundservices.SvgPlanPreviewPort;
import java.util.List;
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
      var result = topology.propose(svg);
      return new SvgPlanPreview.Topology(
          result.zones().stream().map(zone -> new SvgPlanPreview.Zone(zone.id(), zone.name(),
              zone.category(), zone.x(), zone.y(), zone.width(), zone.height())).toList(),
          result.doors().stream().map(door -> new SvgPlanPreview.Door(door.id(), door.x1(),
              door.y1(), door.x2(), door.y2())).toList(),
          result.nodes().stream().map(node -> new SvgPlanPreview.CandidateNode(node.id(),
              node.role(), node.label(), node.x(), node.y())).toList(),
          result.connections().stream().map(link -> new SvgPlanPreview.CandidateLink(link.id(),
              link.fromNodeId(), link.toNodeId(), link.kind(), link.distanceUnits(),
              link.bidirectional())).toList(),
          segments(result.wallCandidates()), result.warnings());
    } catch (SvgGeometryInterpreter.InvalidSvg error) {
      throw new InvalidSvgPlanException(error.getMessage());
    }
  }

  private static List<SvgPlanPreview.Segment> segments(List<SvgGeometryInterpreter.Segment> segments) {
    return segments.stream().map(segment -> new SvgPlanPreview.Segment(segment.x1(),
        segment.y1(), segment.x2(), segment.y2(), segment.source())).toList();
  }
}

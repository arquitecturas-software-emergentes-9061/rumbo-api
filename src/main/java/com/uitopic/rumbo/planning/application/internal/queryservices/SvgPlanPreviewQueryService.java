package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.application.internal.outboundservices.SvgPlanPreviewPort;
import org.springframework.stereotype.Service;

/** Coordinates read-only SVG preview requests without exposing parser details to REST. */
@Service
public class SvgPlanPreviewQueryService {
  private final SvgPlanPreviewPort preview;

  public SvgPlanPreviewQueryService(SvgPlanPreviewPort preview) { this.preview = preview; }

  public SvgPlanPreview.Geometry interpret(String svg) { return preview.interpret(svg); }

  public SvgPlanPreview.Topology proposeTopology(String svg) { return preview.proposeTopology(svg); }
}

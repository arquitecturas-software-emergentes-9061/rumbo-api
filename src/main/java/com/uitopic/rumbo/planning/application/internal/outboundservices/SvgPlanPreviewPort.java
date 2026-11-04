package com.uitopic.rumbo.planning.application.internal.outboundservices;

import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;

/** Technology-independent boundary for reading candidate geometry from an SVG. */
public interface SvgPlanPreviewPort {
  SvgPlanPreview.Geometry interpret(String svg);
  SvgPlanPreview.Topology proposeTopology(String svg);
}

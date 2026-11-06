package com.uitopic.rumbo.planning.application.internal.outboundservices;

import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;

/** Technology-independent boundary for reading candidate geometry from an SVG. */
public interface SvgPlanPreviewPort {
  SvgPlanPreview.Geometry interpret(String svg);
  SvgPlanPreview.Topology proposeTopology(String svg);
  SvgPlanPreview.Topology proposeStoredTopology(byte[] svg, OriginalPlanBounds bounds);
}

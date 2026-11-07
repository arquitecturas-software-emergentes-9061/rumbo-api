package com.uitopic.rumbo.planning.application.internal.outboundservices;

import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;

import java.util.Optional;

/**
 * Technology-independent boundary for reading walls, openings, spaces and a route network from a stored plan
 * original of any supported format. Coordinates come back in the units of {@code bounds}, so the proposal lines up
 * with the original the same way an annotated SVG proposal does. The result is a candidate, never a published map.
 */
public interface StructuralPlanInterpreterPort {
  /**
   * @param metersPerUnit calibrated scale of the plan version, or {@code null} when it was not calibrated
   * @return the proposal, or empty when the interpreter is turned off, unreachable or could not read the file; the
   * caller then keeps showing the original as a preview
   */
  Optional<SvgPlanPreview.Topology> propose(byte[] original, FloorPlanFile.Format format, OriginalPlanBounds bounds,
                                            Double metersPerUnit);
}

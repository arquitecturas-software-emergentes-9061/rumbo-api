package com.uitopic.rumbo.planning.infrastructure.mapper;

import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.application.internal.outboundservices.StructuralPlanInterpreterPort;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Used while the structural mapper is not enabled: every plan other than an annotated SVG stays a preview. */
@Component
@ConditionalOnProperty(name = "rumbo.planning.structural-mapper.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledStructuralPlanInterpreter implements StructuralPlanInterpreterPort {
  @Override
  public Optional<SvgPlanPreview.Topology> propose(byte[] original, FloorPlanFile.Format format,
                                                   OriginalPlanBounds bounds, Double metersPerUnit) {
    return Optional.empty();
  }
}

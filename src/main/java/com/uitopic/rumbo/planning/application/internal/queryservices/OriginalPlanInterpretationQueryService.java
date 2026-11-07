package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.application.internal.FloorPlanVersionService;
import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidSvgPlanException;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.application.internal.outboundservices.StructuralPlanInterpreterPort;
import com.uitopic.rumbo.planning.application.internal.outboundservices.SvgPlanPreviewPort;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile.Format;
import com.uitopic.rumbo.planning.domain.model.valueobjects.ScaleReference;
import com.uitopic.rumbo.planning.domain.repositories.ScaleReferenceRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OriginalPlanInterpretationQueryService {
  private final FloorPlanVersionService files;
  private final ScaleReferenceRepository scales;
  private final SvgPlanPreviewPort previews;
  private final StructuralPlanInterpreterPort structural;

  public OriginalPlanInterpretationQueryService(FloorPlanVersionService files,
      ScaleReferenceRepository scales, SvgPlanPreviewPort previews, StructuralPlanInterpreterPort structural) {
    this.files = files;
    this.scales = scales;
    this.previews = previews;
    this.structural = structural;
  }

  public enum Processing { SVG_CANDIDATES, PREVIEW_ONLY }
  public enum ScaleStatus { CONFIRMED, PROVISIONAL }

  public record Result(UUID versionId, UUID floorId, String mediaType, Processing processing,
                       ScaleStatus scaleStatus, Double metersPerUnit, SvgPlanPreview.Topology proposal) {}

  public Result inspect(UUID owner, UUID site, UUID floor, UUID version) {
    var original = files.original(owner, site, floor, version);
    var format = original.version().file().format();
    var scale = scales.find(site, version).filter(value -> value.floorId().equals(floor))
      .map(ScaleReference::metersPerUnit).orElse(null);
    var bounds = files.bounds(original);
    SvgPlanPreview.Topology proposal = null;
    InvalidSvgPlanException unsupportedSvg = null;
    if (format == Format.SVG) {
      try {
        proposal = previews.proposeStoredTopology(original.bytes(), bounds);
      } catch (InvalidSvgPlanException failure) {
        // The restricted annotation parser cannot read every valid SVG (e.g. percentage
        // dimensions). Let the structural adapter interpret its rendered geometry.
        unsupportedSvg = failure;
      }
    }
    // An SVG with annotated zones is read exactly. Everything else, such as PDF, PNG, JPEG or an SVG without
    // annotations, goes to the structural mapper; if it is off or fails, the plan stays a preview.
    if (proposal == null || proposal.zones().isEmpty()) {
      var inferred = structural.propose(original.bytes(), format, bounds, scale);
      if (inferred.isPresent() && !inferred.get().zones().isEmpty()) proposal = inferred.get();
    }
    if (proposal == null && unsupportedSvg != null) throw unsupportedSvg;
    return new Result(version, floor, format.mediaType(),
      proposal == null ? Processing.PREVIEW_ONLY : Processing.SVG_CANDIDATES,
      scale == null ? ScaleStatus.PROVISIONAL : ScaleStatus.CONFIRMED, scale, proposal);
  }
}

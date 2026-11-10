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
import java.util.ArrayList;
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
    // Explicit SVG rooms stay authoritative. If the SVG has no annotated doors, structural interpretation may
    // supplement door candidates without replacing those rooms. Other formats use the structural proposal.
    if (format == Format.SVG && proposal != null && !proposal.zones().isEmpty() && proposal.doors().isEmpty()) {
      var inferred = structural.propose(original.bytes(), format, bounds, scale);
      if (inferred.isPresent() && !inferred.get().doors().isEmpty()) {
        var warnings = new ArrayList<>(proposal.warnings());
        warnings.add("Se detectaron puertas adicionales en el dibujo; revisa su posición y asociación con los muros");
        inferred.get().warnings().stream().filter(warning -> warning.startsWith("Puerta ")).forEach(warnings::add);
        var walls = proposal.wallCandidates().isEmpty() ? inferred.get().wallCandidates() : proposal.wallCandidates();
        proposal = new SvgPlanPreview.Topology(proposal.zones(), inferred.get().doors(), proposal.nodes(),
          proposal.connections(), walls, warnings, proposal.barriers(), proposal.walkable());
      }
    } else if (proposal == null || proposal.zones().isEmpty()) {
      var inferred = structural.propose(original.bytes(), format, bounds, scale);
      if (inferred.isPresent() && hasReviewableGeometry(inferred.get())) {
        var candidate = inferred.get();
        if (proposal != null && candidate.wallCandidates().isEmpty() && !proposal.wallCandidates().isEmpty())
          candidate = new SvgPlanPreview.Topology(candidate.zones(), candidate.doors(), candidate.nodes(),
            candidate.connections(), proposal.wallCandidates(), candidate.warnings(), candidate.barriers(), candidate.walkable());
        proposal = candidate;
      }
    }
    if (proposal == null && unsupportedSvg != null) throw unsupportedSvg;
    return new Result(version, floor, format.mediaType(),
      proposal == null ? Processing.PREVIEW_ONLY : Processing.SVG_CANDIDATES,
      scale == null ? ScaleStatus.PROVISIONAL : ScaleStatus.CONFIRMED, scale, proposal);
  }

  private static boolean hasReviewableGeometry(SvgPlanPreview.Topology proposal) {
    return !proposal.zones().isEmpty() || !proposal.doors().isEmpty()
      || !proposal.wallCandidates().isEmpty() || !proposal.barriers().isEmpty();
  }
}

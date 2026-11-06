package com.uitopic.rumbo.planning.application.internal.queryservices;

import com.uitopic.rumbo.planning.application.internal.FloorPlanVersionService;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
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

  public OriginalPlanInterpretationQueryService(FloorPlanVersionService files,
      ScaleReferenceRepository scales, SvgPlanPreviewPort previews) {
    this.files = files;
    this.scales = scales;
    this.previews = previews;
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
    var proposal = format == Format.SVG
      ? previews.proposeStoredTopology(original.bytes(), files.bounds(original)) : null;
    return new Result(version, floor, format.mediaType(),
      proposal == null ? Processing.PREVIEW_ONLY : Processing.SVG_CANDIDATES,
      scale == null ? ScaleStatus.PROVISIONAL : ScaleStatus.CONFIRMED, scale, proposal);
  }
}

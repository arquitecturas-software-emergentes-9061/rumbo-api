package com.uitopic.rumbo.planning.infrastructure.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.application.internal.outboundservices.StructuralPlanInterpreterPort;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.valueobjects.OriginalPlanBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Adapter of {@link StructuralPlanInterpreterPort} that sends the stored original to the structural mapper service and
 * converts its scene into a proposal. Any failure of the service, such as an outage, a timeout or a file it cannot
 * decode, is logged and answered with an empty result, so the plan stays available as a preview.
 * {@code use-ml} (on by default) asks the service for its semantic floor-plan parser, which recognizes rooms, walls and
 * doors on raster plans; when the service has no model loaded it falls back to its deterministic perception.
 */
@Component
public class StructuralMapperPlanInterpreter implements StructuralPlanInterpreterPort {
  private static final Logger log = LoggerFactory.getLogger(StructuralMapperPlanInterpreter.class);
  /** Zoom the mapper applies to vector pages; it is sent explicitly so both sides agree on it. */
  static final double RASTER_SCALE = 2.0;
  /** Longest side the mapper keeps when it analyzes a raster image; larger images are reduced. */
  static final int MAPPER_MAX_SIDE = 4096;

  private final RestClient http;
  private final boolean useMl;

  StructuralMapperPlanInterpreter(RestClient http, boolean useMl) {
    this.http = http;
    this.useMl = useMl;
  }

  @Autowired
  public StructuralMapperPlanInterpreter(
    @Value("${rumbo.planning.structural-mapper.url:http://localhost:8010}") String url,
    @Value("${rumbo.planning.structural-mapper.connect-timeout-ms:2000}") int connectTimeoutMs,
    @Value("${rumbo.planning.structural-mapper.read-timeout-ms:30000}") int readTimeoutMs,
    @Value("${rumbo.planning.structural-mapper.use-ml:true}") boolean useMl) {
    this(RestClient.builder().baseUrl(url).requestFactory(factory(connectTimeoutMs, readTimeoutMs)).build(), useMl);
  }

  private static SimpleClientHttpRequestFactory factory(int connectTimeoutMs, int readTimeoutMs) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(connectTimeoutMs);
    factory.setReadTimeout(readTimeoutMs);
    return factory;
  }

  @Override
  public Optional<SvgPlanPreview.Topology> propose(byte[] original, FloorPlanFile.Format format,
                                                   OriginalPlanBounds bounds, Double metersPerUnit) {
    try {
      JsonNode scene = http.post().uri("/analyze").contentType(MediaType.MULTIPART_FORM_DATA)
        .body(parts(original, format, bounds, metersPerUnit, useMl)).retrieve().body(JsonNode.class);
      if (scene == null) return Optional.empty();
      return Optional.of(MapperSceneTopologyMapper.toTopology(scene, bounds, metersPerUnit != null));
    } catch (RestClientException | IllegalArgumentException failure) {
      log.warn("The structural mapper could not interpret a {} plan; it stays a preview: {}", format,
        failure.getMessage());
      return Optional.empty();
    }
  }

  private static MultiValueMap<String, Object> parts(byte[] original, FloorPlanFile.Format format,
                                                      OriginalPlanBounds bounds, Double metersPerUnit,
                                                      boolean useMl) {
    HttpHeaders fileHeaders = new HttpHeaders();
    fileHeaders.setContentType(MediaType.parseMediaType(format.mediaType()));
    ByteArrayResource file = new ByteArrayResource(original) {
      @Override
      public String getFilename() {
        return "plan." + format.name().toLowerCase();
      }
    };
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add("file", new HttpEntity<>(file, fileHeaders));
    parts.add("raster_scale", Double.toString(RASTER_SCALE));
    parts.add("use_ml", Boolean.toString(useMl));
    if (metersPerUnit != null && metersPerUnit > 0) {
      parts.add("meters_per_unit", Double.toString(metersPerUnit));
      parts.add("original_width_units", Double.toString(bounds.width()));
    }
    Double pixelsPerMeter = pixelsPerMeter(format, bounds, metersPerUnit);
    if (pixelsPerMeter != null) parts.add("pixels_per_meter", Double.toString(pixelsPerMeter));
    return parts;
  }

  /**
   * The calibrated scale expressed in the pixels the mapper will work with, so it does not have to guess one.
   * Raster plans are measured in pixels and reduced above {@link #MAPPER_MAX_SIDE}; PDF pages are measured in points
   * and rendered at {@link #RASTER_SCALE}. An SVG sizes itself, so no hint is sent.
   */
  static Double pixelsPerMeter(FloorPlanFile.Format format, OriginalPlanBounds bounds, Double metersPerUnit) {
    if (metersPerUnit == null || metersPerUnit <= 0) return null;
    return switch (format) {
      case PNG, JPEG -> Math.min(1.0, MAPPER_MAX_SIDE / Math.max(bounds.width(), bounds.height())) / metersPerUnit;
      case PDF -> RASTER_SCALE / metersPerUnit;
      case SVG -> null;
    };
  }
}

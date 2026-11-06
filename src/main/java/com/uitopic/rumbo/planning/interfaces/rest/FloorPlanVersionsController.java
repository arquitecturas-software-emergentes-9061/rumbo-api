package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.application.internal.FloorPlanVersionService;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidFloorPlanFile;
import com.uitopic.rumbo.planning.domain.model.entities.FloorPlanVersion;
import com.uitopic.rumbo.planning.domain.model.valueobjects.FloorPlanFile;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
@RequestMapping("/api/v1/sites/{siteId}/floors/{floorId}/plans")
@Tag(name = "Architectural floor plans")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class FloorPlanVersionsController {
  private final FloorPlanVersionService service;

  public FloorPlanVersionsController(FloorPlanVersionService service) {
    this.service = service;
  }

  public record VersionResponse(UUID id, UUID siteId, UUID floorId, int version, String fileName,
                                String mediaType, long sizeBytes, Instant createdAt) {
    static VersionResponse from(FloorPlanVersion version) {
      var file = version.file();
      return new VersionResponse(version.id(), version.siteId(), version.floorId(), version.number(),
        file.name(), file.format().mediaType(), file.sizeBytes(), version.createdAt());
    }
  }

  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "List plan metadata from newest to oldest",
    description = "The first item is the current original. A registered floor with no plans returns an empty list.")
  @ApiResponse(responseCode = "200", description = "Version metadata, newest first")
  @ApiResponse(responseCode = "400", description = "Invalid site or floor identifier", content = @Content)
  @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
  @ApiResponse(responseCode = "403", description = "Administrative role required", content = @Content)
  @ApiResponse(responseCode = "404", description = "Site or floor not available to the administrator", content = @Content)
  public ResponseEntity<List<VersionResponse>> list(@AuthenticationPrincipal AuthenticatedPrincipal user,
      @PathVariable UUID siteId, @PathVariable UUID floorId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
      .body(service.list(user.userId(), siteId, floorId).stream().map(VersionResponse::from).toList());
  }

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Operation(summary = "Store an immutable original file as the next architectural plan version")
  @ApiResponse(responseCode = "201", description = "Original stored; Location identifies the exact version")
  @ApiResponse(responseCode = "400", description = "Missing file, empty file or invalid name", content = @Content)
  @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
  @ApiResponse(responseCode = "403", description = "Administrative role required", content = @Content)
  @ApiResponse(responseCode = "404", description = "Site or floor not found", content = @Content)
  @ApiResponse(responseCode = "413", description = "Upload exceeds the size limit", content = @Content)
  @ApiResponse(responseCode = "415", description = "Unsupported format or media type", content = @Content)
  @ApiResponse(responseCode = "422", description = "Invalid file contents", content = @Content)
  public ResponseEntity<VersionResponse> upload(@AuthenticationPrincipal AuthenticatedPrincipal user,
      @PathVariable UUID siteId, @PathVariable UUID floorId, @RequestPart("file") MultipartFile file)
      throws IOException {
    if (file.getSize() > FloorPlanFile.MAX_BYTES) throw FloorPlanFile.tooLarge();
    var saved = service.register(user.userId(), siteId, floorId, file.getOriginalFilename(),
      file.getContentType(), file.getBytes());
    var location = URI.create("/api/v1/sites/" + siteId + "/floors/" + floorId + "/plans/versions/" + saved.id());
    return ResponseEntity.created(location).body(VersionResponse.from(saved));
  }

  @GetMapping("/versions/{versionId}")
  @Operation(summary = "Download the unchanged original bytes of a specific plan version")
  @ApiResponse(responseCode = "200", description = "Original file, with its stored media type",
    content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
      schema = @Schema(type = "string", format = "binary")))
  @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content)
  @ApiResponse(responseCode = "403", description = "Administrative role required", content = @Content)
  @ApiResponse(responseCode = "404", description = "Site, floor or version not found", content = @Content)
  public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedPrincipal user,
      @PathVariable UUID siteId, @PathVariable UUID floorId, @PathVariable UUID versionId) {
    var original = service.original(user.userId(), siteId, floorId, versionId);
    var file = original.version().file();
    return ResponseEntity.ok()
      .contentType(MediaType.parseMediaType(file.format().mediaType()))
      .contentLength(file.sizeBytes())
      .cacheControl(CacheControl.noStore())
      .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
      .header("Content-Security-Policy", "sandbox; default-src 'none'")
      .header("X-Content-Type-Options", "nosniff")
      .body(original.bytes());
  }

  @RestControllerAdvice(assignableTypes = FloorPlanVersionsController.class)
  public static class Errors {
    @ExceptionHandler(FloorPlanVersionService.SiteNotFound.class)
    public ProblemDetail siteNotFound() {
      return ProblemDetails.of(HttpStatus.NOT_FOUND, "SITE_NOT_FOUND", "Site not found");
    }

    @ExceptionHandler(FloorPlanVersionService.FloorNotFound.class)
    public ProblemDetail floorNotFound() {
      return ProblemDetails.of(HttpStatus.NOT_FOUND, "FLOOR_NOT_FOUND", "Floor not found in this site");
    }

    @ExceptionHandler(FloorPlanVersionService.NotFound.class)
    public ProblemDetail notFound() {
      return ProblemDetails.of(HttpStatus.NOT_FOUND, "FLOOR_PLAN_NOT_FOUND", "Site, floor or plan version not found");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ProblemDetail missingFile() {
      return ProblemDetails.of(HttpStatus.BAD_REQUEST, "PLAN_FILE_REQUIRED", "Provide the multipart part 'file'");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail tooLarge() {
      return invalid(FloorPlanFile.tooLarge());
    }

    @ExceptionHandler(InvalidFloorPlanFile.class)
    public ProblemDetail invalid(InvalidFloorPlanFile exception) {
      var status = switch (exception.reason()) {
        case INVALID_NAME, EMPTY_FILE -> HttpStatus.BAD_REQUEST;
        case FILE_TOO_LARGE -> HttpStatus.PAYLOAD_TOO_LARGE;
        case UNSUPPORTED_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        case INVALID_CONTENT -> HttpStatus.UNPROCESSABLE_ENTITY;
      };
      return ProblemDetails.of(status, "PLAN_" + exception.reason().name(), exception.getMessage());
    }
  }
}

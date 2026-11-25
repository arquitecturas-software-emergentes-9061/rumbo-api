package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.application.internal.queryservices.PublishedModelQueryService;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedMapSnapshot;
import com.uitopic.rumbo.planning.domain.model.valueobjects.PublishedSite;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidPublishedMapDataException;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

/** Read-only visitor synchronization; never exports draft data or original files. */
@RestController
@Profile("postgres")
@Tag(name = "Published indoor models")
@SecurityRequirement(name = "bearerAuth")
public class PublishedModelsController {
  private final PublishedModelQueryService queries;
  public PublishedModelsController(PublishedModelQueryService queries) { this.queries = queries; }

  @GetMapping("/api/v1/published-sites")
  @PreAuthorize("hasAnyRole('PATIENT', 'CLINIC_ADMIN')")
  @Operation(summary = "List sites with an official map", description = "Patients see published sites; administrators see only their own.")
  public ResponseEntity<List<PublishedSite>> catalog(@AuthenticationPrincipal AuthenticatedPrincipal caller) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore())
        .body(queries.catalog(caller.isClinicAdmin() ? caller.userId() : null));
  }

  public record FloorLabel(UUID id, int number, String name) {}
  public record ModelResource(UUID siteId, UUID mapId, UUID modelVersionId, String status,
      List<PublishedMapSnapshot.Plan> plans, List<FloorLabel> floors,
      List<PublishedMapSnapshot.Node> nodes, List<PublishedMapSnapshot.Connection> connections,
      List<PublishedMapSnapshot.Destination> destinations,
      List<com.uitopic.rumbo.planning.domain.model.valueobjects.MapWall> walls,
      List<com.uitopic.rumbo.planning.domain.model.valueobjects.MapDoor> doors) {
    static ModelResource from(PublishedModelQueryService.View view) {
      var graph = view.graph();
      return new ModelResource(graph.siteId(), graph.mapId(), graph.modelVersionId(), "PUBLISHED", graph.plans(),
          view.floors().stream().map(floor -> new FloorLabel(floor.id(), floor.number(), floor.name())).toList(),
          graph.nodes(), graph.connections(), graph.destinations(), graph.walls(), graph.doors());
    }
  }

  @GetMapping("/api/v1/sites/{siteId}/published-map")
  @PreAuthorize("hasRole('PATIENT') or (hasRole('CLINIC_ADMIN') and @siteOwnerGuard.isOwner(authentication, #siteId))")
  @Operation(summary = "Synchronize a site's current published model", description = "The model version is immutable. "
      + "Send If-None-Match to revalidate a previous ETag. Draft and archived versions are excluded.")
  @Parameter(name = "If-None-Match", in = ParameterIn.HEADER, description = "ETag of the previously synchronized official version")
  @ApiResponse(responseCode = "200", description = "Current published model and ETag",
      content = @Content(schema = @Schema(implementation = ModelResource.class)))
  @ApiResponse(responseCode = "304", description = "The cached official version is still current")
  @ApiResponse(responseCode = "404", description = "No published map is available")
  public ResponseEntity<?> current(@PathVariable UUID siteId, WebRequest request) {
    var view = queries.current(siteId);
    if (view.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
        ProblemDetails.of(HttpStatus.NOT_FOUND, "PUBLISHED_MAP_NOT_FOUND", "No published map is available"));
    String etag = "\"map-" + view.get().graph().modelVersionId() + "\"";
    var cache = CacheControl.noCache().cachePrivate();
    // checkNotModified already writes ETag to the servlet response; adding it again
    // produces two values on a real HTTP connection and breaks client revalidation.
    if (request.checkNotModified(etag)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).cacheControl(cache).build();
    return ResponseEntity.ok().cacheControl(cache).eTag(etag).body(ModelResource.from(view.get()));
  }

  @ExceptionHandler(InvalidPublishedMapDataException.class)
  public ResponseEntity<?> invalidModel() {
    return ResponseEntity.unprocessableEntity().body(ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY,
        "INVALID_PUBLISHED_MODEL", "The published model could not be read safely"));
  }
}

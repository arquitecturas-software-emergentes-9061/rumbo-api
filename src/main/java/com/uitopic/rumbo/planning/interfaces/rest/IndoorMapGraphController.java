package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import com.uitopic.rumbo.planning.domain.services.IndoorMapQueryService;
import com.uitopic.rumbo.planning.domain.model.aggregates.IndoorMap;
import com.uitopic.rumbo.planning.interfaces.rest.resources.DraftGraphResource;
import com.uitopic.rumbo.planning.interfaces.rest.resources.GraphUpdateResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlled REST entry point for reading and replacing an existing draft graph. */
@RestController
@PreAuthorize("hasRole('CLINIC_ADMIN') and @siteOwnerGuard.isOwner(authentication, #siteId)")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@Tag(name = "Indoor map graph")
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.planning.editing.enabled", havingValue = "true")
@RequestMapping("/api/v1/sites/{siteId}/maps")
public class IndoorMapGraphController {
  private static final String VERSION_GRAPH = "/{mapId}/versions/{versionId}/graph";
  private final IndoorMapQueryService queries;
  private final IndoorMapCommandService commands;

  public IndoorMapGraphController(IndoorMapQueryService queries, IndoorMapCommandService commands) {
    this.queries = queries;
    this.commands = commands;
  }

  /** Retrieves one draft graph and its coordinate frame for the editor. */
  @Operation(summary = "Read an indoor map draft graph")
  @GetMapping(VERSION_GRAPH)
  public ResponseEntity<DraftGraphResource> getDraft(@PathVariable UUID siteId,
      @PathVariable UUID mapId, @PathVariable UUID versionId) {
    return snapshot(queries.getDraft(siteId, mapId, versionId));
  }

  @Operation(summary = "Find the current indoor map draft graph for a site")
  @ApiResponse(responseCode = "200", description = "Draft snapshot and current ETag")
  @ApiResponse(responseCode = "403", description = "The caller cannot edit the site")
  @ApiResponse(responseCode = "404", description = "The site has no draft")
  @GetMapping("/draft/graph")
  public ResponseEntity<DraftGraphResource> getSiteDraft(@PathVariable UUID siteId) {
    return snapshot(queries.getSiteDraft(siteId));
  }

  /** Replaces all editable graph collections if the caller read the current revision. */
  @Operation(summary = "Replace an indoor map draft graph",
      description = "Requires nodes, connections and destinations in full, plus the ETag "
          + "from a prior GET in If-Match. Returns the next ETag after an atomic save.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Draft graph replaced"),
      @ApiResponse(responseCode = "400", description = "Malformed request or If-Match"),
      @ApiResponse(responseCode = "404", description = "Draft map version not found"),
      @ApiResponse(responseCode = "409", description = "Version is no longer a draft"),
      @ApiResponse(responseCode = "412", description = "Draft graph changed since GET"),
      @ApiResponse(responseCode = "422", description = "Graph failed domain validation"),
      @ApiResponse(responseCode = "428", description = "If-Match is required")
  })
  @PutMapping(VERSION_GRAPH)
  public ResponseEntity<DraftGraphResource> replaceDraft(@PathVariable UUID siteId,
      @PathVariable UUID mapId, @PathVariable UUID versionId,
      @Parameter(description = "ETag returned by GET, for example \"rev-0\"", required = true)
      @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
      @Valid @RequestBody GraphUpdateResource update) {
    return snapshot(commands.handle(update.toCommand(siteId, mapId, versionId, expectedRevision(ifMatch))));
  }

  private static ResponseEntity<DraftGraphResource> snapshot(IndoorMap map) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag(etag(map.editRevision()))
        .body(DraftGraphResource.from(map));
  }

  private static String etag(long revision) {
    return "\"rev-" + revision + "\"";
  }

  private static long expectedRevision(String ifMatch) {
    if (ifMatch == null) throw new GraphEditPreconditionException(true);
    if (!ifMatch.matches("\"rev-(0|[1-9][0-9]*)\"")) {
      throw new GraphEditPreconditionException(false);
    }
    try {
      return Long.parseLong(ifMatch.substring(5, ifMatch.length() - 1));
    } catch (NumberFormatException error) {
      throw new GraphEditPreconditionException(false);
    }
  }
}

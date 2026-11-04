package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.domain.model.commands.CreateDraftMapVersionCommand;
import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import com.uitopic.rumbo.planning.interfaces.rest.resources.CreateMapVersionResource;
import com.uitopic.rumbo.planning.interfaces.rest.resources.DraftGraphResource;
import com.uitopic.rumbo.planning.interfaces.rest.resources.MapVersionResource;
import com.uitopic.rumbo.planning.interfaces.rest.resources.MapVersionStatusPatchResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST entry point for publishing Planning map versions. */
@RestController
@Tag(name = "Indoor maps")
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.planning.publication.enabled", havingValue = "true")
@RequestMapping("/api/v1/sites/{siteId}/maps")
public class IndoorMapsController {
  private final IndoorMapCommandService commands;

  public IndoorMapsController(IndoorMapCommandService commands) {
    this.commands = commands;
  }

  /** Creates an editable copy of the current published map without changing its visibility. */
  @Operation(summary = "Create a draft from the current published map version")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "Draft created; Location points to its graph"),
      @ApiResponse(responseCode = "400", description = "Malformed or missing source version ID"),
      @ApiResponse(responseCode = "404", description = "Site, map, or published version not found"),
      @ApiResponse(responseCode = "409", description = "Draft already exists or source version changed"),
      @ApiResponse(responseCode = "422", description = "Published source contains invalid graph data")
  })
  @PostMapping("/{mapId}/versions")
  public ResponseEntity<DraftGraphResource> createDraft(
      @PathVariable UUID siteId, @PathVariable UUID mapId,
      @Valid @RequestBody CreateMapVersionResource request) {
    var draft = commands.handle(new CreateDraftMapVersionCommand(
        siteId, mapId, request.sourceVersionId()));
    URI graphLocation = URI.create("/api/v1/sites/" + siteId + "/maps/" + mapId
        + "/versions/" + draft.modelVersionId() + "/graph");
    return ResponseEntity.created(graphLocation)
        .eTag("\"rev-" + draft.editRevision() + "\"")
        .body(DraftGraphResource.from(draft));
  }

  /** Validates and publishes a draft version, returning its new state. */
  @Operation(summary = "Publish an indoor map version")
  @PatchMapping(value = "/{mapId}/versions/{versionId}", consumes = "application/merge-patch+json")
  public ResponseEntity<MapVersionResource> publish(
      @PathVariable UUID siteId, @PathVariable UUID mapId, @PathVariable UUID versionId,
      @Valid @RequestBody MapVersionStatusPatchResource patch) {
    var map = commands.handle(new PublishMapVersionCommand(siteId, mapId, versionId));
    return ResponseEntity.ok(new MapVersionResource(map.siteId(), map.id(), map.modelVersionId(), map.status()));
  }
}

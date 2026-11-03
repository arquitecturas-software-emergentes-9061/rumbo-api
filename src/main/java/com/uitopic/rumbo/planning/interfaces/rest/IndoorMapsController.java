package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.domain.model.commands.PublishMapVersionCommand;
import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import com.uitopic.rumbo.planning.interfaces.rest.resources.MapVersionResource;
import com.uitopic.rumbo.planning.interfaces.rest.resources.MapVersionStatusPatchResource;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** REST entry point for publishing Planning map versions. */
@RestController
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.planning.publication.enabled", havingValue = "true")
@RequestMapping("/api/v1/sites/{siteId}/maps")
public class IndoorMapsController {
  private final IndoorMapCommandService commands;

  public IndoorMapsController(IndoorMapCommandService commands) {
    this.commands = commands;
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

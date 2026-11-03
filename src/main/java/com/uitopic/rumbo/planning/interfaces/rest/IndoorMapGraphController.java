package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.domain.services.IndoorMapCommandService;
import com.uitopic.rumbo.planning.domain.services.IndoorMapQueryService;
import com.uitopic.rumbo.planning.interfaces.rest.resources.DraftGraphResource;
import com.uitopic.rumbo.planning.interfaces.rest.resources.GraphUpdateResource;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlled REST entry point for reading and replacing an existing draft graph. */
@RestController
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.planning.editing.enabled", havingValue = "true")
@RequestMapping("/api/v1/sites/{siteId}/maps/{mapId}/versions/{versionId}/graph")
public class IndoorMapGraphController {
  private final IndoorMapQueryService queries;
  private final IndoorMapCommandService commands;

  public IndoorMapGraphController(IndoorMapQueryService queries, IndoorMapCommandService commands) {
    this.queries = queries;
    this.commands = commands;
  }

  /** Retrieves one draft graph and its coordinate frame for the editor. */
  @Operation(summary = "Read an indoor map draft graph")
  @GetMapping
  public ResponseEntity<DraftGraphResource> getDraft(@PathVariable UUID siteId,
      @PathVariable UUID mapId, @PathVariable UUID versionId) {
    return ResponseEntity.ok(DraftGraphResource.from(queries.getDraft(siteId, mapId, versionId)));
  }

  /** Atomically replaces draft nodes and connections while retaining plans and scales. */
  @Operation(summary = "Replace an indoor map draft graph")
  @PutMapping
  public ResponseEntity<DraftGraphResource> replaceDraft(@PathVariable UUID siteId,
      @PathVariable UUID mapId, @PathVariable UUID versionId,
      @Valid @RequestBody GraphUpdateResource update) {
    return ResponseEntity.ok(DraftGraphResource.from(
        commands.handle(update.toCommand(siteId, mapId, versionId))));
  }
}

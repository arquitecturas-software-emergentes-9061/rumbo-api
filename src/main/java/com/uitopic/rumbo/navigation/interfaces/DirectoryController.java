package com.uitopic.rumbo.navigation.interfaces;

import com.uitopic.rumbo.navigation.domain.services.DirectoryQueryService;
import com.uitopic.rumbo.navigation.domain.DirectoryEntry;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controlled search API over published destinations of one site. */
@RestController
@Profile("postgres")
@ConditionalOnProperty(name = "rumbo.navigation.directory.enabled", havingValue = "true")
@RequestMapping("/api/v1/sites/{siteId}/directory-entries")
public class DirectoryController {
  private final DirectoryQueryService queries;

  public DirectoryController(DirectoryQueryService queries) {
    this.queries = queries;
  }

  /** Finds doctors, specialties, and services with a navigable published node. */
  @Operation(summary = "Search the current published medical directory of one site")
  @GetMapping
  public ResponseEntity<List<DirectoryEntryResponse>> search(@PathVariable UUID siteId,
                                                               @RequestParam String q) {
    return ResponseEntity.ok(queries.search(siteId, q).stream()
        .map(DirectoryEntryResponse::from).toList());
  }

  /** Public directory result with the selected model version and destination node. */
  public record DirectoryEntryResponse(UUID id, UUID siteId, UUID modelVersionId,
                                       UUID floorId, UUID nodeId, DirectoryEntry.EntryType type,
                                       String name, String specialty) {
    public static DirectoryEntryResponse from(DirectoryEntry entry) {
      return new DirectoryEntryResponse(entry.id(), entry.siteId(), entry.modelVersionId(),
          entry.floorId(), entry.nodeId(), entry.type(), entry.name(), entry.specialty());
    }
  }
}

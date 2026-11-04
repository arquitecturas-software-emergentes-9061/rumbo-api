package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.navigation.domain.services.DirectoryQueryService;
import com.uitopic.rumbo.navigation.interfaces.rest.resources.DirectoryEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Directory")
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
      @Parameter(description = "Text to search in entry names, specialties, and types", example = "cardio")
      @RequestParam(name = "searchTerm") String searchTerm) {
    return ResponseEntity.ok(queries.search(siteId, searchTerm).stream()
        .map(DirectoryEntryResponse::from).toList());
  }

}

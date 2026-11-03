package com.uitopic.rumbo.positioning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.positioning.application.BeaconInventoryService;
import com.uitopic.rumbo.positioning.domain.Beacon;
import com.uitopic.rumbo.shared.interfaces.rest.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Authenticated clinic administrator inventory; physical scanning is a separate mobile concern. */
@RestController
@RequestMapping("/api/v1/sites")
@Tag(name = "BLE inventory")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class BeaconController {
  private final BeaconInventoryService inventory;

  public BeaconController(BeaconInventoryService inventory) { this.inventory = inventory; }

  public record CreateSiteRequest(@NotBlank String name) {}
  public record RegisterBeaconRequest(@NotNull UUID floorId, @NotBlank String identifier,
                                       @NotNull Double x, @NotNull Double y) {}
  public record SetBeaconStatusRequest(@NotNull Boolean active) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BeaconInventoryService.Site createSite(@AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody CreateSiteRequest request) {
    return inventory.createSite(principal.userId(), request.name());
  }

  @GetMapping
  public List<BeaconInventoryService.Site> sites(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
    return inventory.listSites(principal.userId());
  }

  @PostMapping("/{siteId}/beacons")
  @ResponseStatus(HttpStatus.CREATED)
  public Beacon register(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID siteId,
                         @Valid @RequestBody RegisterBeaconRequest request) {
    return inventory.register(principal.userId(), siteId, request.floorId(), request.identifier(), request.x(), request.y());
  }

  @GetMapping("/{siteId}/beacons")
  public List<Beacon> list(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID siteId,
                            @RequestParam(required = false) UUID floorId) {
    return inventory.list(principal.userId(), siteId, floorId);
  }

  @GetMapping("/{siteId}/beacons/{beaconId}")
  public Beacon get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID siteId,
                    @PathVariable UUID beaconId) {
    return inventory.get(principal.userId(), siteId, beaconId);
  }

  @PatchMapping("/{siteId}/beacons/{beaconId}/status")
  public Beacon setStatus(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID siteId,
                          @PathVariable UUID beaconId, @Valid @RequestBody SetBeaconStatusRequest request) {
    return inventory.setActive(principal.userId(), siteId, beaconId, request.active());
  }
}

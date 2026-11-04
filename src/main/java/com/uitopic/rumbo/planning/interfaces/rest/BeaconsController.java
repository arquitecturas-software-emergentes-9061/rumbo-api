package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.services.BeaconCommandService;
import com.uitopic.rumbo.planning.domain.services.BeaconQueryService;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Planning entry point for registered beacon inventory, separate from mobile BLE scanning. */
@RestController
@RequestMapping("/api/v1/sites/{siteId}/beacons")
@Tag(name = "BLE inventory")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class BeaconsController {
  private final BeaconCommandService commands;
  private final BeaconQueryService queries;

  public BeaconsController(BeaconCommandService commands, BeaconQueryService queries) {
    this.commands = commands;
    this.queries = queries;
  }

  /** Input for one registered beacon and its coordinates in floor-plan units. */
  public record RegisterBeaconRequest(@NotNull UUID floorId, @NotBlank String identifier,
                                      @NotNull Double x, @NotNull Double y) {}

  /** Input for changing the inventory status of a beacon. */
  public record SetBeaconStatusRequest(@NotNull Boolean active) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Beacon register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                         @PathVariable UUID siteId, @Valid @RequestBody RegisterBeaconRequest request) {
    return commands.register(principal.userId(), siteId, request.floorId(),
        request.identifier(), request.x(), request.y());
  }

  @GetMapping
  public List<Beacon> list(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                           @PathVariable UUID siteId, @RequestParam(required = false) UUID floorId) {
    return queries.list(principal.userId(), siteId, floorId);
  }

  @GetMapping("/{beaconId}")
  public Beacon get(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                    @PathVariable UUID siteId, @PathVariable UUID beaconId) {
    return queries.get(principal.userId(), siteId, beaconId);
  }

  @PatchMapping("/{beaconId}/status")
  public Beacon setStatus(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                          @PathVariable UUID siteId, @PathVariable UUID beaconId,
                          @Valid @RequestBody SetBeaconStatusRequest request) {
    return commands.setActive(principal.userId(), siteId, beaconId, request.active());
  }
}

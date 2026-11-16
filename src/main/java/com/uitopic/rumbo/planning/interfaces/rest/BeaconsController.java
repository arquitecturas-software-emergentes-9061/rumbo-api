package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.domain.model.commands.SaveBeaconCommand;
import com.uitopic.rumbo.planning.domain.model.entities.Beacon;
import com.uitopic.rumbo.planning.domain.services.BeaconCommandService;
import com.uitopic.rumbo.planning.domain.services.BeaconQueryService;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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

  /**
   * Input for one registered beacon. Identifier is optional when UUID, major and minor are given;
   * position is optional and a beacon without it stays unplaced and inactive.
   */
  public record SaveBeaconRequest(@NotNull UUID floorId, String identifier,
                                  @Size(max = 100) String name, UUID proximityUuid,
                                  @Min(0) @Max(65535) Integer major,
                                  @Min(0) @Max(65535) Integer minor,
                                  @Min(-120) @Max(0) Integer txPowerDbm, Double x, Double y) {
    SaveBeaconCommand toCommand() {
      return new SaveBeaconCommand(floorId, identifier, name, proximityUuid, major, minor,
          txPowerDbm, x, y);
    }
  }

  /** Input for changing the inventory status of a beacon. */
  public record SetBeaconStatusRequest(@NotNull Boolean active) {}

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Register a beacon with a platform-wide unique identity")
  @ApiResponse(responseCode = "201", description = "Beacon registered")
  @ApiResponse(responseCode = "409", description = "Identifier or iBeacon triplet already registered", content = @Content)
  @ApiResponse(responseCode = "404", description = "Site not available to the administrator", content = @Content)
  @ApiResponse(responseCode = "422", description = "Invalid identifier or spatial anchor", content = @Content)
  public Beacon register(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                         @PathVariable UUID siteId, @Valid @RequestBody SaveBeaconRequest request) {
    return commands.register(principal.userId(), siteId, request.toCommand());
  }

  @PutMapping("/{beaconId}")
  @Operation(summary = "Update a beacon while preserving platform-wide unique identity")
  @ApiResponse(responseCode = "409", description = "Identifier or iBeacon triplet already registered", content = @Content)
  public Beacon update(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                       @PathVariable UUID siteId, @PathVariable UUID beaconId,
                       @Valid @RequestBody SaveBeaconRequest request) {
    return commands.update(principal.userId(), siteId, beaconId, request.toCommand());
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

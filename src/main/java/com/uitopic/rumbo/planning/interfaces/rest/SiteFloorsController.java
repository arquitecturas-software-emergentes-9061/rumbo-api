package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.domain.model.entities.Floor;
import com.uitopic.rumbo.planning.domain.services.ClinicStructureCommandService;
import com.uitopic.rumbo.planning.domain.services.ClinicStructureQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/sites/{siteId}/floors")
@Tag(name="Clinic structure") @SecurityRequirement(name="bearerAuth")
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class SiteFloorsController {
  private final ClinicStructureCommandService commands;
  private final ClinicStructureQueryService queries;
  public SiteFloorsController(ClinicStructureCommandService commands,ClinicStructureQueryService queries) {
    this.commands=commands; this.queries=queries;
  }
  public record CreateFloorRequest(
      @NotNull @Digits(integer = 10, fraction = 0) @Min(Integer.MIN_VALUE) @Max(Integer.MAX_VALUE) BigDecimal number,
      @NotBlank @Size(min = 2, max = 100) String name,
      @Size(max = 250) String description) {}
  @GetMapping @Operation(summary="List registered floors of an owned site, ordered by level")
  public List<Floor> list(@AuthenticationPrincipal AuthenticatedPrincipal user,@PathVariable UUID siteId) {
    return queries.findFloors(user.userId(),siteId);
  }
  @PostMapping @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary="Register a floor with a server-generated identity and unique site level")
  public Floor create(@AuthenticationPrincipal AuthenticatedPrincipal user,@PathVariable UUID siteId,
      @Valid @RequestBody CreateFloorRequest request) {
    return commands.createFloor(user.userId(), siteId, request.number().intValueExact(),
        request.name(), request.description());
  }
}

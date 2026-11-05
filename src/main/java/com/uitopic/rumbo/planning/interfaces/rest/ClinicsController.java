package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.domain.model.aggregates.Clinic;
import com.uitopic.rumbo.planning.domain.services.ClinicStructureCommandService;
import com.uitopic.rumbo.planning.domain.services.ClinicStructureQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/clinics/me")
@Tag(name="Clinic structure") @SecurityRequirement(name="bearerAuth")
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class ClinicsController {
  private final ClinicStructureCommandService commands;
  private final ClinicStructureQueryService queries;
  public ClinicsController(ClinicStructureCommandService commands,ClinicStructureQueryService queries) {
    this.commands=commands; this.queries=queries;
  }
  public record SaveClinicRequest(@NotBlank @Size(max=160) String name,
      @NotBlank @Size(max=60) String code, @NotBlank @Email @Size(max=254) String contactEmail,
      @Size(max=40) String phone) {}
  @GetMapping @Operation(summary="Read the clinic owned by the current account; 404 before registration")
  public ResponseEntity<Clinic> get(@AuthenticationPrincipal AuthenticatedPrincipal user) {
    return ResponseEntity.of(queries.findClinic(user.userId()));
  }
  @PutMapping @Operation(summary="Register or update the current account's clinic")
  public Clinic save(@AuthenticationPrincipal AuthenticatedPrincipal user,@Valid @RequestBody SaveClinicRequest request) {
    return commands.saveClinic(user.userId(),request.name(),request.code(),request.contactEmail(),request.phone());
  }
}

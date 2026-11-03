package com.uitopic.rumbo.planning.calibration.interfaces.rest;

import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import com.uitopic.rumbo.planning.calibration.application.ScaleReferenceService;
import com.uitopic.rumbo.planning.calibration.domain.ScaleReference;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import com.uitopic.rumbo.shared.interfaces.rest.openapi.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sites/{siteId}/plan-calibrations")
@Tag(name = "Plan scale references")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class ScaleReferenceController {
  private final ScaleReferenceService service;
  public ScaleReferenceController(ScaleReferenceService service) { this.service = service; }

  public record Request(@NotNull UUID floorId, @NotNull ScaleReference.Unit unit,
                        @NotNull Double measuredDistance, @NotNull Double startX,
                        @NotNull Double startY, @NotNull Double endX, @NotNull Double endY) {}

  @PutMapping("/{planVersionId}")
  public ScaleReference save(@AuthenticationPrincipal AuthenticatedPrincipal user,
        @PathVariable UUID siteId, @PathVariable UUID planVersionId,
        @Valid @RequestBody Request request) {
    var value = ScaleReference.calibrate(siteId, request.floorId(), planVersionId,
      request.unit(), request.measuredDistance(),
      request.startX(), request.startY(), request.endX(), request.endY());
    return service.upsert(user.userId(), value);
  }

  @GetMapping("/{planVersionId}")
  public ScaleReference get(@AuthenticationPrincipal AuthenticatedPrincipal user,
      @PathVariable UUID siteId, @PathVariable UUID planVersionId) {
    return service.find(user.userId(), siteId, planVersionId);
  }

  @RestControllerAdvice(assignableTypes = ScaleReferenceController.class)
  public static class Errors {
    @ExceptionHandler(ScaleReferenceService.NotFound.class)
    public ProblemDetail notFound(Exception e) {
      return ProblemDetails.of(HttpStatus.NOT_FOUND, "CALIBRATION_NOT_FOUND", "Site or calibration not found");
    }
    @ExceptionHandler(ScaleReferenceService.FloorConflict.class)
    public ProblemDetail conflict(Exception e) {
      return ProblemDetails.of(HttpStatus.CONFLICT, "CALIBRATION_FLOOR_CONFLICT",
        "Cannot reassign a plan version to another floor");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail invalid(IllegalArgumentException e) {
      return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SCALE_REFERENCE", e.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail missing(Exception e) {
      return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid scale reference fields");
    }
  }
}

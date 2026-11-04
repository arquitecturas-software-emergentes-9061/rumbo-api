package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidSvgPlanException;
import com.uitopic.rumbo.planning.application.internal.models.SvgPlanPreview;
import com.uitopic.rumbo.planning.application.internal.queryservices.SvgPlanPreviewQueryService;

import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration.OpenApiConfiguration;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/**
 * Preview-only parsing. Never publishes a graph or writes any Planning data.
 * Client must explicitly review geometry before turning candidates into nodes.
 */
@RestController
@RequestMapping("/api/v1/plan-interpretations")
@Tag(name = "Plan geometry preview")
@SecurityRequirement(name = OpenApiConfiguration.BEARER_AUTH)
@PreAuthorize("hasRole('CLINIC_ADMIN')")
public class SvgInterpretationController {
  private final SvgPlanPreviewQueryService previews;
  public SvgInterpretationController(SvgPlanPreviewQueryService previews) { this.previews = previews; }
  public record InterpretRequest(@NotBlank String svg) {}

  @PostMapping("/svg")
  public SvgPlanPreview.Geometry interpret(@Valid @RequestBody InterpretRequest request) {
    return previews.interpret(request.svg());
  }

  /**
   * Identifies rooms, corridors and explicit door openings; returns a candidate
   * graph only, without persisting or publishing a navigable model.
   */
  @PostMapping("/svg/topology")
  public SvgPlanPreview.Topology topology(@Valid @RequestBody InterpretRequest request) {
    return previews.proposeTopology(request.svg());
  }

  @RestControllerAdvice(assignableTypes = SvgInterpretationController.class)
  public static class Errors {
    @ExceptionHandler(InvalidSvgPlanException.class)
    public ProblemDetail invalid(InvalidSvgPlanException error) {
      return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SVG_PLAN", error.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail missing(MethodArgumentNotValidException error) {
      return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "SVG content is required");
    }
  }
}

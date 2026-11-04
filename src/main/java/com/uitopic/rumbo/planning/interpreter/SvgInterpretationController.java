package com.uitopic.rumbo.planning.interpreter;

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
  private final SvgGeometryInterpreter interpreter = new SvgGeometryInterpreter();
  private final SvgRoomTopologyProposalService topology = new SvgRoomTopologyProposalService();
  public record InterpretRequest(@NotBlank String svg) {}

  @PostMapping("/svg")
  public SvgGeometryInterpreter.Interpretation interpret(@Valid @RequestBody InterpretRequest request) {
    return interpreter.interpret(request.svg());
  }

  /**
   * Identifies rooms, corridors and explicit door openings; returns a candidate
   * graph only, without persisting or publishing a navigable model.
   */
  @PostMapping("/svg/topology")
  public SvgRoomTopologyProposalService.Proposal topology(@Valid @RequestBody InterpretRequest request) {
    return topology.propose(request.svg());
  }

  @RestControllerAdvice(assignableTypes = SvgInterpretationController.class)
  public static class Errors {
    @ExceptionHandler(SvgGeometryInterpreter.InvalidSvg.class)
    public ProblemDetail invalid(SvgGeometryInterpreter.InvalidSvg error) {
      return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SVG_PLAN", error.getMessage());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail missing(MethodArgumentNotValidException error) {
      return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "SVG content is required");
    }
  }
}

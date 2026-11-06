package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.application.internal.exceptions.DuplicateBeaconIdentifierException;
import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidBeaconAnchorException;
import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = {SitesController.class, BeaconsController.class})
public class BeaconExceptionHandler {
  @ExceptionHandler(PlanningSiteNotFoundException.class)
  ProblemDetail notFound(Exception ignored) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, "POSITIONING_RESOURCE_NOT_FOUND", "Site or beacon not found");
  }

  @ExceptionHandler(DuplicateBeaconIdentifierException.class)
  ProblemDetail duplicate(Exception ignored) {
    return ProblemDetails.of(HttpStatus.CONFLICT, "BEACON_IDENTIFIER_EXISTS", "Beacon identifier is already registered");
  }

  @ExceptionHandler(InvalidBeaconAnchorException.class)
  ProblemDetail badAnchor(Exception ignored) {
    return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SPATIAL_ANCHOR",
      "Beacon point must lie inside a registered floor plan of the same site");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalid(IllegalArgumentException error) {
    return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BEACON", error.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail malformedIdentifier(MethodArgumentTypeMismatchException ignored) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid path or query parameter");
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidBody(MethodArgumentNotValidException ignored) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request fields");
  }
}

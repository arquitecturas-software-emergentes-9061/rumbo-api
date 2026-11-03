package com.uitopic.rumbo.positioning.interfaces.rest;

import com.uitopic.rumbo.positioning.application.BeaconInventoryService;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BeaconController.class)
public class BeaconExceptionHandler {
  @ExceptionHandler(BeaconInventoryService.NotFound.class)
  ProblemDetail notFound(Exception ignored) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, "POSITIONING_RESOURCE_NOT_FOUND", "Site or beacon not found");
  }

  @ExceptionHandler(BeaconInventoryService.DuplicateIdentifier.class)
  ProblemDetail duplicate(Exception ignored) {
    return ProblemDetails.of(HttpStatus.CONFLICT, "BEACON_IDENTIFIER_EXISTS", "Beacon already registered in this site");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalid(IllegalArgumentException error) {
    return ProblemDetails.of(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_BEACON", error.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidBody(MethodArgumentNotValidException ignored) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request fields");
  }
}

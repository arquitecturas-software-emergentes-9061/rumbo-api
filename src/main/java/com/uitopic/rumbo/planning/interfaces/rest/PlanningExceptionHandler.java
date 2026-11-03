package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.application.exceptions.MapVersionNotFoundException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapVersionStateException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.exceptions.MapPublicationRejectedException;
import com.uitopic.rumbo.planning.interfaces.rest.resources.PlanningErrorResource;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Translates Planning draft-edit and publication failures into consistent HTTP responses. */
@RestControllerAdvice(assignableTypes = {IndoorMapsController.class, IndoorMapGraphController.class})
public class PlanningExceptionHandler {
  @ExceptionHandler(MapPublicationRejectedException.class)
  public ResponseEntity<PlanningErrorResource> invalidGraph(MapPublicationRejectedException error) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new PlanningErrorResource(
        "INVALID_MAP_GRAPH", error.getMessage(), error.validation().issues().stream()
            .map(PlanningErrorResource.IssueResource::from).toList()));
  }

  @ExceptionHandler(InvalidMapGraphException.class)
  public ResponseEntity<PlanningErrorResource> invalidStoredGraph(InvalidMapGraphException error) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new PlanningErrorResource(
        "INVALID_MAP_GRAPH", "Indoor map graph is invalid",
        List.of(PlanningErrorResource.IssueResource.from(error.issue()))));
  }

  @ExceptionHandler(MapVersionNotFoundException.class)
  public ResponseEntity<PlanningErrorResource> notFound(MapVersionNotFoundException error) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new PlanningErrorResource(
        "MAP_VERSION_NOT_FOUND", error.getMessage(), List.of()));
  }

  @ExceptionHandler(InvalidMapVersionStateException.class)
  public ResponseEntity<PlanningErrorResource> conflict(InvalidMapVersionStateException error) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanningErrorResource(
        "MAP_VERSION_STATE_CONFLICT", error.getMessage(), List.of()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<PlanningErrorResource> invalidRequest(MethodArgumentNotValidException error) {
    return ResponseEntity.badRequest().body(new PlanningErrorResource(
        "INVALID_REQUEST", "Request data is invalid", List.of()));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<PlanningErrorResource> malformedRequest(Exception error) {
    return ResponseEntity.badRequest().body(new PlanningErrorResource(
        "INVALID_REQUEST", "Request body or identifier is malformed", List.of()));
  }
}

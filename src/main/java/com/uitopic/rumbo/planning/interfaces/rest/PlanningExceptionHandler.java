package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.application.internal.exceptions.FirstDraftPlanNotFoundException;
import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidFirstDraftRequestException;
import com.uitopic.rumbo.planning.application.internal.exceptions.MapVersionNotFoundException;
import com.uitopic.rumbo.planning.application.internal.exceptions.SiteMapAlreadyExistsException;
import com.uitopic.rumbo.planning.application.internal.exceptions.DraftMapVersionAlreadyExistsException;
import com.uitopic.rumbo.planning.application.internal.exceptions.PublishedMapVersionChangedException;
import com.uitopic.rumbo.planning.application.internal.exceptions.InvalidPublishedMapVersionException;
import com.uitopic.rumbo.planning.domain.exceptions.DraftGraphRevisionConflictException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapVersionStateException;
import com.uitopic.rumbo.planning.domain.exceptions.InvalidMapGraphException;
import com.uitopic.rumbo.planning.domain.exceptions.MapPublicationRejectedException;
import com.uitopic.rumbo.planning.interfaces.rest.resources.PlanningErrorResource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
@Order(Ordered.HIGHEST_PRECEDENCE)
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

  @ExceptionHandler(DraftMapVersionAlreadyExistsException.class)
  public ResponseEntity<PlanningErrorResource> draftAlreadyExists(DraftMapVersionAlreadyExistsException error) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanningErrorResource(
        "DRAFT_ALREADY_EXISTS", error.getMessage(), List.of()));
  }

  @ExceptionHandler(SiteMapAlreadyExistsException.class)
  public ResponseEntity<PlanningErrorResource> siteMapAlreadyExists(SiteMapAlreadyExistsException error) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanningErrorResource(
        "SITE_MAP_ALREADY_EXISTS", error.getMessage(), List.of()));
  }

  @ExceptionHandler(FirstDraftPlanNotFoundException.class)
  public ResponseEntity<PlanningErrorResource> firstDraftPlanNotFound(FirstDraftPlanNotFoundException error) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new PlanningErrorResource(
        "PLAN_VERSION_NOT_FOUND", error.getMessage(), List.of()));
  }

  @ExceptionHandler(InvalidFirstDraftRequestException.class)
  public ResponseEntity<PlanningErrorResource> invalidFirstDraft(InvalidFirstDraftRequestException error) {
    return ResponseEntity.badRequest().body(new PlanningErrorResource(
        "INVALID_REQUEST", error.getMessage(), List.of()));
  }

  @ExceptionHandler(PublishedMapVersionChangedException.class)
  public ResponseEntity<PlanningErrorResource> publishedSourceChanged(PublishedMapVersionChangedException error) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new PlanningErrorResource(
        "SOURCE_VERSION_NOT_CURRENT", error.getMessage(), List.of()));
  }

  @ExceptionHandler(InvalidPublishedMapVersionException.class)
  public ResponseEntity<PlanningErrorResource> invalidPublishedSource(InvalidPublishedMapVersionException error) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new PlanningErrorResource(
        "INVALID_PUBLISHED_MODEL", error.getMessage(), List.of()));
  }

  @ExceptionHandler(DraftGraphRevisionConflictException.class)
  public ResponseEntity<PlanningErrorResource> staleDraft(DraftGraphRevisionConflictException error) {
    return ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(new PlanningErrorResource(
        "STALE_DRAFT_GRAPH", error.getMessage(), List.of()));
  }

  @ExceptionHandler(GraphEditPreconditionException.class)
  public ResponseEntity<PlanningErrorResource> invalidPrecondition(GraphEditPreconditionException error) {
    return ResponseEntity.status(error.missing() ? HttpStatus.PRECONDITION_REQUIRED
        : HttpStatus.BAD_REQUEST).body(new PlanningErrorResource(
            error.missing() ? "PRECONDITION_REQUIRED" : "INVALID_REQUEST",
            error.getMessage(), List.of()));
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

package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.navigation.application.internal.exceptions.RouteNotFoundException;
import com.uitopic.rumbo.navigation.domain.exceptions.InvalidPublishedGraphException;
import com.uitopic.rumbo.navigation.domain.exceptions.RouteNodeOutsideSiteException;
import com.uitopic.rumbo.shared.interfaces.rest.resources.ApiErrorResource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Translates published route failures without changing the provisional demo API. */
@RestControllerAdvice(assignableTypes = PublishedRoutesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PublishedRoutesExceptionHandler {
  @ExceptionHandler({MissingServletRequestParameterException.class,
      MethodArgumentTypeMismatchException.class, RouteNodeOutsideSiteException.class})
  public ResponseEntity<ApiErrorResource> invalidRequest(Exception error) {
    return ResponseEntity.badRequest().body(new ApiErrorResource("INVALID_ROUTE_REQUEST",
        "Route parameters are invalid or a node belongs to another site"));
  }

  @ExceptionHandler(RouteNotFoundException.class)
  public ResponseEntity<ApiErrorResource> notFound(RouteNotFoundException error) {
    return ResponseEntity.status(404).body(new ApiErrorResource("ROUTE_NOT_FOUND",
        error.getMessage()));
  }

  @ExceptionHandler(InvalidPublishedGraphException.class)
  public ResponseEntity<ApiErrorResource> invalidModel(InvalidPublishedGraphException error) {
    return ResponseEntity.unprocessableEntity().body(new ApiErrorResource(
        "INVALID_PUBLISHED_MODEL", error.getMessage()));
  }
}

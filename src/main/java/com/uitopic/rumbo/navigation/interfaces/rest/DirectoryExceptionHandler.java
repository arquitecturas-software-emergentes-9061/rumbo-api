package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.shared.interfaces.rest.resources.ApiErrorResource;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps malformed directory searches to a stable client error. */
@RestControllerAdvice(assignableTypes = DirectoryController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DirectoryExceptionHandler {
  @ExceptionHandler({IllegalArgumentException.class,
      MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiErrorResource> invalidRequest(Exception error) {
    return ResponseEntity.badRequest().body(new ApiErrorResource("INVALID_REQUEST",
        "A valid siteId and a non-empty searchTerm of at most 150 characters are required"));
  }

}

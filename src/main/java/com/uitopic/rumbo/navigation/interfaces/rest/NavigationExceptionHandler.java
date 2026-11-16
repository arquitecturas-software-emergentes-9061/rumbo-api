package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.navigation.domain.exceptions.NavigationValidationException;
import com.uitopic.rumbo.shared.interfaces.rest.resources.ApiErrorResource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Translates failures of the provisional demo navigation API. */
@RestControllerAdvice(assignableTypes = NavigationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NavigationExceptionHandler {
  @ExceptionHandler({NavigationValidationException.class, IllegalArgumentException.class})
  public ResponseEntity<ApiErrorResource> invalid(RuntimeException error) {
    return ResponseEntity.unprocessableEntity().body(
        new ApiErrorResource("INVALID_DEMO_ROUTE", error.getMessage()));
  }

  @ExceptionHandler({MethodArgumentNotValidException.class,
      MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiErrorResource> badRequest(Exception error) {
    return ResponseEntity.badRequest().body(
        new ApiErrorResource("INVALID_REQUEST", "Invalid or missing request field"));
  }
}

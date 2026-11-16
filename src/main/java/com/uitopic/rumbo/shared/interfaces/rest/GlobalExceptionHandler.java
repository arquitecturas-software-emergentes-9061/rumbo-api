package com.uitopic.rumbo.shared.interfaces.rest;

import com.uitopic.rumbo.shared.interfaces.rest.resources.ApiErrorResource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Handles HTTP failures that have no bounded-context-specific translation. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {
  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class,
      MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<ApiErrorResource> invalidRequest(Exception error) {
    return ResponseEntity.badRequest().body(new ApiErrorResource(
        "INVALID_REQUEST", "Request data is invalid or malformed"));
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ApiErrorResource> methodNotAllowed(HttpRequestMethodNotSupportedException error) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(new ApiErrorResource(
        "METHOD_NOT_ALLOWED", "HTTP method is not supported for this resource"));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ApiErrorResource> unsupportedMediaType(HttpMediaTypeNotSupportedException error) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(new ApiErrorResource(
        "UNSUPPORTED_MEDIA_TYPE", "Request media type is not supported"));
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ApiErrorResource> resourceNotFound(NoResourceFoundException error) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResource(
        "RESOURCE_NOT_FOUND", "Resource was not found"));
  }
}

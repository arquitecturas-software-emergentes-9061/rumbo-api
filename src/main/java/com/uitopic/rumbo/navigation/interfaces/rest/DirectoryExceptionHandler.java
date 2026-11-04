package com.uitopic.rumbo.navigation.interfaces.rest;

import com.uitopic.rumbo.navigation.interfaces.rest.resources.DirectoryErrorResource;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Maps malformed directory searches to a stable client error. */
@RestControllerAdvice(assignableTypes = DirectoryController.class)
public class DirectoryExceptionHandler {
  @ExceptionHandler({IllegalArgumentException.class,
      MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
  public ResponseEntity<DirectoryErrorResource> invalidRequest(Exception error) {
    return ResponseEntity.badRequest().body(new DirectoryErrorResource("INVALID_REQUEST",
        "A valid siteId and a non-empty q of at most 150 characters are required"));
  }

}

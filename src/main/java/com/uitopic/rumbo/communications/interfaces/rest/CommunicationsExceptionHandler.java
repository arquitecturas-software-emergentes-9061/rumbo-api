package com.uitopic.rumbo.communications.interfaces.rest;

import com.uitopic.rumbo.communications.domain.exceptions.NotificationNotFoundException;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.uitopic.rumbo.communications")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CommunicationsExceptionHandler {
  @ExceptionHandler(NotificationNotFoundException.class)
  ProblemDetail missing(NotificationNotFoundException error) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, error.code(), error.getMessage());
  }
}

package com.uitopic.rumbo.analytics.interfaces.rest;

import com.uitopic.rumbo.analytics.domain.exceptions.InvalidDateRangeException;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/** Translates the errors of the Analytics endpoints into Problem Details with a stable {@code code}. */
@RestControllerAdvice(basePackages = "com.uitopic.rumbo.analytics")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AnalyticsExceptionHandler {
  @ExceptionHandler(InvalidDateRangeException.class)
  ProblemDetail invalidDateRange(InvalidDateRangeException exception) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, InvalidDateRangeException.CODE, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalidRequest(MethodArgumentNotValidException exception) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (FieldError error : exception.getBindingResult().getFieldErrors()) {
      errors.putIfAbsent(error.getField(), error.getDefaultMessage());
    }
    return ProblemDetails.withErrors(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request has invalid fields",
      errors);
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class})
  ProblemDetail unreadableRequest(Exception exception) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
      "The request has a missing value or a value of the wrong type");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalidValue(IllegalArgumentException exception) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
  }
}

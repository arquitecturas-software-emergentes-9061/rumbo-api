package com.uitopic.rumbo.profiles.interfaces.rest;

import com.uitopic.rumbo.profiles.domain.exceptions.*;
import com.uitopic.rumbo.profiles.application.exceptions.*;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice(basePackages = "com.uitopic.rumbo.profiles")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProfilesExceptionHandler {
  @ExceptionHandler(ProfileNotFoundException.class)
  ProblemDetail missing(ProfileNotFoundException error) {
    return ProblemDetails.of(HttpStatus.NOT_FOUND, error.code(), error.getMessage());
  }
  @ExceptionHandler(InvalidProfileImageException.class)
  ProblemDetail image(InvalidProfileImageException error) {
    return ProblemDetails.of(InvalidProfileImageException.IMAGE_TOO_LARGE.equals(error.code())
        ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.BAD_REQUEST, error.code(), error.getMessage());
  }
  @ExceptionHandler(ProfileImageStorageUnavailableException.class)
  ProblemDetail unavailable(ProfileImageStorageUnavailableException error) {
    return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, ProfileImageStorageUnavailableException.CODE, error.getMessage());
  }
  @ExceptionHandler(ProfileImageStorageFailedException.class)
  ProblemDetail failed(ProfileImageStorageFailedException error) {
    return ProblemDetails.of(HttpStatus.BAD_GATEWAY, ProfileImageStorageFailedException.CODE, error.getMessage());
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail invalid(MethodArgumentNotValidException error) {
    Map<String, String> errors = new LinkedHashMap<>();
    error.getBindingResult().getFieldErrors().forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
    return ProblemDetails.withErrors(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request has invalid fields", errors);
  }
  @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
  ProblemDetail malformed(Exception error) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request has missing or malformed data");
  }
}

package com.uitopic.rumbo.iam.interfaces.rest;

import com.uitopic.rumbo.iam.domain.exceptions.AccountDisabledException;
import com.uitopic.rumbo.iam.domain.exceptions.AccountLockedException;
import com.uitopic.rumbo.iam.domain.exceptions.EmailAlreadyRegisteredException;
import com.uitopic.rumbo.iam.domain.exceptions.IamException;
import com.uitopic.rumbo.iam.domain.exceptions.InvalidCredentialsException;
import com.uitopic.rumbo.iam.domain.exceptions.UserAccountNotFoundException;
import com.uitopic.rumbo.iam.domain.exceptions.WeakPasswordException;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates the errors of the IAM endpoints into Problem Details. Credential errors stay generic on purpose: the
 * response never reveals whether an email is registered.
 */
@RestControllerAdvice(basePackages = "com.uitopic.rumbo.iam")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IamExceptionHandler {
  private final Clock clock;

  public IamExceptionHandler(Clock clock) {
    this.clock = clock;
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

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadableRequest(HttpMessageNotReadableException exception) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request body is not valid JSON "
      + "or has a value of the wrong type");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalidValue(IllegalArgumentException exception) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage());
  }

  @ExceptionHandler(WeakPasswordException.class)
  ProblemDetail weakPassword(WeakPasswordException exception) {
    return ProblemDetails.withErrors(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(),
      Map.of("password", exception.getMessage()));
  }

  @ExceptionHandler(EmailAlreadyRegisteredException.class)
  ProblemDetail emailTaken(EmailAlreadyRegisteredException exception) {
    return problem(HttpStatus.CONFLICT, exception);
  }

  @ExceptionHandler(InvalidCredentialsException.class)
  ProblemDetail invalidCredentials(InvalidCredentialsException exception) {
    return problem(HttpStatus.UNAUTHORIZED, exception);
  }

  @ExceptionHandler(AccountLockedException.class)
  ResponseEntity<ProblemDetail> locked(AccountLockedException exception) {
    ProblemDetail problem = problem(HttpStatus.LOCKED, exception);
    problem.setProperty("lockedUntil", exception.lockedUntil());
    long retryAfter = Math.max(1, Duration.between(clock.instant(), exception.lockedUntil()).toSeconds());
    return ResponseEntity.status(HttpStatus.LOCKED).header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter))
      .body(problem);
  }

  @ExceptionHandler(AccountDisabledException.class)
  ProblemDetail disabled(AccountDisabledException exception) {
    return problem(HttpStatus.FORBIDDEN, exception);
  }

  @ExceptionHandler(UserAccountNotFoundException.class)
  ProblemDetail notFound(UserAccountNotFoundException exception) {
    return problem(HttpStatus.NOT_FOUND, exception);
  }

  private static ProblemDetail problem(HttpStatus status, IamException exception) {
    return ProblemDetails.of(status, exception.code(), exception.getMessage());
  }
}

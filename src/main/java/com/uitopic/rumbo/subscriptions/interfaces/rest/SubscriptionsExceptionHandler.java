package com.uitopic.rumbo.subscriptions.interfaces.rest;

import com.uitopic.rumbo.subscriptions.domain.exceptions.*;
import com.uitopic.rumbo.subscriptions.application.exceptions.*;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@RestControllerAdvice(basePackageClasses = PaymentOrdersController.class)
@Order(0)
public class SubscriptionsExceptionHandler {
  @ExceptionHandler(PlanNotFoundException.class)
  public ProblemDetail planNotFound() { return ProblemDetails.of(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "Plan not found"); }
  @ExceptionHandler(PaymentOrderNotFoundException.class)
  public ProblemDetail orderNotFound() { return ProblemDetails.of(HttpStatus.NOT_FOUND, "PAYMENT_ORDER_NOT_FOUND", "Payment order not found"); }
  @ExceptionHandler(PlanRequiresSalesContactException.class)
  public ProblemDetail sales() { return ProblemDetails.of(HttpStatus.CONFLICT, "PLAN_REQUIRES_SALES_CONTACT", "Contact sales for this plan"); }
  @ExceptionHandler(PaymentsUnavailableException.class)
  public ProblemDetail unavailable() { return ProblemDetails.of(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_UNAVAILABLE", "Payments are unavailable"); }
  @ExceptionHandler(PaymentProviderException.class)
  public ProblemDetail provider() { return ProblemDetails.of(HttpStatus.BAD_GATEWAY, "PAYMENT_PROVIDER_ERROR", "Payment provider failed"); }
  @ExceptionHandler({MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class})
  public ProblemDetail invalid() { return ProblemDetails.of(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid request fields"); }
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail malformed() { return ProblemDetails.of(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request"); }
}

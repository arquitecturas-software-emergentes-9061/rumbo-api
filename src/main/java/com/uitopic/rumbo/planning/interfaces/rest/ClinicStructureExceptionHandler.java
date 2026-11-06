package com.uitopic.rumbo.planning.interfaces.rest;

import com.uitopic.rumbo.planning.application.internal.exceptions.PlanningSiteNotFoundException;
import com.uitopic.rumbo.planning.domain.exceptions.ClinicStructureConflictException;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(assignableTypes={ClinicsController.class,SiteFloorsController.class,SitesController.class})
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClinicStructureExceptionHandler {
  @ExceptionHandler(PlanningSiteNotFoundException.class)
  ProblemDetail notFound() { return ProblemDetails.of(HttpStatus.NOT_FOUND,"PLANNING_RESOURCE_NOT_FOUND","Site not found"); }
  @ExceptionHandler(ClinicStructureConflictException.class)
  ProblemDetail conflict(ClinicStructureConflictException error) {
    return ProblemDetails.of(HttpStatus.CONFLICT,error.code(),error.getMessage());
  }
  @ExceptionHandler({IllegalArgumentException.class,MethodArgumentNotValidException.class})
  ProblemDetail invalid(Exception error) {
    return ProblemDetails.of(HttpStatus.BAD_REQUEST,"VALIDATION_FAILED","Clinic, site or floor data is invalid");
  }
}

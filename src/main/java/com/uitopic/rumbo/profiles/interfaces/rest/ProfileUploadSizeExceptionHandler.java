package com.uitopic.rumbo.profiles.interfaces.rest;

import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes = ProfilesController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProfileUploadSizeExceptionHandler {
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ProblemDetail tooLarge(MaxUploadSizeExceededException error) {
    return ProblemDetails.of(HttpStatus.PAYLOAD_TOO_LARGE, "IMAGE_TOO_LARGE", "Profile image exceeds 5 MiB");
  }
}

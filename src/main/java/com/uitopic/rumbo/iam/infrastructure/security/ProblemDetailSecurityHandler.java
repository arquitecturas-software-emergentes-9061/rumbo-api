package com.uitopic.rumbo.iam.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uitopic.rumbo.shared.interfaces.rest.ProblemDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/** Answers 401 and 403 raised by the security chain with the same Problem Details format as the controllers. */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
  private final ObjectMapper mapper;

  public ProblemDetailSecurityHandler(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
    throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    write(request, response, ProblemDetails.of(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED",
      "A valid access token is required"));
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
    throws IOException {
    write(request, response, ProblemDetails.of(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
      "You are not allowed to perform this operation"));
  }

  private void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
    throws IOException {
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    mapper.writeValue(response.getOutputStream(), problem);
  }
}

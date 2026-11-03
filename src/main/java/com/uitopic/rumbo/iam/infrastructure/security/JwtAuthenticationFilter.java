package com.uitopic.rumbo.iam.infrastructure.security;

import com.uitopic.rumbo.iam.application.ports.TokenProvider;
import com.uitopic.rumbo.iam.application.ports.TokenProvider.AccessTokenClaims;
import com.uitopic.rumbo.iam.interfaces.security.AuthenticatedPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests that carry {@code Authorization: Bearer <access token>}. A missing or untrusted token leaves
 * the request anonymous; the authorization rules then decide between letting it through and answering 401.
 * It is created by {@link SecurityConfiguration} and not declared as a bean, so the servlet container does not
 * register it a second time outside the security chain.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private static final String BEARER = "Bearer ";
  private final TokenProvider tokenProvider;

  public JwtAuthenticationFilter(TokenProvider tokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
    throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
      tokenProvider.verify(header.substring(BEARER.length()).trim()).ifPresent(token -> authenticate(token.claims()));
    }
    chain.doFilter(request, response);
  }

  private static void authenticate(AccessTokenClaims claims) {
    AuthenticatedPrincipal principal = new AuthenticatedPrincipal(claims.userId(), claims.role(), claims.sessionId(),
      claims.clinicId());
    var authentication = new UsernamePasswordAuthenticationToken(principal, null,
      List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }
}

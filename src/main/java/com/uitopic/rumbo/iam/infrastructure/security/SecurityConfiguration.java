package com.uitopic.rumbo.iam.infrastructure.security;

import com.uitopic.rumbo.iam.application.ports.TokenProvider;
import com.uitopic.rumbo.iam.application.services.SessionRevocationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

/**
 * Stateless security of the API: every request is authenticated by its access token, nothing is kept in an HTTP
 * session, and CSRF protection is off because no cookie carries credentials.
 * Endpoints are protected by default; only the ones listed in {@link #PUBLIC_ENDPOINTS} are open. Bounded contexts
 * add finer rules with {@code @PreAuthorize} on their controllers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
  static final String[] PUBLIC_ENDPOINTS = {
    "/api/v1/password-recovery/**",
    "/api/v1/navigations/**",
    "/actuator/health/**", "/actuator/info",
    "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
    "/error"
  };
  static final String[] PUBLIC_AUTH_ENDPOINTS = {
    "/api/v1/auth/sign-up", "/api/v1/auth/sign-in", "/api/v1/auth/refresh"
  };
  /**
   * Administrative writes that need an active plan (TS-28): everything a clinic changes on its sites. Registering the
   * clinic itself ({@code /api/v1/clinics/me}) and the subscription endpoints stay outside, so a clinic can always
   * choose or renew its plan.
   */
  static final String[] SUBSCRIPTION_BOUND_WRITES = {"/api/v1/sites", "/api/v1/sites/**"};

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, TokenProvider tokenProvider, SessionRevocationService revocation,
                                  ProblemDetailSecurityHandler problemHandler,
                                  SubscriptionAuthorizationManager subscriptions) throws Exception {
    return http
      .csrf(AbstractHttpConfigurer::disable)
      .cors(cors -> { })
      .httpBasic(AbstractHttpConfigurer::disable)
      .formLogin(AbstractHttpConfigurer::disable)
      .logout(AbstractHttpConfigurer::disable)
      .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .exceptionHandling(errors -> errors.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler))
      .authorizeHttpRequests(auth -> auth
        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
        .requestMatchers(HttpMethod.POST, "/api/v1/subscriptions/stripe/webhook").permitAll()
        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
        .requestMatchers(HttpMethod.POST, SUBSCRIPTION_BOUND_WRITES).access(subscriptions)
        .requestMatchers(HttpMethod.PUT, SUBSCRIPTION_BOUND_WRITES).access(subscriptions)
        .requestMatchers(HttpMethod.PATCH, SUBSCRIPTION_BOUND_WRITES).access(subscriptions)
        .requestMatchers(HttpMethod.DELETE, SUBSCRIPTION_BOUND_WRITES).access(subscriptions)
        .anyRequest().authenticated())
      .addFilterBefore(new JwtAuthenticationFilter(tokenProvider, revocation), UsernamePasswordAuthenticationFilter.class)
      .build();
  }

  @Bean
  CorsConfigurationSource corsConfigurationSource(
    @Value("${rumbo.security.cors.allowed-origins:http://localhost:4200}") List<String> allowedOrigins) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(allowedOrigins);
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept-Language", "If-Match"));
    cors.setExposedHeaders(List.of("Location", "Retry-After", "ETag", "X-Plan-Width", "X-Plan-Height"));
    cors.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }
}

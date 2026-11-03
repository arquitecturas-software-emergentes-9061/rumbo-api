package com.uitopic.rumbo.iam.infrastructure.configuration;

import com.uitopic.rumbo.iam.domain.model.valueobjects.LockoutPolicy;
import com.uitopic.rumbo.iam.domain.model.valueobjects.PasswordPolicy;
import com.uitopic.rumbo.iam.domain.model.valueobjects.SessionPolicy;
import com.uitopic.rumbo.iam.infrastructure.tokens.jwt.JwtProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/** Policies of Identity and Access Control. Lifetimes can be tuned per environment without code changes. */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class IamConfiguration {
  @Bean
  PasswordPolicy passwordPolicy() {
    return PasswordPolicy.DEFAULT;
  }

  @Bean
  LockoutPolicy lockoutPolicy() {
    return LockoutPolicy.DEFAULT;
  }

  @Bean
  SessionPolicy sessionPolicy(@Value("${rumbo.security.session.access-token-lifetime:15m}") Duration accessToken,
                              @Value("${rumbo.security.session.refresh-token-lifetime:7d}") Duration refreshToken) {
    return new SessionPolicy(accessToken, refreshToken);
  }
}

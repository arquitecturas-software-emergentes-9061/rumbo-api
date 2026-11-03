package com.uitopic.rumbo.navigation.infrastructure;

import com.uitopic.rumbo.navigation.domain.AStarRouteCalculationDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Wires the pure published-model route calculator for PostgreSQL-backed queries. */
@Configuration
@Profile("postgres")
public class PublishedRoutingConfiguration {
  @Bean
  AStarRouteCalculationDomainService aStarRouteCalculationDomainService() {
    return new AStarRouteCalculationDomainService();
  }
}

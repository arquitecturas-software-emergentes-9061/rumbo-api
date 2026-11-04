package com.uitopic.rumbo.navigation.infrastructure.configuration;
import com.uitopic.rumbo.navigation.domain.services.RouteCalculationDomainService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
@Configuration
@Profile("demo & !postgres")
public class NavigationConfiguration {
  @Bean RouteCalculationDomainService routePlanner() {return new RouteCalculationDomainService();}
}

package com.uitopic.rumbo.navigation.infrastructure;
import com.uitopic.rumbo.navigation.application.*;
import com.uitopic.rumbo.navigation.domain.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
@Configuration
@Profile("demo & !postgres")
public class NavigationConfiguration {
  @Bean GetDestinationDirectoryQueryHandler destinationQuery(NavigableModelRepository repo) { return new GetDestinationDirectoryQueryHandler(repo); }
  @Bean RouteCalculationDomainService routePlanner() {return new RouteCalculationDomainService();}
  @Bean CalculateRouteCommandHandler calculateRoute(NavigableModelRepository repo,RouteCalculationDomainService planner) {
    return new CalculateRouteCommandHandler(repo,planner);
  }
}

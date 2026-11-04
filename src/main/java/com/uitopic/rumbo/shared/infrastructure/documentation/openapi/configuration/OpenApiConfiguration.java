package com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Describes the Rumbo HTTP API in the generated OpenAPI document. */
@Configuration
public class OpenApiConfiguration {
  @Bean
  public OpenAPI rumboOpenApi() {
    return new OpenAPI().info(new Info()
        .title("Rumbo API")
        .description("Indoor map planning, published directory, and navigation for clinics")
        .version("0.1.0"));
  }
}

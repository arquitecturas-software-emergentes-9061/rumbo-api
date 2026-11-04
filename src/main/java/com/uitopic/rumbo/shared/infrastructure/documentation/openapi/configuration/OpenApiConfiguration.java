package com.uitopic.rumbo.shared.infrastructure.documentation.openapi.configuration;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Describes the Rumbo HTTP API in the generated OpenAPI document. */
@Configuration
@SecurityScheme(name = OpenApiConfiguration.BEARER_AUTH, type = SecuritySchemeType.HTTP,
    scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfiguration {
  public static final String BEARER_AUTH = "bearerAuth";

  @Bean
  public OpenAPI rumboOpenApi() {
    return new OpenAPI().info(new Info()
        .title("Rumbo API")
        .description("Indoor map planning, published directory, and navigation for clinics")
        .version("0.1.0"));
  }
}

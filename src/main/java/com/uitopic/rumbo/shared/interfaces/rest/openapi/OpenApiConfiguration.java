package com.uitopic.rumbo.shared.interfaces.rest.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document of the API. Protected operations declare {@code @SecurityRequirement(name = "bearerAuth")} so
 * Swagger UI sends the access token obtained from {@code /api/v1/auth/sign-in}.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Rumbo API", version = "v1",
  description = "Web Services of Rumbo, indoor wayfinding for clinics"))
@SecurityScheme(name = OpenApiConfiguration.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer",
  bearerFormat = "JWT")
public class OpenApiConfiguration {
  public static final String BEARER_AUTH = "bearerAuth";
}

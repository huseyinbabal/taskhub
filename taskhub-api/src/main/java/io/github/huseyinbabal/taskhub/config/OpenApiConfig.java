package io.github.huseyinbabal.taskhub.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI/Swagger metadata (SPEC §Session 2 acceptance #5). Declares a global
 * HTTP bearer (JWT) security scheme so Swagger UI shows an "Authorize" button and
 * documents every endpoint as requiring a bearer token. The docs endpoints
 * themselves ({@code /v3/api-docs}, {@code /swagger-ui/**}) are public in
 * {@link SecurityConfig}.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "TaskHub API",
                version = "v1",
                description = "Team task-management REST API — projects, tasks, tags and users."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}

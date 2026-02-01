package it.unipi.findyourdoc.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for OpenAPI (Swagger) documentation.
 *
 * <p>This class uses standard OpenAPI annotations to define the API metadata (title, version) and
 * the security schemes required to access protected endpoints.
 *
 * <p>By defining the {@link SecurityScheme} and adding it to the {@link OpenAPIDefinition}, the
 * Swagger UI will display an "Authorize" button, allowing users to paste their JWT token and test
 * secured endpoints directly from the browser.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(title = "FindYourDoc API", version = "1.0"),
        security =
        @SecurityRequirement(
                name = "bearerAuth") // Applies the "bearerAuth" scheme globally to all endpoints
)
@SecurityScheme(
        name = "bearerAuth", // Must match the name in @SecurityRequirement
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT" // Descriptive hint that the token is a JWT
)
public class OpenApiConfig {
    // No explicit bean methods are needed here; the annotations drive the configuration.
}
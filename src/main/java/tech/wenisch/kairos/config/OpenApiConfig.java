package tech.wenisch.kairos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Configures the global OpenAPI specification metadata served at {@code /v3/api-docs}
 * and displayed in the interactive Swagger UI at {@code /api/}.
 */
@Configuration
public class OpenApiConfig {

    /**
     * Builds the top-level {@link OpenAPI} bean that populates the Info and Security
     * sections of the generated OpenAPI document.
     *
     * <p>Two authentication schemes are declared:
     * <ul>
     *   <li><b>cookieAuth</b> â€“ session cookie obtained via {@code POST /login} (form login)</li>
        *   <li><b>apiKeyAuth</b> â€“ bearer JWT token created in the Admin Panel</li>
     * </ul>
     *
     * @return a fully populated {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI kairosOpenAPI() {
        final String cookieScheme = "cookieAuth";
                final String apiKeyScheme = "apiKeyAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Kairos API")
                        .description("""
                                REST API for the **Kairos** uptime-monitoring platform.

                                ### Authentication
                                Most write endpoints require an authenticated session or API key JWT with `ADMIN` permissions.  \s
                                Read-only endpoints (`GET /api/resources`, `GET /api/resources/{id}`, `GET /api/announcements`, `GET /api/announcements/{id}`) are public.

                                Obtain a session by `POST`-ing credentials to `/login`,
                                or use an API key JWT created in **Admin -> API Keys** as `Authorization: Bearer <token>`.

                                ### Links
                                - [Official Website](https://kairos.wenisch.tech)
                                - [Documentation](https://kairos.wenisch.tech/docs)
                                - [Source Code](https://github.com/wenisch-tech/Kairos)
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Official Website")
                                .url("https://kairos.wenisch.tech"))
                        .license(new License()
                                .name("GNU AGPL v3.0")
                                .url("https://github.com/wenisch-tech/Kairos/blob/main/LICENSE.md")))
                .components(new Components()
                        .addSecuritySchemes(cookieScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Session cookie acquired via form login at `/login`"))
                        .addSecuritySchemes(apiKeyScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Bearer JWT API key token created in Admin -> API Keys")))
                .externalDocs(new ExternalDocumentation()
                        .description("Documentation")
                        .url("https://kairos.wenisch.tech/docs"))
                .addSecurityItem(new SecurityRequirement()
                        .addList(cookieScheme)
                        .addList(apiKeyScheme));
    }
}

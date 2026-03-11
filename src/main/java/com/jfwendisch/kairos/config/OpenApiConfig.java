package com.jfwendisch.kairos.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
     *   <li><b>cookieAuth</b> – session cookie obtained via {@code POST /login} (form login)</li>
     *   <li><b>basicAuth</b> – HTTP Basic, useful for scripted API access</li>
     * </ul>
     *
     * @return a fully populated {@link OpenAPI} instance
     */
    @Bean
    public OpenAPI kairosOpenAPI() {
        final String cookieScheme = "cookieAuth";
        final String basicScheme  = "basicAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("Kairos API")
                        .description("""
                                REST API for the **Kairos** uptime-monitoring platform.

                                ### Authentication
                                Most write endpoints require an authenticated session or HTTP Basic credentials with the `ADMIN` role.  \s
                                Read-only resource endpoints (`GET /api/resources`, `GET /api/resources/{id}`) are public.

                                Obtain a session by `POST`-ing credentials to `/login`, or supply HTTP Basic credentials on every request.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Jean-Fabian Wenisch")
                                .url("https://github.com/JFWenisch/Kairos"))
                        .license(new License()
                                .name("GNU GPL v3.0")
                                .url("https://github.com/JFWenisch/Kairos/blob/main/LICENSE.md")))
                .components(new Components()
                        .addSecuritySchemes(cookieScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("Session cookie acquired via form login at `/login`"))
                        .addSecuritySchemes(basicScheme, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic")
                                .description("HTTP Basic authentication with an ADMIN-role account")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(cookieScheme)
                        .addList(basicScheme));
    }
}

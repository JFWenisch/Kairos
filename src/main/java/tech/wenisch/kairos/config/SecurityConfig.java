package tech.wenisch.kairos.config;

import tech.wenisch.kairos.entity.CorsAllowedOrigin;
import tech.wenisch.kairos.entity.EmbedPolicy;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.CorsAllowedOriginRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;
import tech.wenisch.kairos.service.EmbedSettingsService;
import tech.wenisch.kairos.service.UserService;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    @Value("${OIDC_ENABLED:false}")
    private boolean oidcEnabled;

    @Value("${OIDC_CLIENT_ID:}")
    private String oidcClientId;

    @Value("${OIDC_CLIENT_SECRET:}")
    private String oidcClientSecret;

    @Value("${OIDC_ISSUER_URI:}")
    private String oidcIssuerUri;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserService userService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public AuthenticationSuccessHandler formLoginSuccessHandler(UserService userService) {
        SavedRequestAwareAuthenticationSuccessHandler handler = new SavedRequestAwareAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return (request, response, authentication) -> {
            userService.updateLastLogin(authentication.getName());
            handler.onAuthenticationSuccess(request, response, authentication);
        };
    }

        @Bean
        public SecurityFilterChain filterChain(HttpSecurity http, UserService userService,
            ResourceTypeConfigRepository resourceTypeConfigRepository,
            CorsAllowedOriginRepository corsAllowedOriginRepository,
            EmbedSettingsService embedSettingsService,
            AuthenticationSuccessHandler formLoginSuccessHandler,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
            .requestMatchers("/login", "/error", "/css/**", "/js/**", "/img/**", "/webjars/**").permitAll()
            .requestMatchers("/embed/**").permitAll()
            .requestMatchers("/groups/**").permitAll()
            .requestMatchers("/", "/announcements", "/outages", "/resources/**", "/instant-check", "/actuator/prometheus", "/actuator/health", "/actuator/health/**", "/h2-console/**",
                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**", "/api")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                isPublicAccessAllowed(resourceTypeConfigRepository) || isAuthenticated(authentication.get())
            ))
            .requestMatchers("/api/resources")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                isPublicAccessAllowed(resourceTypeConfigRepository) || isAuthenticated(authentication.get())
            ))
            .requestMatchers(HttpMethod.GET, "/api/resources/*")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                isPublicAccessAllowed(resourceTypeConfigRepository) || isAuthenticated(authentication.get())
            ))
            .requestMatchers(HttpMethod.GET, "/api/resources/*/status-update")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                isPublicAccessAllowed(resourceTypeConfigRepository) || isAuthenticated(authentication.get())
            ))
            .requestMatchers(HttpMethod.GET, "/api/resources/*/latency-samples")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                isPublicAccessAllowed(resourceTypeConfigRepository) || isAuthenticated(authentication.get())
            ))
            .requestMatchers(HttpMethod.GET, "/api/outages", "/api/resources/*/outages")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                isPublicAccessAllowed(resourceTypeConfigRepository) || isAuthenticated(authentication.get())
            ))
            .requestMatchers(HttpMethod.GET, "/api/announcements", "/api/announcements/*")
            .access((authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                isPublicAccessAllowed(resourceTypeConfigRepository) || isAuthenticated(authentication.get())
            ))
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/resources/*/history").authenticated()
                .requestMatchers("/api/resources/**").hasRole("ADMIN")
                .requestMatchers("/api/announcements/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(formLoginSuccessHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            .cors(cors -> cors.configurationSource(request -> {
                String path = request.getRequestURI();
                if (!path.startsWith("/api/")) {
                    return null;
                }
                List<String> allowedOrigins = corsAllowedOriginRepository.findAll()
                        .stream()
                        .map(CorsAllowedOrigin::getOrigin)
                        .collect(Collectors.toList());
                if (allowedOrigins.isEmpty()) {
                    return null;
                }
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(allowedOrigins);
                config.addAllowedMethod("*");
                config.addAllowedHeader("*");
                config.setMaxAge(3600L);
                return config;
            }))
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/h2-console/**", "/api/resources", "/api/resources/**", "/api/announcements", "/api/announcements/**")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())
                .addHeaderWriter((request, response) -> {
                    if (!request.getRequestURI().startsWith("/embed/")) {
                        response.setHeader("X-Frame-Options", "SAMEORIGIN");
                        return;
                    }

                    EmbedPolicy policy = embedSettingsService.getPolicy();
                    if (policy == EmbedPolicy.DISABLED) {
                        response.setHeader("X-Frame-Options", "DENY");
                        response.setHeader("Content-Security-Policy", "frame-ancestors 'none'");
                        return;
                    }

                    response.setHeader("Content-Security-Policy",
                            "frame-ancestors " + embedSettingsService.frameAncestorsDirective());
                })
            );

        http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        if (oidcEnabled && !oidcClientId.isBlank() && !oidcClientSecret.isBlank() && !oidcIssuerUri.isBlank()) {
            log.info("OIDC authentication enabled");
            ClientRegistration registration = ClientRegistration
                    .withRegistrationId("oidc")
                    .clientId(oidcClientId)
                    .clientSecret(oidcClientSecret)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid", "profile", "email")
                    .authorizationUri(oidcIssuerUri + "/protocol/openid-connect/auth")
                    .tokenUri(oidcIssuerUri + "/protocol/openid-connect/token")
                    .userInfoUri(oidcIssuerUri + "/protocol/openid-connect/userinfo")
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .jwkSetUri(oidcIssuerUri + "/protocol/openid-connect/certs")
                    .clientName("OIDC")
                    .build();

            InMemoryClientRegistrationRepository clientRegistrationRepository =
                    new InMemoryClientRegistrationRepository(registration);

            http.oauth2Login(oauth2 -> oauth2
                    .clientRegistrationRepository(clientRegistrationRepository)
                    .defaultSuccessUrl("/")
                    .successHandler((request, response, authentication) -> {
                        String email = authentication.getName();
                        userService.createOidcUser(email);
                        response.sendRedirect("/");
                    })
            );
        }

        return http.build();
    }

    private boolean isPublicAccessAllowed(ResourceTypeConfigRepository resourceTypeConfigRepository) {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        if (configs.isEmpty()) {
            return true;
        }
        return configs.stream().allMatch(ResourceTypeConfig::isAllowPublicAccess);
    }

    private boolean isAuthenticated(org.springframework.security.core.Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}

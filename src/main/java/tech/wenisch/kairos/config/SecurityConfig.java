package tech.wenisch.kairos.config;

import tech.wenisch.kairos.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
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
            AuthenticationSuccessHandler formLoginSuccessHandler,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/announcements", "/resources/**", "/login", "/css/**", "/js/**", "/img/**",
                        "/webjars/**", "/actuator/prometheus", "/actuator/health", "/h2-console/**",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/**", "/api").permitAll()
                .requestMatchers("/api/resources").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/resources/*").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/announcements", "/api/announcements/*").permitAll()
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
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/h2-console/**", "/api/resources", "/api/resources/**", "/api/announcements", "/api/announcements/**")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin())
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
}

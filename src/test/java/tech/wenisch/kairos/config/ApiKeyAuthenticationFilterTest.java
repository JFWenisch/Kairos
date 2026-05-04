package tech.wenisch.kairos.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Optional;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import tech.wenisch.kairos.entity.ApiKey;
import tech.wenisch.kairos.repository.ApiKeyRepository;
import tech.wenisch.kairos.service.ApiKeyService;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyService = new ApiKeyService(apiKeyRepository);
        ReflectionTestUtils.setField(apiKeyService, "jwtSecret", "test-secret-that-is-long-enough-for-hs256-signing");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldFilterApiAndMcpEndpoints() {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService);

        assertThat(filter.shouldNotFilter(requestFor("/api/resources"))).isFalse();
        assertThat(filter.shouldNotFilter(requestFor("/sse"))).isFalse();
        assertThat(filter.shouldNotFilter(requestFor("/mcp/message"))).isFalse();
        assertThat(filter.shouldNotFilter(requestFor("/login"))).isTrue();
    }

    @Test
    void doFilterAuthenticatesSseRequestWithValidBearerToken() throws ServletException, IOException {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService);
        MockHttpServletRequest request = requestFor("/sse");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();
        when(apiKeyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApiKeyService.CreatedApiKey created = apiKeyService.create("mcp", "tester@example.com");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + created.token());

        when(apiKeyRepository.findByKeyId(created.apiKey().getKeyId()))
            .thenReturn(Optional.of(created.apiKey()));

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_OK);
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("api-key:mcp");
        verify(apiKeyRepository).findByKeyId(created.apiKey().getKeyId());
    }

    @Test
    void doFilterRejectsInvalidTokenOnMcpMessageEndpoint() throws ServletException, IOException {
        ApiKeyAuthenticationFilter filter = new ApiKeyAuthenticationFilter(apiKeyService);
        MockHttpServletRequest request = requestFor("/mcp/message");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(MockHttpServletResponse.SC_UNAUTHORIZED);
        assertThat(response.getContentAsString()).contains("Invalid API key token");
    }

    private MockHttpServletRequest requestFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath(path);
        request.setRequestURI(path);
        return request;
    }
}
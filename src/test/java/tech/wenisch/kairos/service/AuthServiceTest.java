package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.repository.ResourceTypeAuthRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private ResourceTypeAuthRepository authRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authRepository);
    }

    private ResourceTypeAuth auth(String pattern) {
        ResourceTypeAuth a = new ResourceTypeAuth();
        a.setUrlPattern(pattern);
        return a;
    }

    @Test
    void returnsEmptyWhenNoAuthsExist() {
        when(authRepository.findByResourceTypeConfig_TypeName("HTTP")).thenReturn(List.of());

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth("https://example.com", "HTTP");

        assertThat(result).isEmpty();
    }

    @Test
    void matchesExactPattern() {
        ResourceTypeAuth a = auth("https://example.com");
        when(authRepository.findByResourceTypeConfig_TypeName("HTTP")).thenReturn(List.of(a));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth("https://example.com", "HTTP");

        assertThat(result).contains(a);
    }

    @Test
    void doesNotMatchDifferentExactPattern() {
        ResourceTypeAuth a = auth("https://other.com");
        when(authRepository.findByResourceTypeConfig_TypeName("HTTP")).thenReturn(List.of(a));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth("https://example.com", "HTTP");

        assertThat(result).isEmpty();
    }

    @Test
    void matchesWildcardPattern() {
        ResourceTypeAuth a = auth("https://registry.example.com*");
        when(authRepository.findByResourceTypeConfig_TypeName("DOCKER")).thenReturn(List.of(a));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth(
                "https://registry.example.com/myimage:latest", "DOCKER");

        assertThat(result).contains(a);
    }

    @Test
    void wildcardDoesNotMatchUnrelatedUrl() {
        ResourceTypeAuth a = auth("https://registry.example.com*");
        when(authRepository.findByResourceTypeConfig_TypeName("DOCKER")).thenReturn(List.of(a));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth("https://other.com/image", "DOCKER");

        assertThat(result).isEmpty();
    }

    @Test
    void prefersMoreSpecificPatternOverWildcard() {
        ResourceTypeAuth generic = auth("https://registry.example.com*");
        ResourceTypeAuth specific = auth("https://registry.example.com/myorg*");
        when(authRepository.findByResourceTypeConfig_TypeName("DOCKER"))
                .thenReturn(List.of(generic, specific));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth(
                "https://registry.example.com/myorg/myimage:latest", "DOCKER");

        assertThat(result).contains(specific);
    }

    @Test
    void returnsEmptyWhenNullPatternInAuth() {
        ResourceTypeAuth a = auth(null);
        when(authRepository.findByResourceTypeConfig_TypeName("HTTP")).thenReturn(List.of(a));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth("https://example.com", "HTTP");

        assertThat(result).isEmpty();
    }

    @Test
    void returnsEmptyWhenNullTarget() {
        ResourceTypeAuth a = auth("https://example.com");
        when(authRepository.findByResourceTypeConfig_TypeName("HTTP")).thenReturn(List.of(a));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth(null, "HTTP");

        assertThat(result).isEmpty();
    }

    @Test
    void longerPrefixWildcardWinsOverShorterPrefixWildcard() {
        ResourceTypeAuth broad = auth("https://example.com*");
        ResourceTypeAuth narrow = auth("https://example.com/myorg*");
        when(authRepository.findByResourceTypeConfig_TypeName("HTTP"))
                .thenReturn(List.of(broad, narrow));

        Optional<ResourceTypeAuth> result = authService.findMatchingAuth(
                "https://example.com/myorg/service", "HTTP");

        assertThat(result).contains(narrow);
    }
}

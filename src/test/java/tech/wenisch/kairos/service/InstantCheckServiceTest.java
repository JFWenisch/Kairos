package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import tech.wenisch.kairos.dto.InstantCheckExecutionResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InstantCheckServiceTest {

    @Mock
    private ResourceTypeConfigRepository resourceTypeConfigRepository;

    @Mock
    private HttpCheckService httpCheckService;

    @Mock
    private DockerCheckService dockerCheckService;

    @Mock
    private TcpCheckService tcpCheckService;

    private InstantCheckService instantCheckService;

    @BeforeEach
    void setUp() {
        instantCheckService = new InstantCheckService(resourceTypeConfigRepository, httpCheckService, dockerCheckService, tcpCheckService);
    }

    @Test
    void allowsAnyTargetWhenWildcardRuleIsConfigured() {
        assertThat(instantCheckService.isTargetAllowed("https://any.example/path", "*")).isTrue();
    }

    @Test
    void matchesHostAndPathAgainstWildcardDomainRule() {
        assertThat(instantCheckService.isTargetAllowed("https://example.com/path/health", "example.com/*")).isTrue();
        assertThat(instantCheckService.isTargetAllowed("https://other.com/path/health", "example.com/*")).isFalse();
    }

    @Test
    void requiresAuthenticationWhenInstantCheckIsNotPublic() {
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config(false, true)));

        assertThat(instantCheckService.isAllowedForRequest(null)).isFalse();
        assertThat(instantCheckService.isAllowedForRequest(new TestingAuthenticationToken("user", "pw", "ROLE_USER"))).isTrue();
    }

    @Test
    void delegatesHttpChecksToHttpService() {
        when(httpCheckService.probe("https://example.com", false, true))
                .thenReturn(InstantCheckExecutionResult.builder().status(CheckStatus.AVAILABLE).message("ok").build());

        InstantCheckExecutionResult result = instantCheckService.runInstantCheck(ResourceType.HTTP, "https://example.com", false, true);

        assertThat(result.status()).isEqualTo(CheckStatus.AVAILABLE);
        verify(httpCheckService).probe("https://example.com", false, true);
    }

    @Test
    void delegatesDockerChecksToDockerService() {
        when(dockerCheckService.probe("ghcr.io/org/image:latest", false, false))
                .thenReturn(InstantCheckExecutionResult.builder().status(CheckStatus.AVAILABLE).message("ok").build());

        InstantCheckExecutionResult result = instantCheckService.runInstantCheck(ResourceType.DOCKER, "ghcr.io/org/image:latest", false, false);

        assertThat(result.status()).isEqualTo(CheckStatus.AVAILABLE);
        verify(dockerCheckService).probe("ghcr.io/org/image:latest", false, false);
    }

    @Test
    void delegatesTcpChecksToTcpService() {
        when(tcpCheckService.probe("mydb.example.com:5432", false, false))
                .thenReturn(InstantCheckExecutionResult.builder().status(CheckStatus.AVAILABLE).message("TCP connection succeeded").build());

        InstantCheckExecutionResult result = instantCheckService.runInstantCheck(ResourceType.TCP, "mydb.example.com:5432", false, false);

        assertThat(result.status()).isEqualTo(CheckStatus.AVAILABLE);
        verify(tcpCheckService).probe("mydb.example.com:5432", false, false);
    }

    private ResourceTypeConfig config(boolean allowPublic, boolean enabled) {
        return ResourceTypeConfig.builder()
                .typeName("HTTP")
                .checkIntervalMinutes(1)
                .parallelism(1)
                .instantCheckAllowPublic(allowPublic)
                .instantCheckEnabled(enabled)
                .build();
    }
}

package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpCheckServiceTest {

    @Mock
    private CheckResultRepository checkResultRepository;

    @Mock
    private AuthService authService;

    @Mock
    private ResourceStatusStreamService resourceStatusStreamService;

    @InjectMocks
    private HttpCheckService httpCheckService;

    @Test
    void checkInvalidUrlReturnsNotAvailable() {
        MonitoredResource resource = MonitoredResource.builder()
                .id(1L)
                .name("Test")
                .resourceType(ResourceType.HTTP)
                .target("http://localhost:99999/nonexistent")
                .active(true)
                .build();

        when(checkResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authService.findMatchingAuth(any(), eq("HTTP"))).thenReturn(Optional.empty());

        CheckResult result = httpCheckService.check(resource);
        assertThat(result.getStatus()).isEqualTo(CheckStatus.NOT_AVAILABLE);
        verify(resourceStatusStreamService).publishResourceUpdate(resource);
    }
}

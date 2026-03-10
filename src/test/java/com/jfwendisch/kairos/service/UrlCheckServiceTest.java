package com.jfwendisch.kairos.service;

import com.jfwendisch.kairos.entity.CheckResult;
import com.jfwendisch.kairos.entity.CheckStatus;
import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.entity.ResourceType;
import com.jfwendisch.kairos.repository.CheckResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HttpCheckServiceTest {

    @Mock
    private CheckResultRepository checkResultRepository;

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

        CheckResult result = httpCheckService.check(resource);
        assertThat(result.getStatus()).isEqualTo(CheckStatus.NOT_AVAILABLE);
    }
}

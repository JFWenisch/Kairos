package tech.wenisch.kairos.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.OutageRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutageRetentionServiceTest {

    @Mock
    private OutageRepository outageRepository;

    @Mock
    private ResourceTypeConfigRepository resourceTypeConfigRepository;

    private OutageRetentionService outageRetentionService;

    @BeforeEach
    void setUp() {
        outageRetentionService = new OutageRetentionService(outageRepository, resourceTypeConfigRepository);
    }

    @Test
    void cleanupOutagesDeletesByEndDateWhenEnabled() {
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(
                ResourceTypeConfig.builder()
                        .outageRetentionEnabled(true)
                        .outageRetentionIntervalHours(12)
                        .outageRetentionDays(31)
                        .build()
        ));
        when(outageRepository.deleteByActiveFalseAndEndDateBefore(any(LocalDateTime.class))).thenReturn(5L);

        outageRetentionService.cleanupOutages();

        verify(outageRepository).deleteByActiveFalseAndEndDateBefore(any(LocalDateTime.class));
    }

    @Test
    void cleanupOutagesSkipsWhenDisabled() {
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(
                ResourceTypeConfig.builder()
                        .outageRetentionEnabled(false)
                        .outageRetentionIntervalHours(12)
                        .outageRetentionDays(31)
                        .build()
        ));

        outageRetentionService.cleanupOutages();

        verify(outageRepository, never()).deleteByActiveFalseAndEndDateBefore(any(LocalDateTime.class));
    }
}

package tech.wenisch.kairos.service;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

@ExtendWith(MockitoExtension.class)
class CheckHistoryRetentionServiceTest {

    @Mock private CheckResultRepository checkResultRepository;
    @Mock private ResourceTypeConfigRepository resourceTypeConfigRepository;

    private CheckHistoryRetentionService service;

    @BeforeEach
    void setUp() {
        service = new CheckHistoryRetentionService(checkResultRepository, resourceTypeConfigRepository);
    }

    @Test
    void cleanupDoesNothingWhenNoConfigs() {
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of());
        service.cleanupCheckHistory();
        verify(checkResultRepository, never()).deleteByCheckedAtBefore(any());
    }

    @Test
    void cleanupDoesNothingWhenRetentionDisabled() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .checkHistoryRetentionEnabled(false)
                .checkHistoryRetentionIntervalMinutes(60)
                .checkHistoryRetentionDays(31)
                .build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        service.cleanupCheckHistory();
        verify(checkResultRepository, never()).deleteByCheckedAtBefore(any());
    }

    @Test
    void cleanupDeletesWhenIntervalHasElapsed() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .checkHistoryRetentionEnabled(true)
                .checkHistoryRetentionIntervalMinutes(1)
                .checkHistoryRetentionDays(31)
                .build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        when(checkResultRepository.deleteByCheckedAtBefore(any(LocalDateTime.class))).thenReturn(5L);

        // lastCleanupTimeMs starts at 0 so any current time will exceed 1-minute interval
        service.cleanupCheckHistory();

        verify(checkResultRepository).deleteByCheckedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void cleanupLogsDebugWhenNothingDeleted() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .checkHistoryRetentionEnabled(true)
                .checkHistoryRetentionIntervalMinutes(1)
                .checkHistoryRetentionDays(31)
                .build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        when(checkResultRepository.deleteByCheckedAtBefore(any(LocalDateTime.class))).thenReturn(0L);

        service.cleanupCheckHistory();

        verify(checkResultRepository).deleteByCheckedAtBefore(any(LocalDateTime.class));
    }

    @Test
    void cleanupSkipsWhenIntervalNotElapsed() {
        ResourceTypeConfig config = ResourceTypeConfig.builder()
                .checkHistoryRetentionEnabled(true)
                .checkHistoryRetentionIntervalMinutes(60)
                .checkHistoryRetentionDays(31)
                .build();
        when(resourceTypeConfigRepository.findAll()).thenReturn(List.of(config));
        when(checkResultRepository.deleteByCheckedAtBefore(any(LocalDateTime.class))).thenReturn(0L);

        // First call sets lastCleanupTimeMs to now
        service.cleanupCheckHistory();

        // Second call with 60-minute interval should be skipped
        service.cleanupCheckHistory();

        // deleteByCheckedAtBefore should only be called once
        verify(checkResultRepository).deleteByCheckedAtBefore(any(LocalDateTime.class));
    }
}

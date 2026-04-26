package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckHistoryRetentionService {

    private final CheckResultRepository checkResultRepository;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    private volatile long lastCleanupTimeMs = 0L;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanupCheckHistory() {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        if (configs.isEmpty()) {
            return;
        }

        ResourceTypeConfig baseline = configs.get(0);
        if (!baseline.isCheckHistoryRetentionEnabled()) {
            return;
        }

        int intervalMinutes = Math.max(1, baseline.getCheckHistoryRetentionIntervalMinutes());
        int retentionDays = Math.max(1, baseline.getCheckHistoryRetentionDays());

        long now = System.currentTimeMillis();
        long intervalMs = intervalMinutes * 60_000L;
        if (now - lastCleanupTimeMs < intervalMs) {
            return;
        }

        lastCleanupTimeMs = now;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        try {
            long deletedRows = checkResultRepository.deleteByCheckedAtBefore(cutoff);
            if (deletedRows > 0) {
                log.info(
                        "Deleted {} check history rows older than {} days (cutoff: {})",
                        deletedRows,
                        retentionDays,
                        cutoff
                );
            } else {
                log.debug(
                        "Check history cleanup ran with no deletions (retention {} days, cutoff: {})",
                        retentionDays,
                        cutoff
                );
            }
        } catch (Exception ex) {
            log.error("Failed to clean up old check history records", ex);
        }
    }
}

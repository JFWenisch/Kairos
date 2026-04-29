package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.OutageRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutageRetentionService {

    private final OutageRepository outageRepository;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    private volatile long lastCleanupTimeMs = 0L;

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanupOutages() {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        if (configs.isEmpty()) {
            return;
        }

        ResourceTypeConfig baseline = configs.get(0);
        if (!baseline.isOutageRetentionEnabled()) {
            return;
        }

        int intervalHours = Math.max(1, baseline.getOutageRetentionIntervalHours());
        int retentionDays = Math.max(1, baseline.getOutageRetentionDays());

        long now = System.currentTimeMillis();
        long intervalMs = intervalHours * 3_600_000L;
        if (now - lastCleanupTimeMs < intervalMs) {
            return;
        }

        lastCleanupTimeMs = now;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);

        try {
            long deletedRows = outageRepository.deleteByActiveFalseAndEndDateBefore(cutoff);
            if (deletedRows > 0) {
                log.info(
                        "Deleted {} closed outages with endDate older than {} days (cutoff: {})",
                        deletedRows,
                        retentionDays,
                        cutoff
                );
            } else {
                log.debug(
                        "Outage cleanup ran with no deletions (retention {} days, cutoff: {})",
                        retentionDays,
                        cutoff
                );
            }
        } catch (Exception ex) {
            log.error("Failed to clean up old outage records", ex);
        }
    }
}

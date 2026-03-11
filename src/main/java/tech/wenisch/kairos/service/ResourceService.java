package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final MonitoredResourceRepository resourceRepository;
    private final CheckResultRepository checkResultRepository;

    public List<MonitoredResource> findAllActive() {
        return resourceRepository.findByActiveTrue();
    }

    public List<MonitoredResource> findAll() {
        return resourceRepository.findAll();
    }

    public Optional<MonitoredResource> findById(Long id) {
        return resourceRepository.findById(id);
    }

    public MonitoredResource save(MonitoredResource resource) {
        if (resource.getCreatedAt() == null) {
            resource.setCreatedAt(LocalDateTime.now());
        }
        return resourceRepository.save(resource);
    }

    @Transactional
    public void delete(Long id) {
        resourceRepository.findById(id).ifPresent(resource -> {
            checkResultRepository.findByResourceOrderByCheckedAtDesc(resource)
                    .forEach(checkResultRepository::delete);
            resourceRepository.delete(resource);
        });
    }

    public List<CheckResult> getHistory(Long resourceId, int limit) {
        return resourceRepository.findById(resourceId)
                .map(resource -> checkResultRepository.findByResourceOrderByCheckedAtDesc(
                        resource, PageRequest.of(0, limit)).getContent())
                .orElse(Collections.emptyList());
    }

        public Page<CheckResult> getHistoryPage(
            Long resourceId,
            int page,
            int size,
            CheckStatus status,
            String errorCode,
            String message
        ) {
        PageRequest pageable = PageRequest.of(page, size);
        return resourceRepository.findById(resourceId)
            .map(resource -> checkResultRepository.findHistoryFiltered(
                resource,
                status,
                normalizeFilter(errorCode),
                normalizeFilter(message),
                pageable
            ))
            .orElseGet(() -> new PageImpl<>(Collections.emptyList(), pageable, 0));
        }

    public List<CheckResult> getFullHistory(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .map(checkResultRepository::findByResourceOrderByCheckedAtDesc)
                .orElse(Collections.emptyList());
    }

    public double getUptimePercentage(MonitoredResource resource, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<CheckResult> results = checkResultRepository
                .findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(resource, since);

        if (results.isEmpty()) return 0.0;

        long available = results.stream()
                .filter(r -> r.getStatus() == CheckStatus.AVAILABLE)
                .count();
        long relevant = results.stream()
                .filter(r -> r.getStatus() != CheckStatus.UNKNOWN)
                .count();

        if (relevant == 0) return 0.0;
        return (double) available / relevant * 100.0;
    }

    /** Number of color-coded blocks displayed in the 24-hour timeline visualization (one block ≈ 16 min). */
    private static final int TIMELINE_BUCKETS = 90;

    public List<String> getTimelineBlocks(MonitoredResource resource) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = now.minusHours(24);

        List<CheckResult> results = checkResultRepository
                .findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(resource, start);

        long totalMinutes = 24 * 60;
        long bucketMinutes = totalMinutes / TIMELINE_BUCKETS;

        List<String> blocks = new ArrayList<>(TIMELINE_BUCKETS);
        for (int i = 0; i < TIMELINE_BUCKETS; i++) {
            LocalDateTime bucketStart = start.plusMinutes(i * bucketMinutes);
            LocalDateTime bucketEnd = bucketStart.plusMinutes(bucketMinutes);

            CheckResult lastInBucket = null;
            for (CheckResult r : results) {
                if (!r.getCheckedAt().isBefore(bucketStart) && r.getCheckedAt().isBefore(bucketEnd)) {
                    lastInBucket = r;
                }
            }

            if (lastInBucket == null) {
                blocks.add("unknown");
            } else {
                switch (lastInBucket.getStatus()) {
                    case AVAILABLE -> blocks.add("available");
                    case NOT_AVAILABLE -> blocks.add("not-available");
                    default -> blocks.add("unknown");
                }
            }
        }
        return blocks;
    }

    public String getCurrentStatus(MonitoredResource resource) {
        return checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource)
                .map(r -> switch (r.getStatus()) {
                    case AVAILABLE -> "available";
                    case NOT_AVAILABLE -> "not-available";
                    default -> "unknown";
                })
                .orElse("unknown");
    }

    public Optional<CheckResult> getLatestCheckResult(MonitoredResource resource) {
        return checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource);
    }

    public List<MonitoredResource> findByType(ResourceType type) {
        return resourceRepository.findByResourceTypeAndActiveTrue(type);
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.dto.TimelineBlockDTO;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.ResourceGroupRepository;
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
    private final ResourceGroupRepository resourceGroupRepository;

    public List<MonitoredResource> findAllActive() {
        return resourceRepository.findAllActiveForLanding();
    }

    public List<MonitoredResource> findAll() {
        return resourceRepository.findAllForAdmin();
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
            if (resource.getResourceType() == ResourceType.DOCKERREPOSITORY) {
                deleteManagedDockerResources(resource);
            }

            checkResultRepository.findByResourceOrderByCheckedAtDesc(resource)
                    .forEach(checkResultRepository::delete);
            resourceRepository.delete(resource);
        });
    }

    private void deleteManagedDockerResources(MonitoredResource dockerRepositoryResource) {
        String groupName = managedGroupName(dockerRepositoryResource);
        resourceGroupRepository.findByNameIgnoreCase(groupName).ifPresent(group -> {
            List<MonitoredResource> managedResources = resourceRepository
                    .findByGroup_IdAndResourceType(group.getId(), ResourceType.DOCKER);

            for (MonitoredResource managedResource : managedResources) {
                checkResultRepository.findByResourceOrderByCheckedAtDesc(managedResource)
                        .forEach(checkResultRepository::delete);
                resourceRepository.delete(managedResource);
            }

            if (resourceRepository.findByGroup_Id(group.getId()).isEmpty()) {
                resourceGroupRepository.delete(group);
            }
        });
    }

    private String managedGroupName(MonitoredResource dockerRepositoryResource) {
        String target = dockerRepositoryResource.getTarget() == null ? "" : dockerRepositoryResource.getTarget().trim();
        return "Dockerrepository: " + target;
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
        return computeUptimePercentage(results);
    }

    private double computeUptimePercentage(List<CheckResult> results) {
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

    /**
     * Combined result of a single history query, carrying both the timeline blocks and the
     * uptime percentage so callers avoid issuing the same database query twice.
     */
    public record TimelineData(List<TimelineBlockDTO> timelineBlocks, double uptimePercentage) {}

    public List<TimelineBlockDTO> getTimelineBlocks(MonitoredResource resource) {
        return getTimelineBlocks(resource, 24);
    }

    public List<TimelineBlockDTO> getTimelineBlocks(MonitoredResource resource, int hours) {
        int safeHours = Math.max(hours, 1);
        LocalDateTime start = LocalDateTime.now().minusHours(safeHours);
        List<CheckResult> results = checkResultRepository
                .findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(resource, start);
        return buildTimelineBlocks(results, start, safeHours);
    }

    /**
     * Fetches check results for the given time window exactly once and returns both
     * the timeline blocks and the uptime percentage, eliminating the duplicate query
     * that was present when {@link #getTimelineBlocks} and {@link #getUptimePercentage}
     * were called independently.
     */
    public TimelineData getTimelineData(MonitoredResource resource, int hours) {
        int safeHours = Math.max(hours, 1);
        LocalDateTime start = LocalDateTime.now().minusHours(safeHours);
        List<CheckResult> results = checkResultRepository
                .findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(resource, start);
        return new TimelineData(
                buildTimelineBlocks(results, start, safeHours),
                computeUptimePercentage(results));
    }

    private List<TimelineBlockDTO> buildTimelineBlocks(List<CheckResult> results, LocalDateTime start, int safeHours) {
        long totalMinutes = (long) safeHours * 60;
        long bucketMinutes = Math.max(totalMinutes / TIMELINE_BUCKETS, 1);

        List<TimelineBlockDTO> blocks = new ArrayList<>(TIMELINE_BUCKETS);
        for (int i = 0; i < TIMELINE_BUCKETS; i++) {
            LocalDateTime bucketStart = start.plusMinutes(i * bucketMinutes);
            LocalDateTime bucketEnd = bucketStart.plusMinutes(bucketMinutes);

            CheckResult lastInBucket = null;
            for (CheckResult r : results) {
                if (!r.getCheckedAt().isBefore(bucketStart) && r.getCheckedAt().isBefore(bucketEnd)) {
                    lastInBucket = r;
                }
            }

            String status = lastInBucket == null ? "unknown" : mapStatus(lastInBucket.getStatus());
            LocalDateTime timestamp = lastInBucket == null ? bucketEnd : lastInBucket.getCheckedAt();
            blocks.add(new TimelineBlockDTO(status, timestamp));
        }
        return blocks;
    }

    private String mapStatus(CheckStatus status) {
        return switch (status) {
            case AVAILABLE -> "available";
            case NOT_AVAILABLE -> "not-available";
            default -> "unknown";
        };
    }

    public String getCurrentStatus(MonitoredResource resource) {
        return checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource)
                .map(r -> mapStatus(r.getStatus()))
                .orElse("unknown");
    }

    public Optional<CheckResult> getLatestCheckResult(MonitoredResource resource) {
        return checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource);
    }

    public List<MonitoredResource> findByType(ResourceType type) {
        return resourceRepository.findByResourceTypeAndActiveTrue(type);
    }

    @Transactional
    public int clearGroupAssignment(Long groupId) {
        List<MonitoredResource> resources = resourceRepository.findByGroup_Id(groupId);
        for (MonitoredResource resource : resources) {
            resource.setGroup(null);
        }
        resourceRepository.saveAll(resources);
        return resources.size();
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Transactional
    public ResourceGroup findOrCreateGroupByName(String groupName) {
        return resourceGroupRepository.findByNameIgnoreCase(groupName)
                .orElseGet(() -> resourceGroupRepository.save(ResourceGroup.builder()
                        .name(groupName)
                        .displayOrder(0)
                        .build()));
    }
}

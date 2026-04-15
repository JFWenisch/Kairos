package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.dto.LatencySampleDTO;
import tech.wenisch.kairos.dto.TimelineBlockDTO;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.OutageRepository;
import tech.wenisch.kairos.repository.ResourceGroupRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final MonitoredResourceRepository resourceRepository;
    private final CheckResultRepository checkResultRepository;
    private final OutageRepository outageRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

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
        MonitoredResource existing = null;
        if (resource.getId() != null && resource.getResourceType() == ResourceType.DOCKERREPOSITORY) {
            existing = resourceRepository.findById(resource.getId()).orElse(null);
        }

        MonitoredResource saved = resourceRepository.save(resource);
        if (saved.getResourceType() == ResourceType.DOCKERREPOSITORY) {
            renameManagedDockerGroupIfNeeded(existing, saved);
        }
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        boolean deleteOutages = resourceTypeConfigRepository.findAll().stream()
                .anyMatch(c -> c.isDeleteOutagesOnResourceDelete());
        resourceRepository.findById(id).ifPresent(resource -> {
            if (resource.getResourceType() == ResourceType.DOCKERREPOSITORY) {
                deleteManagedDockerResources(resource, deleteOutages);
            }

            deleteOrNullifyOutages(resource, deleteOutages);
            checkResultRepository.findByResourceOrderByCheckedAtDesc(resource)
                    .forEach(checkResultRepository::delete);
            resourceRepository.delete(resource);
        });
    }

    private void deleteOrNullifyOutages(MonitoredResource resource, boolean deleteOutages) {
        List<tech.wenisch.kairos.entity.Outage> outages = outageRepository.findByResourceOrderByStartDateDesc(resource);
        if (deleteOutages) {
            outageRepository.deleteAll(outages);
        } else {
            outages.forEach(o -> o.setResource(null));
            outageRepository.saveAll(outages);
        }
    }

    private void deleteManagedDockerResources(MonitoredResource dockerRepositoryResource, boolean deleteOutages) {
        Set<Long> processedGroupIds = new HashSet<>();
        for (String groupName : managedGroupNames(dockerRepositoryResource)) {
            resourceGroupRepository.findByNameIgnoreCase(groupName).ifPresent(group -> {
                if (!processedGroupIds.add(group.getId())) {
                    return;
                }

                List<MonitoredResource> managedResources = resourceRepository
                        .findByGroups_IdAndResourceType(group.getId(), ResourceType.DOCKER);

                for (MonitoredResource managedResource : managedResources) {
                    deleteOrNullifyOutages(managedResource, deleteOutages);
                    checkResultRepository.findByResourceOrderByCheckedAtDesc(managedResource)
                            .forEach(checkResultRepository::delete);
                    resourceRepository.delete(managedResource);
                }

                if (resourceRepository.findByGroups_Id(group.getId()).isEmpty()) {
                    resourceGroupRepository.delete(group);
                }
            });
        }
    }

    public ResourceGroup findOrCreateManagedDockerGroup(MonitoredResource dockerRepositoryResource) {
        String preferredGroupName = managedGroupName(dockerRepositoryResource);
        Optional<ResourceGroup> existingPreferred = resourceGroupRepository.findByNameIgnoreCase(preferredGroupName);
        if (existingPreferred.isPresent()) {
            return existingPreferred.get();
        }

        for (String candidateName : managedGroupNames(dockerRepositoryResource)) {
            if (candidateName.equalsIgnoreCase(preferredGroupName)) {
                continue;
            }

            Optional<ResourceGroup> candidateGroup = resourceGroupRepository.findByNameIgnoreCase(candidateName);
            if (candidateGroup.isPresent()) {
                ResourceGroup group = candidateGroup.get();
                group.setName(preferredGroupName);
                return resourceGroupRepository.save(group);
            }
        }

        return resourceGroupRepository.save(ResourceGroup.builder()
                .name(preferredGroupName)
                .displayOrder(0)
                .build());
    }

    public List<String> managedGroupNames(MonitoredResource dockerRepositoryResource) {
        LinkedHashSet<String> groupNames = new LinkedHashSet<>();
        groupNames.add(managedGroupName(dockerRepositoryResource));

        String legacyGroupName = legacyManagedGroupName(dockerRepositoryResource.getTarget());
        if (!legacyGroupName.isBlank()) {
            groupNames.add(legacyGroupName);
        }

        return new ArrayList<>(groupNames);
    }

    private String managedGroupName(MonitoredResource dockerRepositoryResource) {
        String name = dockerRepositoryResource.getName() == null ? "" : dockerRepositoryResource.getName().trim();
        if (!name.isBlank()) {
            return name;
        }

        String target = dockerRepositoryResource.getTarget() == null ? "" : dockerRepositoryResource.getTarget().trim();
        return target;
    }

    private String legacyManagedGroupName(String target) {
        String normalizedTarget = target == null ? "" : target.trim();
        return normalizedTarget.isBlank() ? "" : "Dockerrepository: " + normalizedTarget;
    }

    private void renameManagedDockerGroupIfNeeded(MonitoredResource existing, MonitoredResource saved) {
        String preferredGroupName = managedGroupName(saved);
        Optional<ResourceGroup> existingPreferred = resourceGroupRepository.findByNameIgnoreCase(preferredGroupName);
        if (existingPreferred.isPresent()) {
            return;
        }

        LinkedHashSet<String> candidateNames = new LinkedHashSet<>(managedGroupNames(saved));
        if (existing != null) {
            candidateNames.addAll(managedGroupNames(existing));
        }

        for (String candidateName : candidateNames) {
            if (candidateName.equalsIgnoreCase(preferredGroupName)) {
                continue;
            }

            Optional<ResourceGroup> candidateGroup = resourceGroupRepository.findByNameIgnoreCase(candidateName);
            if (candidateGroup.isPresent()) {
                ResourceGroup group = candidateGroup.get();
                group.setName(preferredGroupName);
                resourceGroupRepository.save(group);
                return;
            }
        }
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

    private static final DateTimeFormatter LATENCY_SAMPLE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Maximum number of raw check results returned by the latency-samples endpoint. */
    private static final int LATENCY_SAMPLE_MAX = 500;

    /**
     * Returns up to {@value #LATENCY_SAMPLE_MAX} chronological latency samples for the given
     * resource and time window. Results are evenly downsampled when the raw count exceeds the cap.
     */
    public List<LatencySampleDTO> getLatencySamples(MonitoredResource resource, int hours) {
        int safeHours = Math.max(hours, 1);
        LocalDateTime since = LocalDateTime.now().minusHours(safeHours);
        List<CheckResult> all = checkResultRepository
                .findByResourceAndCheckedAtAfterOrderByCheckedAtAsc(resource, since);
        List<CheckResult> withLatency = all.stream()
                .filter(r -> r.getLatencyMs() != null)
                .collect(Collectors.toList());
        List<CheckResult> sampled;
        if (withLatency.size() <= LATENCY_SAMPLE_MAX) {
            sampled = withLatency;
        } else {
            sampled = new ArrayList<>(LATENCY_SAMPLE_MAX);
            double step = (double) withLatency.size() / LATENCY_SAMPLE_MAX;
            for (int i = 0; i < LATENCY_SAMPLE_MAX; i++) {
                sampled.add(withLatency.get((int) (i * step)));
            }
        }
        return sampled.stream()
                .map(r -> new LatencySampleDTO(
                        r.getLatencyMs(),
                        r.getDnsResolutionMs(),
                        r.getConnectMs(),
                        r.getTlsHandshakeMs(),
                        r.getCheckedAt().format(LATENCY_SAMPLE_FORMATTER)))
                .collect(Collectors.toList());
    }

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
            blocks.add(new TimelineBlockDTO(
                    status,
                    timestamp,
                    lastInBucket == null ? null : lastInBucket.getLatencyMs(),
                    lastInBucket == null ? null : lastInBucket.getDnsResolutionMs(),
                    lastInBucket == null ? null : lastInBucket.getConnectMs(),
                    lastInBucket == null ? null : lastInBucket.getTlsHandshakeMs()
            ));
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
        List<MonitoredResource> resources = resourceRepository.findByGroups_Id(groupId);
        resourceGroupRepository.findById(groupId).ifPresent(group ->
            resources.forEach(resource -> resource.getGroups().remove(group))
        );
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

package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.OutageRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutageService {

    private final OutageRepository outageRepository;
    private final CheckResultRepository checkResultRepository;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    /**
     * Evaluates whether an outage should be opened or closed for the given resource,
     * based on the most recent check results and the configured thresholds.
     * Must be called after a new CheckResult has been persisted.
     */
    @Transactional
    public void evaluate(MonitoredResource resource) {
        ResourceTypeConfig config = resourceTypeConfigRepository
                .findByTypeName(resource.getResourceType().name())
                .orElse(null);
        if (config == null) {
            return;
        }

        int outageThreshold = Math.max(1, config.getOutageThreshold());
        int recoveryThreshold = Math.max(1, config.getRecoveryThreshold());

        Optional<Outage> activeOutage = resolveActiveOutage(resource);

        if (activeOutage.isEmpty()) {
            // No active outage – check whether to open one
            List<CheckResult> recent = checkResultRepository
                    .findByResourceOrderByCheckedAtDesc(resource, PageRequest.of(0, outageThreshold))
                    .getContent();

            if (recent.size() >= outageThreshold
                    && recent.stream().allMatch(r -> r.getStatus() == CheckStatus.NOT_AVAILABLE)) {

                // The earliest of the N failing checks is the start time
                LocalDateTime startDate = recent.get(recent.size() - 1).getCheckedAt();
                Outage outage = Outage.builder()
                        .resource(resource)
                        .startDate(startDate)
                        .active(true)
                        .build();
                outageRepository.save(outage);
                log.info("Outage opened for resource '{}' (id={}) starting at {}",
                        resource.getName(), resource.getId(), startDate);
            }
        } else {
            // Active outage – check whether to close it
            List<CheckResult> recent = checkResultRepository
                    .findByResourceOrderByCheckedAtDesc(resource, PageRequest.of(0, recoveryThreshold))
                    .getContent();

            if (recent.size() >= recoveryThreshold
                    && recent.stream().allMatch(r -> r.getStatus() == CheckStatus.AVAILABLE)) {

                // The most recent successful check is the end time
                LocalDateTime endDate = recent.get(0).getCheckedAt();
                Outage outage = activeOutage.get();
                outage.setEndDate(endDate);
                outage.setActive(false);
                outageRepository.save(outage);
                log.info("Outage closed for resource '{}' (id={}) ending at {}",
                        resource.getName(), resource.getId(), endDate);
            }
        }
    }


        private static final DateTimeFormatter ACTIVE_OUTAGE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        /**
         * Returns a map of resourceId → ISO outage start datetime for all currently active outages,
         * loaded in a single batch query (no N+1).
         */
        public Map<Long, String> findAllActiveSinceByResourceId() {
        return outageRepository.findAllActiveWithResource().stream()
            .collect(Collectors.toMap(
                outage -> outage.getResource().getId(),
                outage -> outage.getStartDate().format(ACTIVE_OUTAGE_FORMATTER),
                (a, b) -> a   // keep first (most recent) when duplicates exist per resource
            ));
        }

    public Optional<Outage> findActiveOutage(MonitoredResource resource) {
        return resolveActiveOutage(resource);
    }

    public List<Outage> findByResource(MonitoredResource resource) {
        return outageRepository.findByResourceOrderByStartDateDesc(resource);
    }

    public List<Outage> findAll() {
        return outageRepository.findAllByOrderByStartDateDesc();
    }

    private Optional<Outage> resolveActiveOutage(MonitoredResource resource) {
        List<Outage> activeOutages = outageRepository.findByResourceAndActiveTrueOrderByStartDateDesc(resource);
        if (activeOutages.isEmpty()) {
            return Optional.empty();
        }

        Outage primary = activeOutages.get(0);
        if (activeOutages.size() > 1) {
            List<Outage> duplicates = new ArrayList<>(activeOutages.subList(1, activeOutages.size()));
            List<Outage> toDelete = new ArrayList<>();
            List<Outage> toClose = new ArrayList<>();

            for (Outage duplicate : duplicates) {
                if (duplicate.getStartDate() != null && duplicate.getStartDate().equals(primary.getStartDate())) {
                    toDelete.add(duplicate);
                    continue;
                }

                duplicate.setActive(false);
                if (duplicate.getEndDate() == null) {
                    duplicate.setEndDate(primary.getStartDate());
                }
                toClose.add(duplicate);
            }

            if (!toDelete.isEmpty()) {
                outageRepository.deleteAll(toDelete);
            }
            if (!toClose.isEmpty()) {
                outageRepository.saveAll(toClose);
            }

            log.warn("Found {} active outages for resource '{}' (id={}). Kept newest id={}, deleted {} exact duplicates (same start), auto-closed {} older duplicates.",
                    activeOutages.size(), resource.getName(), resource.getId(), primary.getId(), toDelete.size(), toClose.size());
        }

        return Optional.of(primary);
    }
}

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
import java.util.List;
import java.util.Optional;

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

        Optional<Outage> activeOutage = outageRepository.findByResourceAndActiveTrue(resource);

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

    public Optional<Outage> findActiveOutage(MonitoredResource resource) {
        return outageRepository.findByResourceAndActiveTrue(resource);
    }

    public List<Outage> findByResource(MonitoredResource resource) {
        return outageRepository.findByResourceOrderByStartDateDesc(resource);
    }

    public List<Outage> findAll() {
        return outageRepository.findAllByOrderByStartDateDesc();
    }
}

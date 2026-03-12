package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsService {

    private final MeterRegistry meterRegistry;
    private final MonitoredResourceRepository resourceRepository;
    private final CheckResultRepository checkResultRepository;

    @PostConstruct
    public void registerMetrics() {
        List<MonitoredResource> resources = resourceRepository.findAllActiveForLanding();
        for (MonitoredResource resource : resources) {
            registerResourceMetric(resource);
        }
    }

    public void registerResourceMetric(MonitoredResource resource) {
        Gauge.builder("kairos_resource_status", resource, r -> getStatusValue(r))
                .tag("resource_name", resource.getName())
                .tag("resource_type", resource.getResourceType().name())
                .description("Resource status: 1=available, 0=not_available, -1=unknown")
                .register(meterRegistry);
    }

    private double getStatusValue(MonitoredResource resource) {
        Optional<CheckResult> latest = checkResultRepository.findTopByResourceOrderByCheckedAtDesc(resource);
        if (latest.isEmpty()) return -1;
        CheckStatus status = latest.get().getStatus();
        return switch (status) {
            case AVAILABLE -> 1;
            case NOT_AVAILABLE -> 0;
            default -> -1;
        };
    }
}

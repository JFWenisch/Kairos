package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CheckExecutorService {

    private final HttpCheckService httpCheckService;
    private final DockerCheckService dockerCheckService;
    private final ResourceStatusStreamService resourceStatusStreamService;
    private final CheckResultRepository checkResultRepository;
    private final MonitoredResourceRepository resourceRepository;
    private final ResourceTypeConfigRepository configRepository;

    private final Map<String, Long> lastRunTimeMap = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executorMap = new ConcurrentHashMap<>();

    public CheckExecutorService(
            HttpCheckService httpCheckService,
            DockerCheckService dockerCheckService,
            ResourceStatusStreamService resourceStatusStreamService,
            CheckResultRepository checkResultRepository,
            MonitoredResourceRepository resourceRepository,
            ResourceTypeConfigRepository configRepository) {
        this.httpCheckService = httpCheckService;
        this.dockerCheckService = dockerCheckService;
        this.resourceStatusStreamService = resourceStatusStreamService;
        this.checkResultRepository = checkResultRepository;
        this.resourceRepository = resourceRepository;
        this.configRepository = configRepository;
    }

    @Scheduled(fixedDelay = 30000)
    public void dispatch() {
        long now = System.currentTimeMillis();

        for (ResourceType type : ResourceType.values()) {
            String typeName = type.name();
            ResourceTypeConfig config = configRepository.findByTypeName(typeName).orElse(null);
            if (config == null) continue;

            long intervalMs = (long) config.getCheckIntervalMinutes() * 60 * 1000;
            long lastRun = lastRunTimeMap.getOrDefault(typeName, 0L);

            if (now - lastRun >= intervalMs) {
                lastRunTimeMap.put(typeName, now);
                int parallelism = Math.max(1, config.getParallelism());
                ExecutorService executor = executorMap.computeIfAbsent(typeName,
                        k -> Executors.newFixedThreadPool(parallelism));

                List<MonitoredResource> resources = resourceRepository.findByResourceTypeAndActiveTrue(type);
                for (MonitoredResource resource : resources) {
                    final MonitoredResource r = resource;
                    executor.submit(() -> {
                        try {
                            resourceStatusStreamService.publishResourceChecking(r);
                            if (type == ResourceType.HTTP) {
                                httpCheckService.check(r);
                            } else if (type == ResourceType.DOCKER) {
                                dockerCheckService.check(r);
                            }
                        } catch (Exception e) {
                            log.error("Error checking resource {}: {}", r.getName(), e.getMessage(), e);
                        }
                    });
                }
                log.debug("Dispatched checks for type {} ({} resources)", typeName, resources.size());
            }
        }
    }

    @Transactional
    public boolean runImmediateCheck(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .map(this::runImmediateCheck)
                .orElse(false);
    }

    @Transactional
    public boolean runImmediateCheck(MonitoredResource resource) {
        if (resource == null || !resource.isActive()) {
            return false;
        }

        resourceStatusStreamService.publishResourceChecking(resource);
        if (resource.getResourceType() == ResourceType.HTTP) {
            httpCheckService.check(resource);
            return true;
        }
        if (resource.getResourceType() == ResourceType.DOCKER) {
            dockerCheckService.check(resource);
            return true;
        }
        return false;
    }

    /**
     * Run an immediate check pass as soon as the application is fully ready.
     * This fires after all ApplicationRunner beans (including DataInitializer) have completed,
     * ensuring resource-type configs are already present in the database.
     * Without this, the first @Scheduled dispatch can race with DataInitializer and find no
     * configs, delaying the first check by one full fixedDelay period (30 s).
     */
    @EventListener(ApplicationReadyEvent.class)
    public void runChecksOnStartup() {
        log.info("Application ready – running initial resource checks");
        dispatch();
    }

    @PreDestroy
    public void shutdown() {
        executorMap.forEach((type, executor) -> {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        });
    }
}

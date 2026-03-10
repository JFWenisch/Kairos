package com.jfwendisch.kairos.service;

import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.entity.ResourceType;
import com.jfwendisch.kairos.entity.ResourceTypeConfig;
import com.jfwendisch.kairos.repository.MonitoredResourceRepository;
import com.jfwendisch.kairos.repository.ResourceTypeConfigRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    private final UrlCheckService urlCheckService;
    private final DockerCheckService dockerCheckService;
    private final MonitoredResourceRepository resourceRepository;
    private final ResourceTypeConfigRepository configRepository;

    private final Map<String, Long> lastRunTimeMap = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executorMap = new ConcurrentHashMap<>();

    public CheckExecutorService(
            UrlCheckService urlCheckService,
            DockerCheckService dockerCheckService,
            MonitoredResourceRepository resourceRepository,
            ResourceTypeConfigRepository configRepository) {
        this.urlCheckService = urlCheckService;
        this.dockerCheckService = dockerCheckService;
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
                            if (type == ResourceType.URL) {
                                urlCheckService.check(r);
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

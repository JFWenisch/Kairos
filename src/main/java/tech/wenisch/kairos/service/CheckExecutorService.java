package tech.wenisch.kairos.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import tech.wenisch.kairos.entity.DiscoveryServiceConfig;
import tech.wenisch.kairos.entity.DiscoveryServiceType;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceDiscovery;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.DiscoveryServiceConfigRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.ResourceDiscoveryRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

@Service
@Slf4j
public class CheckExecutorService {

    private final HttpCheckService httpCheckService;
    private final DockerCheckService dockerCheckService;
    private final TcpCheckService tcpCheckService;
    private final DockerRepositorySyncService dockerRepositorySyncService;
    private final OpenshiftRouteSyncService openshiftRouteSyncService;
    private final ResourceStatusStreamService resourceStatusStreamService;
    private final MonitoredResourceRepository resourceRepository;
    private final ResourceTypeConfigRepository configRepository;
    private final ResourceDiscoveryRepository resourceDiscoveryRepository;
    private final DiscoveryServiceConfigRepository discoveryConfigRepository;
    private final CheckAuditService checkAuditService;

    private final Map<String, Long> lastRunTimeMap = new ConcurrentHashMap<>();
    private final Map<String, ExecutorService> executorMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> executorParallelismMap = new ConcurrentHashMap<>();

    public CheckExecutorService(
            HttpCheckService httpCheckService,
            DockerCheckService dockerCheckService,
            TcpCheckService tcpCheckService,
            DockerRepositorySyncService dockerRepositorySyncService,
            OpenshiftRouteSyncService openshiftRouteSyncService,
            ResourceStatusStreamService resourceStatusStreamService,
            MonitoredResourceRepository resourceRepository,
            ResourceTypeConfigRepository configRepository,
            ResourceDiscoveryRepository resourceDiscoveryRepository,
            DiscoveryServiceConfigRepository discoveryConfigRepository,
            CheckAuditService checkAuditService) {
        this.httpCheckService = httpCheckService;
        this.dockerCheckService = dockerCheckService;
        this.tcpCheckService = tcpCheckService;
        this.dockerRepositorySyncService = dockerRepositorySyncService;
        this.openshiftRouteSyncService = openshiftRouteSyncService;
        this.resourceStatusStreamService = resourceStatusStreamService;
        this.resourceRepository = resourceRepository;
        this.configRepository = configRepository;
        this.resourceDiscoveryRepository = resourceDiscoveryRepository;
        this.discoveryConfigRepository = discoveryConfigRepository;
        this.checkAuditService = checkAuditService;
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
                ExecutorService executor = resolveExecutor(typeName, parallelism);

                List<MonitoredResource> resources = resourceRepository.findByResourceTypeAndActiveTrue(type);
                for (MonitoredResource resource : resources) {
                    submitCheck(executor, resource, type, false);
                }
                log.debug("Dispatched checks for type {} ({} resources)", typeName, resources.size());
            }
        }

        for (DiscoveryServiceType type : DiscoveryServiceType.values()) {
            String typeName = type.name();
            DiscoveryServiceConfig config = discoveryConfigRepository.findByTypeName(typeName).orElse(null);
            if (config == null) continue;

            long intervalMs = (long) config.getSyncIntervalMinutes() * 60 * 1000;
            long lastRun = lastRunTimeMap.getOrDefault(typeName, 0L);

            if (now - lastRun >= intervalMs) {
                lastRunTimeMap.put(typeName, now);
                int parallelism = Math.max(1, config.getParallelism());
                ExecutorService executor = resolveExecutor(typeName, parallelism);

                List<ResourceDiscovery> discoveries = resourceDiscoveryRepository.findByTypeAndActiveTrue(type);
                for (ResourceDiscovery discovery : discoveries) {
                    submitDiscoverySync(executor, discovery);
                }
                log.debug("Dispatched discovery sync for type {} ({} services)", typeName, discoveries.size());
            }
        }
    }

    public boolean runImmediateCheck(Long resourceId) {
        return resourceRepository.findById(resourceId)
                .map(this::runImmediateCheck)
                .orElse(false);
    }

    public boolean runImmediateCheck(Long resourceId, String triggeredBy) {
        return resourceRepository.findById(resourceId)
                .map(r -> runImmediateCheck(r, triggeredBy))
                .orElse(false);
    }

    public boolean runImmediateCheck(MonitoredResource resource, String triggeredBy) {
        if (resource == null || !resource.isActive()) {
            return false;
        }
        ResourceType type = resource.getResourceType();
        if (type == null) {
            return false;
        }
        int parallelism = configRepository.findByTypeName(type.name())
                .map(ResourceTypeConfig::getParallelism)
                .map(value -> Math.max(1, value))
                .orElse(1);
        ExecutorService executor = resolveExecutor(type.name(), parallelism);
        return submitCheck(executor, resource, type, "Check Now", triggeredBy);
    }

    public boolean runImmediateCheck(MonitoredResource resource) {
        if (resource == null || !resource.isActive()) {
            return false;
        }

        ResourceType type = resource.getResourceType();
        if (type == null) {
            return false;
        }

        int parallelism = configRepository.findByTypeName(type.name())
                .map(ResourceTypeConfig::getParallelism)
                .map(value -> Math.max(1, value))
                .orElse(1);

        ExecutorService executor = resolveExecutor(type.name(), parallelism);
        return submitCheck(executor, resource, type, true);
    }

    public boolean runImmediateDiscoverySync(ResourceDiscovery discovery) {
        if (discovery == null || !discovery.isActive() || discovery.getType() == null) {
            return false;
        }

        int parallelism = discoveryConfigRepository.findByTypeName(discovery.getType().name())
                .map(DiscoveryServiceConfig::getParallelism)
                .map(value -> Math.max(1, value))
                .orElse(1);

        ExecutorService executor = resolveExecutor(discovery.getType().name(), parallelism);
        return submitDiscoverySync(executor, discovery);
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

    private ExecutorService resolveExecutor(String typeName, int parallelism) {
        Integer currentParallelism = executorParallelismMap.get(typeName);
        if (currentParallelism != null && currentParallelism == parallelism) {
            return executorMap.get(typeName);
        }

        ExecutorService newExecutor = createExecutor(typeName, parallelism);
        ExecutorService oldExecutor = executorMap.put(typeName, newExecutor);
        executorParallelismMap.put(typeName, parallelism);

        if (oldExecutor != null) {
            oldExecutor.shutdown();
        }

        return newExecutor;
    }

    private ExecutorService createExecutor(String typeName, int parallelism) {
        int queueCapacity = Math.max(50, parallelism * 20);
        AtomicInteger threadCounter = new AtomicInteger(0);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("kairos-check-" + typeName.toLowerCase() + "-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };

        return new ThreadPoolExecutor(
                parallelism,
                parallelism,
                30,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                threadFactory
        );
    }

    private boolean submitCheck(ExecutorService executor,
                                MonitoredResource resource,
                                ResourceType type,
                                @SuppressWarnings("unused") boolean immediate) {
        return submitCheck(executor, resource, type, "Scheduled", "System");
    }

    private boolean submitCheck(ExecutorService executor,
                                MonitoredResource resource,
                                ResourceType type,
                                String kind,
                                String triggeredBy) {
        try {
            executor.submit(() -> executeCheck(resource, type, kind, triggeredBy));
            return true;
        } catch (RejectedExecutionException ex) {
            log.warn("Skipping check for resource '{}' (type {}) because the worker queue is full",
                    resource.getName(), type.name());
            return false;
        }
    }

    private void executeCheck(MonitoredResource resource, ResourceType type, String kind, String triggeredBy) {
        try {
            if (type == ResourceType.HTTP) {
                publishCheckingState(resource);
                tech.wenisch.kairos.entity.CheckResult cr = httpCheckService.check(resource);
                String result = cr != null && cr.getStatus() != null ? cr.getStatus().name() : "UNKNOWN";
                checkAuditService.record(kind, resource.getName(), resource.getTarget(), triggeredBy, result);
            } else if (type == ResourceType.DOCKER) {
                publishCheckingState(resource);
                tech.wenisch.kairos.entity.CheckResult cr = dockerCheckService.check(resource);
                String result = cr != null && cr.getStatus() != null ? cr.getStatus().name() : "UNKNOWN";
                checkAuditService.record(kind, resource.getName(), resource.getTarget(), triggeredBy, result);
            } else if (type == ResourceType.TCP) {
                publishCheckingState(resource);
                tech.wenisch.kairos.entity.CheckResult cr = tcpCheckService.check(resource);
                String result = cr != null && cr.getStatus() != null ? cr.getStatus().name() : "UNKNOWN";
                checkAuditService.record(kind, resource.getName(), resource.getTarget(), triggeredBy, result);
            }
        } catch (Exception e) {
            log.error("Error checking resource {}: {}", resource.getName(), e.getMessage(), e);
        }
    }

    private boolean submitDiscoverySync(ExecutorService executor, ResourceDiscovery discovery) {
        try {
            executor.submit(() -> executeDiscoverySync(discovery));
            return true;
        } catch (RejectedExecutionException ex) {
            log.warn("Skipping discovery sync for '{}' (type {}) because the worker queue is full",
                    discovery.getName(), discovery.getType().name());
            return false;
        }
    }

    private void executeDiscoverySync(ResourceDiscovery discovery) {
        try {
            if (discovery.getType() == DiscoveryServiceType.DOCKER_REPOSITORY) {
                dockerRepositorySyncService.sync(discovery);
            } else if (discovery.getType() == DiscoveryServiceType.OPENSHIFT_ROUTE) {
                openshiftRouteSyncService.sync(discovery);
            }
        } catch (Exception e) {
            log.error("Error syncing discovery service {}: {}", discovery.getName(), e.getMessage(), e);
        }
    }

    private void publishCheckingState(MonitoredResource resource) {
        try {
            resourceStatusStreamService.publishResourceChecking(resource);
        } catch (Throwable ex) {
            log.trace("Ignoring transient SSE failure while marking resource '{}' as checking: {}",
                    resource.getName(), ex.getMessage());
        }
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

package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.wenisch.kairos.dto.ResourceStatusUpdateDTO;
import tech.wenisch.kairos.entity.MonitoredResource;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceStatusStreamService {

    private final ResourceService resourceService;
    private final OutageService outageService;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private static final DateTimeFormatter OUTAGE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));

        sendEvent(emitter, "connected", Map.of("status", "ok"));
        return emitter;
    }

    public void publishResourceUpdate(MonitoredResource resource) {
        String activeOutageSince = outageService.findActiveOutage(resource)
            .map(o -> o.getStartDate().format(OUTAGE_FORMATTER))
            .orElse(null);
        ResourceStatusUpdateDTO update = buildUpdate(resource, 24, activeOutageSince);
        for (SseEmitter emitter : emitters) {
            safelySend(emitter, "resource-update", update);
        }
    }

    public void publishResourceChecking(MonitoredResource resource) {
        Map<String, Long> payload = Map.of("resourceId", resource.getId());
        for (SseEmitter emitter : emitters) {
            safelySend(emitter, "resource-checking", payload);
        }
    }

    public List<ResourceStatusUpdateDTO> getSnapshot() {
        return buildSnapshot(24);
    }

    public List<ResourceStatusUpdateDTO> getSnapshot(int hours) {
        return buildSnapshot(hours);
    }

    public List<ResourceStatusUpdateDTO> getSnapshot(int hours, boolean includeTimeline) {
        return buildSnapshot(hours, includeTimeline);
    }

    public Optional<ResourceStatusUpdateDTO> getSnapshotForResource(Long resourceId, int hours) {
        return resourceService.findById(resourceId)
            .map(resource -> {
                String activeOutageSince = outageService.findActiveOutage(resource)
                    .map(o -> o.getStartDate().format(OUTAGE_FORMATTER))
                    .orElse(null);
                return buildUpdate(resource, hours, activeOutageSince);
            });
    }

    public Optional<ResourceStatusUpdateDTO> getSnapshotForResource(Long resourceId, int hours, boolean includeTimeline) {
        return resourceService.findById(resourceId)
            .map(resource -> {
                String activeOutageSince = outageService.findActiveOutage(resource)
                    .map(o -> o.getStartDate().format(OUTAGE_FORMATTER))
                    .orElse(null);
                return buildUpdate(resource, hours, activeOutageSince, includeTimeline);
            });
    }

    private List<ResourceStatusUpdateDTO> buildSnapshot() {
        return buildSnapshot(24);
    }

    private List<ResourceStatusUpdateDTO> buildSnapshot(int hours) {
        Map<Long, String> outageMap = outageService.findAllActiveSinceByResourceId();
        return resourceService.findAllActive().stream()
            .map(resource -> buildUpdate(resource, hours, outageMap.get(resource.getId())))
            .toList();
    }

    private List<ResourceStatusUpdateDTO> buildSnapshot(int hours, boolean includeTimeline) {
        Map<Long, String> outageMap = outageService.findAllActiveSinceByResourceId();
        return resourceService.findAllActive().stream()
            .map(resource -> buildUpdate(resource, hours, outageMap.get(resource.getId()), includeTimeline))
            .toList();
    }

    private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource) {
        return buildUpdate(resource, 24, null);
    }

    private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource, int hours) {
        return buildUpdate(resource, hours, null);
        }

    private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource, int hours, String activeOutageSince) {
        return buildUpdate(resource, hours, activeOutageSince, true);
    }

    private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource, int hours, String activeOutageSince, boolean includeTimeline) {
        if (!includeTimeline) {
            return new ResourceStatusUpdateDTO(
                resource.getId(),
                resourceService.getCurrentStatus(resource),
                Collections.emptyList(),
                0.0,
                activeOutageSince
            );
        }

        ResourceService.TimelineData timelineData = resourceService.getTimelineData(resource, hours);
        return new ResourceStatusUpdateDTO(
            resource.getId(),
            resourceService.getCurrentStatus(resource),
            timelineData.timelineBlocks(),
            timelineData.uptimePercentage(),
            activeOutageSince
        );
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Throwable ex) {
            emitters.remove(emitter);
            if (isClientDisconnect(ex)) {
                log.trace("Removed SSE emitter after client disconnect on '{}': {}", eventName, ex.getMessage());
            } else {
                log.debug("Removed stale SSE emitter after send failure on '{}': {}", eventName, ex.getMessage());
            }
        }
    }

    private void safelySend(SseEmitter emitter, String eventName, Object payload) {
        try {
            sendEvent(emitter, eventName, payload);
        } catch (Throwable ex) {
            emitters.remove(emitter);
            if (isClientDisconnect(ex)) {
                log.trace("Suppressed SSE disconnect while broadcasting '{}': {}", eventName, ex.getMessage());
            } else {
                log.debug("Suppressed SSE send error while broadcasting '{}': {}", eventName, ex.getMessage());
            }
        }
    }

    private boolean isClientDisconnect(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                if (normalized.contains("broken pipe")
                        || normalized.contains("connection reset")
                        || normalized.contains("connection abort")
                        || normalized.contains("forcibly closed")
                        || normalized.contains("softwaregesteuert")
                        || normalized.contains("abgebrochen")) {
                    return true;
                }
            }

            String className = current.getClass().getName();
            if ("org.apache.catalina.connector.ClientAbortException".equals(className)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}

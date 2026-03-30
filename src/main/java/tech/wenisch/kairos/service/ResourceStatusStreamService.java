package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.wenisch.kairos.dto.ResourceStatusUpdateDTO;
import tech.wenisch.kairos.entity.MonitoredResource;

import java.time.format.DateTimeFormatter;
import java.util.List;
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
            sendEvent(emitter, "resource-update", update);
        }
    }

    public void publishResourceChecking(MonitoredResource resource) {
        Map<String, Long> payload = Map.of("resourceId", resource.getId());
        for (SseEmitter emitter : emitters) {
            sendEvent(emitter, "resource-checking", payload);
        }
    }

    public List<ResourceStatusUpdateDTO> getSnapshot() {
        return buildSnapshot(24);
    }

    public List<ResourceStatusUpdateDTO> getSnapshot(int hours) {
        return buildSnapshot(hours);
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

    private List<ResourceStatusUpdateDTO> buildSnapshot() {
        return buildSnapshot(24);
    }

    private List<ResourceStatusUpdateDTO> buildSnapshot(int hours) {
        Map<Long, String> outageMap = outageService.findAllActiveSinceByResourceId();
        return resourceService.findAllActive().stream()
            .map(resource -> buildUpdate(resource, hours, outageMap.get(resource.getId())))
            .toList();
    }

    private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource) {
        return buildUpdate(resource, 24, null);
    }

    private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource, int hours) {
        return buildUpdate(resource, hours, null);
        }

        private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource, int hours, String activeOutageSince) {
        return new ResourceStatusUpdateDTO(
            resource.getId(),
            resourceService.getCurrentStatus(resource),
            resourceService.getTimelineBlocks(resource, hours),
            resourceService.getUptimePercentage(resource, hours),
            activeOutageSince
        );
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (Exception ex) {
            emitters.remove(emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
            log.debug("Removed stale SSE emitter after send failure: {}", ex.getMessage());
        }
    }
}

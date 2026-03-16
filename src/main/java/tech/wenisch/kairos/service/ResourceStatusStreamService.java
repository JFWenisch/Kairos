package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.wenisch.kairos.dto.ResourceStatusUpdateDTO;
import tech.wenisch.kairos.entity.MonitoredResource;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceStatusStreamService {

    private final ResourceService resourceService;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(ex -> emitters.remove(emitter));

        sendEvent(emitter, "snapshot", buildSnapshot());
        return emitter;
    }

    public void publishResourceUpdate(MonitoredResource resource) {
        ResourceStatusUpdateDTO update = buildUpdate(resource);
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
        return buildSnapshot();
    }

    private List<ResourceStatusUpdateDTO> buildSnapshot() {
        return resourceService.findAllActive().stream()
                .map(this::buildUpdate)
                .toList();
    }

    private ResourceStatusUpdateDTO buildUpdate(MonitoredResource resource) {
        return new ResourceStatusUpdateDTO(
                resource.getId(),
                resourceService.getCurrentStatus(resource),
                resourceService.getTimelineBlocks(resource),
                resourceService.getUptimePercentage(resource, 24)
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

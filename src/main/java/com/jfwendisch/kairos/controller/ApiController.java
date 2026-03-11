package com.jfwendisch.kairos.controller;

import com.jfwendisch.kairos.dto.ResourceDTO;
import com.jfwendisch.kairos.dto.ResourceDetailsDTO;
import com.jfwendisch.kairos.entity.CheckResult;
import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ResourceService resourceService;

    @GetMapping("/resources")
    public ResponseEntity<List<MonitoredResource>> listResources() {
        return ResponseEntity.ok(resourceService.findAllActive());
    }

    @GetMapping("/resources/{id}")
    public ResponseEntity<ResourceDetailsDTO> getResourceById(@PathVariable Long id) {
        return resourceService.findById(id)
                .map(resource -> {
                    Optional<CheckResult> latestCheckResult = resourceService.getLatestCheckResult(resource);
                    ResourceDetailsDTO response = new ResourceDetailsDTO(
                            resource.getId(),
                            resource.getName(),
                            resource.getResourceType(),
                            resource.getTarget(),
                            resource.isActive(),
                            resource.getCreatedAt(),
                            resourceService.getCurrentStatus(resource),
                            latestCheckResult.map(CheckResult::getStatus).orElse(null),
                            latestCheckResult.map(CheckResult::getCheckedAt).orElse(null),
                            latestCheckResult.map(CheckResult::getMessage).orElse(null),
                            latestCheckResult.map(CheckResult::getErrorCode).orElse(null)
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/resources")
    public ResponseEntity<MonitoredResource> addResource(@RequestBody ResourceDTO dto) {
        MonitoredResource resource = MonitoredResource.builder()
                .name(dto.getName())
                .resourceType(dto.getResourceType())
                .target(dto.getTarget())
                .active(true)
                .build();
        return ResponseEntity.ok(resourceService.save(resource));
    }

    @DeleteMapping("/resources/{id}")
    public ResponseEntity<Map<String, String>> deleteResource(@PathVariable Long id) {
        resourceService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @GetMapping("/resources/{id}/history")
    public ResponseEntity<List<CheckResult>> getHistory(@PathVariable Long id) {
        return resourceService.findById(id)
                .map(r -> ResponseEntity.ok(resourceService.getFullHistory(id)))
                .orElse(ResponseEntity.notFound().build());
    }
}

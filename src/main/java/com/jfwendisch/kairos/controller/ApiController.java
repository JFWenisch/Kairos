package com.jfwendisch.kairos.controller;

import com.jfwendisch.kairos.dto.ResourceDTO;
import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.repository.ResourceTypeConfigRepository;
import com.jfwendisch.kairos.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ResourceService resourceService;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    @GetMapping("/resources")
    public ResponseEntity<List<MonitoredResource>> listResources() {
        return ResponseEntity.ok(resourceService.findAllActive());
    }

    @PostMapping("/resources")
    public ResponseEntity<?> addResource(@RequestBody ResourceDTO dto) {
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
    public ResponseEntity<?> getHistory(@PathVariable Long id) {
        return resourceService.findById(id)
                .map(r -> ResponseEntity.ok(resourceService.getFullHistory(id)))
                .orElse(ResponseEntity.notFound().build());
    }
}

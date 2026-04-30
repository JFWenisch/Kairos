package tech.wenisch.kairos.dto;

import tech.wenisch.kairos.entity.ResourceType;

import java.time.LocalDateTime;

public record OutageDTO(
        Long id,
        Long resourceId,
        String resourceName,
        ResourceType resourceType,
        LocalDateTime startDate,
        LocalDateTime endDate,
        boolean active,
        Long durationMinutes
) {
}

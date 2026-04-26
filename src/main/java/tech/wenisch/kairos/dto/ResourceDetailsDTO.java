package tech.wenisch.kairos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.ResourceType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Detailed view of a single monitored resource including its latest health-check result.
 *
 * <p>{@code groupId} and {@code groupName} are retained for backward compatibility and reflect
 * the first assigned group (sorted by ID). Prefer the {@code groups} list for multi-group-aware clients.
 */
public record ResourceDetailsDTO(
        Long id,
        String name,
        ResourceType resourceType,
        String target,
        Long groupId,
        String groupName,
        List<GroupSummaryDTO> groups,
        int displayOrder,
        @JsonProperty("skipTLS")
        boolean skipTls,
        boolean recursive,
        boolean active,
        LocalDateTime createdAt,
        String currentStatus,
        CheckStatus latestCheckStatus,
        LocalDateTime latestCheckedAt,
        String latestMessage,
        String latestErrorCode
) {
}

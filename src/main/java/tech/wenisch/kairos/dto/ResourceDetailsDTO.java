package tech.wenisch.kairos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.ResourceType;

import java.time.LocalDateTime;

public record ResourceDetailsDTO(
        Long id,
        String name,
        ResourceType resourceType,
        String target,
        Long groupId,
        String groupName,
        int displayOrder,
        @JsonProperty("skipTLS")
        boolean skipTls,
        boolean active,
        LocalDateTime createdAt,
        String currentStatus,
        CheckStatus latestCheckStatus,
        LocalDateTime latestCheckedAt,
        String latestMessage,
        String latestErrorCode
) {
}

package tech.wenisch.kairos.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.ResourceType;

import java.time.LocalDateTime;
import java.util.List;

public record ResourceDetailsDTO(
        Long id,
        String name,
        ResourceType resourceType,
        String target,
        /** @deprecated Use {@code groups} instead. */
        Long groupId,
        /** @deprecated Use {@code groups} instead. */
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

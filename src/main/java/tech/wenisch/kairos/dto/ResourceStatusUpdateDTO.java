package tech.wenisch.kairos.dto;

import java.util.List;

public record ResourceStatusUpdateDTO(
        Long resourceId,
        String currentStatus,
        List<TimelineBlockDTO> timelineBlocks,
        double uptimePercentage,
        /** ISO-8601 datetime string of the active outage start, or {@code null} if no active outage. */
        String activeOutageSince
) {
}

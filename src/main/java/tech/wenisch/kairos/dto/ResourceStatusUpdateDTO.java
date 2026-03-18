package tech.wenisch.kairos.dto;

import java.util.List;

public record ResourceStatusUpdateDTO(
        Long resourceId,
        String currentStatus,
        List<TimelineBlockDTO> timelineBlocks,
        double uptimePercentage
) {
}

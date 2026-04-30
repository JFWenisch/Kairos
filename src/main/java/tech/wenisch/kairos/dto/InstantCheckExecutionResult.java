package tech.wenisch.kairos.dto;

import lombok.Builder;
import tech.wenisch.kairos.entity.CheckStatus;

@Builder
public record InstantCheckExecutionResult(
        CheckStatus status,
        String message,
        String errorCode,
        Long latencyMs
) {
}

package tech.wenisch.kairos.dto;

import java.time.LocalDateTime;

public record TimelineBlockDTO(
        String status,
        LocalDateTime timestamp,
        Long latencyMs,
        Long dnsResolutionMs,
        Long connectMs,
        Long tlsHandshakeMs
) {
}
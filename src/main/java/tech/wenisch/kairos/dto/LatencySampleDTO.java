package tech.wenisch.kairos.dto;

public record LatencySampleDTO(
        Long latencyMs,
        Long dnsResolutionMs,
        Long connectMs,
        Long tlsHandshakeMs,
        String checkedAt) {
}

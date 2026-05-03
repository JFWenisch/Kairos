package tech.wenisch.kairos.service;

import java.time.LocalDateTime;

public record CheckAuditEntry(
        LocalDateTime timestamp,
        String kind,
        String resourceName,
        String target,
        String triggeredBy,
        String result
) {}

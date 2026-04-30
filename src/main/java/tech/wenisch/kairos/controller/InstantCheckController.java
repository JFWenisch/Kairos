package tech.wenisch.kairos.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import tech.wenisch.kairos.dto.InstantCheckExecutionResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.service.InstantCheckService;

@RestController
@RequiredArgsConstructor
public class InstantCheckController {

    private final InstantCheckService instantCheckService;

    @PostMapping("/instant-check")
    @ResponseBody
    public ResponseEntity<InstantCheckResponse> runInstantCheck(@RequestParam ResourceType resourceType,
                                                                @RequestParam String target,
                                                                @RequestParam(name = "skipTLS", defaultValue = "false") boolean skipTls,
                                                                Authentication authentication) {
        InstantCheckService.InstantCheckSettings settings = instantCheckService.getSettings();
        if (!settings.enabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new InstantCheckResponse("disabled", CheckStatus.UNKNOWN.name(), "Instant check is disabled.", "DISABLED", null));
        }

        if (!settings.allowPublic() && !instantCheckService.isAllowedForRequest(authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new InstantCheckResponse("forbidden", CheckStatus.UNKNOWN.name(), "Authentication is required for instant checks.", "AUTH_REQUIRED", null));
        }

        String normalizedTarget = target == null ? "" : target.trim();
        if (normalizedTarget.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new InstantCheckResponse("invalid", CheckStatus.UNKNOWN.name(), "Target is required.", "TARGET_REQUIRED", null));
        }

        if (!instantCheckService.isTargetAllowed(normalizedTarget, settings.allowedDomains())) {
            return ResponseEntity.badRequest()
                    .body(new InstantCheckResponse("blocked", CheckStatus.UNKNOWN.name(), "Target does not match allowed domain rules.", "TARGET_NOT_ALLOWED", null));
        }

        InstantCheckExecutionResult result = instantCheckService.runInstantCheck(
                resourceType,
                normalizedTarget,
                skipTls,
                settings.useStoredAuth()
        );

        String outcome = result.status() == CheckStatus.AVAILABLE ? "ok" : "error";
        return ResponseEntity.ok(new InstantCheckResponse(
                outcome,
                result.status().name(),
                result.message(),
                result.errorCode(),
                result.latencyMs()
        ));
    }

    public record InstantCheckResponse(
            String outcome,
            String status,
            String message,
            String errorCode,
            Long latencyMs
    ) {
    }
}

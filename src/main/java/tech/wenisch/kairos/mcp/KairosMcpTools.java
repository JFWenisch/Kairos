package tech.wenisch.kairos.mcp;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tech.wenisch.kairos.entity.Announcement;
import tech.wenisch.kairos.entity.AnnouncementKind;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.service.AnnouncementService;
import tech.wenisch.kairos.service.CheckAuditService;
import tech.wenisch.kairos.service.CheckExecutorService;
import tech.wenisch.kairos.service.InstantCheckService;
import tech.wenisch.kairos.service.OutageService;
import tech.wenisch.kairos.service.ResourceService;

/**
 * MCP tool definitions for Kairos. Each {@code @Tool}-annotated method is exposed
 * as an MCP tool that AI assistants can call.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class KairosMcpTools {

    private final ResourceService resourceService;
    private final CheckResultRepository checkResultRepository;
    private final AnnouncementService announcementService;
    private final OutageService outageService;
    private final CheckAuditService checkAuditService;
    private final CheckExecutorService checkExecutorService;
    private final InstantCheckService instantCheckService;

    @Tool(description = "List all active monitored resources with their current status. "
            + "Returns id, name, type (HTTP or DOCKER), target URL/image, and current check status "
            + "(AVAILABLE, NOT_AVAILABLE, or UNKNOWN) along with the last checked timestamp.")
    public List<Map<String, Object>> listResources() {
        return resourceService.findAllActive().stream()
                .map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("name", r.getName());
                    m.put("type", r.getResourceType() != null ? r.getResourceType().name() : null);
                    m.put("target", r.getTarget());
                    m.put("active", r.isActive());
                    checkResultRepository.findTopByResourceOrderByCheckedAtDesc(r).ifPresent(cr -> {
                        m.put("currentStatus", cr.getStatus() != null ? cr.getStatus().name() : "UNKNOWN");
                        m.put("lastCheckedAt", cr.getCheckedAt() != null ? cr.getCheckedAt().toString() : null);
                        m.put("lastMessage", cr.getMessage());
                    });
                    return m;
                })
                .toList();
    }

    @Tool(description = "Get details and current status of a specific monitored resource by its numeric ID. "
            + "Returns resource metadata (name, type, target, createdAt), current check status, "
            + "last check message, error code, and latency in milliseconds.")
    public Map<String, Object> getResource(
            @ToolParam(description = "The numeric ID of the resource") Long id) {
        Optional<MonitoredResource> opt = resourceService.findById(id);
        if (opt.isEmpty()) {
            return Map.of("error", "Resource not found with id: " + id);
        }
        MonitoredResource r = opt.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("type", r.getResourceType() != null ? r.getResourceType().name() : null);
        m.put("target", r.getTarget());
        m.put("active", r.isActive());
        m.put("createdAt", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
        checkResultRepository.findTopByResourceOrderByCheckedAtDesc(r).ifPresent(cr -> {
            m.put("currentStatus", cr.getStatus() != null ? cr.getStatus().name() : "UNKNOWN");
            m.put("lastCheckedAt", cr.getCheckedAt() != null ? cr.getCheckedAt().toString() : null);
            m.put("lastMessage", cr.getMessage());
            m.put("lastErrorCode", cr.getErrorCode());
            m.put("latencyMs", cr.getLatencyMs());
        });
        return m;
    }

    @Tool(description = "Trigger an immediate health check for a monitored resource by its numeric ID. "
            + "The check runs asynchronously in the background. "
            + "Use getCheckHistory to see the result after a moment.")
    @Transactional
    public Map<String, Object> triggerCheck(
            @ToolParam(description = "The numeric ID of the resource to check immediately") Long resourceId) {
        boolean submitted = checkExecutorService.runImmediateCheck(resourceId, "MCP");
        if (submitted) {
            return Map.of(
                    "status", "submitted",
                    "resourceId", resourceId,
                    "message", "Check submitted successfully. Use getCheckHistory to see the result.");
        } else {
            return Map.of(
                    "status", "failed",
                    "resourceId", resourceId,
                    "message", "Could not submit check — resource may not exist, be inactive, or the queue is full.");
        }
    }

    @Tool(description = "Get the check history for a monitored resource (most recent first, up to 50 entries). "
            + "Each entry includes status (AVAILABLE/NOT_AVAILABLE), checkedAt timestamp, message, "
            + "error code, and latency in milliseconds.")
    public List<Map<String, Object>> getCheckHistory(
            @ToolParam(description = "The numeric ID of the resource") Long resourceId) {
        Optional<MonitoredResource> opt = resourceService.findById(resourceId);
        if (opt.isEmpty()) {
            return List.of(Map.of("error", "Resource not found with id: " + resourceId));
        }
        return checkResultRepository.findByResourceOrderByCheckedAtDesc(opt.get())
                .stream()
                .limit(50)
                .map(cr -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("status", cr.getStatus() != null ? cr.getStatus().name() : "UNKNOWN");
                    m.put("checkedAt", cr.getCheckedAt() != null ? cr.getCheckedAt().toString() : null);
                    m.put("message", cr.getMessage());
                    m.put("errorCode", cr.getErrorCode());
                    m.put("latencyMs", cr.getLatencyMs());
                    return m;
                })
                .toList();
    }

    @Tool(description = "List all currently active announcements displayed to users. "
            + "Returns id, kind (INFORMATION/WARNING/PROBLEM), HTML content, active flag, "
            + "and optional expiry timestamp.")
    public List<Map<String, Object>> listAnnouncements() {
        return announcementService.findAllActiveForPublicView().stream()
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("kind", a.getKind() != null ? a.getKind().name() : null);
                    m.put("content", a.getContent());
                    m.put("active", a.isActive());
                    m.put("activeUntil", a.getActiveUntil() != null ? a.getActiveUntil().toString() : null);
                    m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : null);
                    return m;
                })
                .toList();
    }

    @Tool(description = "List outages across all monitored resources. "
            + "Pass activeOnly=true to see only ongoing outages, or false for all outages. "
            + "Each entry includes id, resourceId, resourceName, startDate, endDate, and active flag.")
    public List<Map<String, Object>> listOutages(
            @ToolParam(description = "If true, return only currently active (ongoing) outages; if false, return all") boolean activeOnly) {
        return outageService.findAllForApi().stream()
                .filter(o -> !activeOnly || o.isActive())
                .map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("active", o.isActive());
                    m.put("startDate", o.getStartDate() != null ? o.getStartDate().toString() : null);
                    m.put("endDate", o.getEndDate() != null ? o.getEndDate().toString() : null);
                    if (o.getResource() != null) {
                        m.put("resourceId", o.getResource().getId());
                        m.put("resourceName", o.getResource().getName());
                    }
                    return m;
                })
                .toList();
    }

    @Tool(description = "Get the in-memory check audit log showing the last 200 checks across all resources. "
            + "Each entry includes timestamp, kind (Scheduled / Check Now / Instant Check), "
            + "resource name, target, who triggered it, and the result (AVAILABLE/NOT_AVAILABLE/UNKNOWN).")
    public List<Map<String, Object>> getCheckAuditLog() {
        return checkAuditService.getEntries().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("timestamp", e.timestamp() != null ? e.timestamp().toString() : null);
                    m.put("kind", e.kind());
                    m.put("resourceName", e.resourceName());
                    m.put("target", e.target());
                    m.put("triggeredBy", e.triggeredBy());
                    m.put("result", e.result());
                    return m;
                })
                .toList();
    }

    @Tool(description = "Run an ad-hoc instant check against a URL, Docker image reference, or TCP host:port without "
            + "adding it to the monitored resources. Returns status (AVAILABLE/NOT_AVAILABLE) and message. "
            + "Instant checks must be enabled in Admin → General Settings.")
    public Map<String, Object> runInstantCheck(
            @ToolParam(description = "The URL (for HTTP), Docker image reference (for DOCKER), or host:port (for TCP) to check") String target,
            @ToolParam(description = "Resource type to use: HTTP, DOCKER, or TCP") String resourceType) {
        ResourceType type;
        try {
            type = ResourceType.valueOf(resourceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid resourceType '" + resourceType + "'. Must be HTTP, DOCKER, or TCP.");
        }
        try {
            tech.wenisch.kairos.dto.InstantCheckExecutionResult result =
                    instantCheckService.runInstantCheck(type, target, false, false);
            String statusName = result.status() != null ? result.status().name() : "UNKNOWN";
            checkAuditService.record("Instant Check", null, target, "MCP", statusName);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("target", target);
            m.put("status", statusName);
            m.put("message", result.message());
            m.put("errorCode", result.errorCode());
            m.put("latencyMs", result.latencyMs());
            return m;
        } catch (Exception e) {
            log.warn("MCP instant check failed for target {}: {}", target, e.getMessage());
            return Map.of("error", e.getMessage(), "target", target, "status", "ERROR");
        }
    }

    @Tool(description = "Add a new monitored resource. "
            + "resourceType must be HTTP (for URLs), DOCKER (for container image references), or TCP (for host:port endpoints). "
            + "Returns the created resource including its assigned numeric ID.")
    @Transactional
    public Map<String, Object> createResource(
            @ToolParam(description = "Display name for the resource") String name,
            @ToolParam(description = "Resource type: HTTP, DOCKER, or TCP") String resourceType,
            @ToolParam(description = "Target URL (HTTP), Docker image reference (DOCKER), or host:port (TCP)") String target,
            @ToolParam(description = "Skip TLS certificate verification: true or false") boolean skipTls) {
        ResourceType type;
        try {
            type = ResourceType.valueOf(resourceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid resourceType '" + resourceType + "'. Must be HTTP, DOCKER, or TCP.");
        }
        MonitoredResource resource = MonitoredResource.builder()
                .name(name)
                .resourceType(type)
                .target(target)
                .skipTls(skipTls)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
        MonitoredResource saved = resourceService.save(resource);
        checkExecutorService.runImmediateCheck(saved.getId(), "MCP");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", saved.getId());
        m.put("name", saved.getName());
        m.put("type", saved.getResourceType().name());
        m.put("target", saved.getTarget());
        m.put("active", saved.isActive());
        m.put("createdAt", saved.getCreatedAt().toString());
        m.put("message", "Resource created and initial check submitted.");
        return m;
    }

    @Tool(description = "Permanently delete a monitored resource and all its check history by ID. "
            + "This action cannot be undone.")
    @Transactional
    public Map<String, Object> deleteResource(
            @ToolParam(description = "The numeric ID of the resource to delete") Long id) {
        if (resourceService.findById(id).isEmpty()) {
            return Map.of("error", "Resource not found with id: " + id);
        }
        resourceService.delete(id);
        return Map.of("status", "deleted", "resourceId", id);
    }

    @Tool(description = "Create a new announcement that is displayed to users on the status page. "
            + "kind must be INFORMATION, WARNING, or PROBLEM. "
            + "content is an HTML string. "
            + "activeUntil is an optional ISO-8601 datetime string (e.g. 2026-06-01T08:00:00) after which the announcement auto-deactivates; pass null to keep it active indefinitely.")
    @Transactional
    public Map<String, Object> createAnnouncement(
            @ToolParam(description = "Announcement kind: INFORMATION, WARNING, or PROBLEM") String kind,
            @ToolParam(description = "HTML content of the announcement") String content,
            @ToolParam(description = "Optional ISO-8601 expiry datetime, or null for no expiry") String activeUntil) {
        AnnouncementKind announcementKind;
        try {
            announcementKind = AnnouncementKind.valueOf(kind.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Map.of("error", "Invalid kind '" + kind + "'. Must be INFORMATION, WARNING, or PROBLEM.");
        }
        LocalDateTime until = null;
        if (activeUntil != null && !activeUntil.isBlank() && !activeUntil.equalsIgnoreCase("null")) {
            try {
                until = LocalDateTime.parse(activeUntil, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                return Map.of("error", "Invalid activeUntil format. Use ISO-8601 datetime, e.g. 2026-06-01T08:00:00");
            }
        }
        Announcement announcement = Announcement.builder()
                .kind(announcementKind)
                .content(content)
                .active(true)
                .activeUntil(until)
                .createdBy("MCP")
                .build();
        Announcement saved = announcementService.save(announcement);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", saved.getId());
        m.put("kind", saved.getKind().name());
        m.put("content", saved.getContent());
        m.put("active", saved.isActive());
        m.put("activeUntil", saved.getActiveUntil() != null ? saved.getActiveUntil().toString() : null);
        m.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().toString() : null);
        return m;
    }

    @Tool(description = "Permanently delete an announcement by its numeric ID.")
    @Transactional
    public Map<String, Object> deleteAnnouncement(
            @ToolParam(description = "The numeric ID of the announcement to delete") Long id) {
        if (announcementService.findById(id).isEmpty()) {
            return Map.of("error", "Announcement not found with id: " + id);
        }
        announcementService.delete(id);
        return Map.of("status", "deleted", "announcementId", id);
    }
}

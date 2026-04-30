package tech.wenisch.kairos.controller;

import tech.wenisch.kairos.dto.AnnouncementDTO;
import tech.wenisch.kairos.dto.GroupSummaryDTO;
import tech.wenisch.kairos.dto.LatencySampleDTO;
import tech.wenisch.kairos.dto.OutageDTO;
import tech.wenisch.kairos.dto.ResourceDTO;
import tech.wenisch.kairos.dto.ResourceDetailsDTO;
import tech.wenisch.kairos.dto.ResourceStatusUpdateDTO;
import tech.wenisch.kairos.entity.Announcement;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceGroupVisibility;
import tech.wenisch.kairos.service.AnnouncementService;
import tech.wenisch.kairos.service.CheckExecutorService;
import tech.wenisch.kairos.service.OutageService;
import tech.wenisch.kairos.service.ResourceGroupService;
import tech.wenisch.kairos.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tech.wenisch.kairos.service.ResourceStatusStreamService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller exposing the Kairos public and management API under the {@code /api} base path.
 *
 * <p>Endpoints that perform mutations (create, delete) require the caller to hold the
 * {@code ADMIN} role. Read-only resource endpoints are publicly accessible without
 * authentication.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final ResourceService resourceService;
    private final ResourceGroupService resourceGroupService;
    private final CheckExecutorService checkExecutorService;
        private final OutageService outageService;
    private final AnnouncementService announcementService;
    private final ResourceStatusStreamService resourceStatusStreamService;

            @Operation(summary = "List outages",
                description = "Returns outages ordered by newest start date. By default returns all outages visible to the caller. Use active=true to only return active outages.",
                tags = "Outages")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful - list of outages (may be empty)")
        })
        @GetMapping("/outages")
        public ResponseEntity<List<OutageDTO>> listOutages(
            @RequestParam(name = "active", required = false) Boolean active,
            Authentication authentication) {
        boolean authenticated = isAuthenticated(authentication);

        List<OutageDTO> outages = outageService.findAllForApi().stream()
            .filter(outage -> outage.getResource() != null)
            .filter(outage -> isVisibleByGroupPolicy(outage.getResource(), authenticated))
            .filter(outage -> active == null || Boolean.valueOf(outage.isActive()).equals(active))
            .map(this::toOutageDto)
            .toList();

        return ResponseEntity.ok(outages);
        }

            @Operation(summary = "List outages for a resource",
                description = "Returns outages for a single resource ordered by newest start date.",
                    tags = "Resources")
        @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successful - list of outages (may be empty)"),
            @ApiResponse(responseCode = "404", description = "No resource with the given ID exists", content = @Content)
        })
        @GetMapping("/resources/{id}/outages")
        public ResponseEntity<List<OutageDTO>> listOutagesForResource(
            @PathVariable Long id,
            @RequestParam(name = "active", required = false) Boolean active,
            Authentication authentication) {
        boolean authenticated = isAuthenticated(authentication);
        Optional<MonitoredResource> resourceOpt = resourceService.findById(id)
            .filter(resource -> isVisibleByGroupPolicy(resource, authenticated));
        if (resourceOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<OutageDTO> outages = outageService.findByResourceForApi(resourceOpt.get()).stream()
            .filter(outage -> active == null || Boolean.valueOf(outage.isActive()).equals(active))
            .map(this::toOutageDto)
            .toList();

        return ResponseEntity.ok(outages);
        }

    /**
     * Returns all currently active monitored resources.
     *
     * <p>This endpoint is public and does not require authentication.
     *
     * @return list of active {@link MonitoredResource} objects
     */
    @Operation(summary = "List active resources",
               description = "Returns all monitored resources that are currently marked as active. No authentication required.",
               tags = "Resources")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful – list of active resources (may be empty)")
    })
    @GetMapping("/resources")
    public ResponseEntity<List<MonitoredResource>> listResources(Authentication authentication) {
        boolean authenticated = isAuthenticated(authentication);
        List<MonitoredResource> visibleResources = resourceService.findAllActive().stream()
                .filter(resource -> isVisibleByGroupPolicy(resource, authenticated))
                .toList();
        return ResponseEntity.ok(visibleResources);
    }

        @Operation(summary = "Stream resource updates",
            description = "Server-sent event stream for live resource status changes.",
            tags = "Resources")
        @GetMapping(value = "/resources/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> streamResourceUpdates() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore().mustRevalidate());
        headers.add(HttpHeaders.PRAGMA, "no-cache");
        headers.add("X-Accel-Buffering", "no");
        return ResponseEntity.ok().headers(headers).body(resourceStatusStreamService.subscribe());
    }

        @Operation(summary = "Get status update snapshot",
            description = "Returns current status snapshots for visible resources.",
            tags = "Resources")
        @GetMapping("/resources/status-updates")
    public ResponseEntity<List<ResourceStatusUpdateDTO>> getResourceStatusUpdates(
            @RequestParam(name = "hours", defaultValue = "24") int hours) {
        int normalizedHours = normalizeTimelineHours(hours);
        return ResponseEntity.ok(resourceStatusStreamService.getSnapshot(normalizedHours));
    }

        @Operation(summary = "Get resource status update",
            description = "Returns one status snapshot for the selected resource.",
            tags = "Resources")
        @GetMapping("/resources/{id}/status-update")
    public ResponseEntity<ResourceStatusUpdateDTO> getResourceStatusUpdateByResourceId(
            @PathVariable Long id,
            @RequestParam(name = "hours", defaultValue = "24") int hours,
            Authentication authentication) {
        int normalizedHours = normalizeTimelineHours(hours);
        boolean authenticated = isAuthenticated(authentication);
        if (resourceService.findById(id).filter(resource -> isVisibleByGroupPolicy(resource, authenticated)).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return resourceStatusStreamService.getSnapshotForResource(id, normalizedHours)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

        @Operation(summary = "Get resource latency samples",
            description = "Returns latency samples for a resource in the selected time range.",
            tags = "Resources")
        @GetMapping("/resources/{id}/latency-samples")
    public ResponseEntity<List<LatencySampleDTO>> getResourceLatencySamples(
            @PathVariable Long id,
            @RequestParam(name = "hours", defaultValue = "24") int hours,
            Authentication authentication) {
        int normalizedHours = normalizeTimelineHours(hours);
        boolean authenticated = isAuthenticated(authentication);
        return resourceService.findById(id)
            .filter(resource -> isVisibleByGroupPolicy(resource, authenticated))
                .map(resource -> ResponseEntity.ok(resourceService.getLatencySamples(resource, normalizedHours)))
                .orElse(ResponseEntity.notFound().build());
    }

    private int normalizeTimelineHours(int hours) {
        return switch (hours) {
            case 24, 168, 720 -> hours;
            default -> 24;
        };
    }

    /**
     * Returns detailed information about a single monitored resource including its
     * latest health-check result.
     *
     * <p>This endpoint is public and does not require authentication.
     *
     * @param id the unique identifier of the monitored resource
     * @return a {@link ResourceDetailsDTO} with general resource data and the most
     *         recent check status, or {@code 404} when no resource with the given id exists
     */
    @Operation(summary = "Get resource by ID",
               description = "Returns general information about a monitored resource together with its latest health-check result. No authentication required.",
               tags = "Resources")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resource found",
                     content = @Content(schema = @Schema(implementation = ResourceDetailsDTO.class))),
        @ApiResponse(responseCode = "404", description = "No resource with the given ID exists", content = @Content)
    })
    @GetMapping("/resources/{id}")
    public ResponseEntity<ResourceDetailsDTO> getResourceById(
            @Parameter(description = "Unique ID of the monitored resource", required = true, example = "1")
            @PathVariable Long id,
            Authentication authentication) {
        boolean authenticated = isAuthenticated(authentication);
        return resourceService.findById(id)
                .filter(resource -> isVisibleByGroupPolicy(resource, authenticated))
                .map(resource -> {
                    Optional<CheckResult> latestCheckResult = resourceService.getLatestCheckResult(resource);
                    List<GroupSummaryDTO> groupDtos = resource.getGroups().stream()
                            .sorted(java.util.Comparator.comparing(tech.wenisch.kairos.entity.ResourceGroup::getId))
                            .map(g -> new GroupSummaryDTO(g.getId(), g.getName()))
                            .toList();
                    Long firstGroupId = groupDtos.isEmpty() ? null : groupDtos.get(0).id();
                    String firstGroupName = groupDtos.isEmpty() ? null : groupDtos.get(0).name();
                    ResourceDetailsDTO response = new ResourceDetailsDTO(
                            resource.getId(),
                            resource.getName(),
                            resource.getResourceType(),
                            resource.getTarget(),
                            firstGroupId,
                            firstGroupName,
                            groupDtos,
                            resource.getDisplayOrder(),
                            resource.isSkipTls(),
                            resource.isRecursive(),
                            resource.isActive(),
                            resource.getCreatedAt(),
                            resourceService.getCurrentStatus(resource),
                            latestCheckResult.map(CheckResult::getStatus).orElse(null),
                            latestCheckResult.map(CheckResult::getCheckedAt).orElse(null),
                            latestCheckResult.map(CheckResult::getMessage).orElse(null),
                            latestCheckResult.map(CheckResult::getErrorCode).orElse(null)
                    );
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean isVisibleByGroupPolicy(MonitoredResource resource, boolean authenticated) {
        if (resource.getGroups().isEmpty()) {
            return true;
        }
        // Most-permissive rule: take the least-restrictive visibility across all groups.
        ResourceGroupVisibility effective = resource.getGroups().stream()
                .map(ResourceGroup::getVisibilityOrDefault)
                .min(java.util.Comparator.comparingInt(ResourceGroupVisibility::ordinal))
                .orElse(ResourceGroupVisibility.PUBLIC);
        return switch (effective) {
            case PUBLIC -> true;
            case AUTHENTICATED -> authenticated;
            case HIDDEN -> false;
        };
    }

    private OutageDTO toOutageDto(Outage outage) {
        MonitoredResource resource = outage.getResource();
        java.time.LocalDateTime endDate = outage.getEndDate() != null ? outage.getEndDate() : java.time.LocalDateTime.now();
        long durationMinutes = Math.max(0L, java.time.Duration.between(outage.getStartDate(), endDate).toMinutes());

        return new OutageDTO(
                outage.getId(),
                resource != null ? resource.getId() : null,
                resource != null ? resource.getName() : null,
                resource != null ? resource.getResourceType() : null,
                outage.getStartDate(),
                outage.getEndDate(),
                outage.isActive(),
                durationMinutes
        );
    }

    /**
     * Creates a new monitored resource.
     *
     * <p>The resource is immediately activated. Health checks will start on the next
     * scheduler cycle. Requires {@code ADMIN} role.
     *
     * @param dto the resource definition containing name, type and target URL/host
     * @return the persisted {@link MonitoredResource} including its generated id
     */
    @Operation(summary = "Create a resource",
               description = "Adds a new monitored resource and activates it immediately. Requires ADMIN role.",
               tags = "Resources",
               security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resource created successfully",
                     content = @Content(schema = @Schema(implementation = MonitoredResource.class))),
        @ApiResponse(responseCode = "403", description = "Caller does not hold the ADMIN role", content = @Content)
    })
    @PostMapping("/resources")
    public ResponseEntity<MonitoredResource> addResource(@RequestBody ResourceDTO dto) {
        ResourceGroup group = null;
        if (dto.getGroupId() != null) {
            group = resourceGroupService.findById(dto.getGroupId()).orElse(null);
        }

        MonitoredResource resource = MonitoredResource.builder()
                .name(dto.getName())
                .resourceType(dto.getResourceType())
                .target(dto.getTarget())
                .skipTls(dto.isSkipTls())
                .recursive(dto.isRecursive())
            .displayOrder(java.util.Optional.ofNullable(dto.getDisplayOrder()).orElse(0))
                .active(true)
                .build();
        if (group != null) {
            resource.getGroups().add(group);
        }
        MonitoredResource saved = resourceService.save(resource);
        return ResponseEntity.ok(saved);
    }

    /**
     * Creates template/sample resources for testing purposes.
     *
     * <p>Creates a set of diverse sample resources (HTTP endpoints, Docker images)
     * organized into groups to demonstrate Kairos functionality. Useful for testing
     * and demos. Requires {@code ADMIN} role.
     *
     * @return a JSON map with status and count of created resources
     */
    @Operation(summary = "Create template resources",
               description = "Creates a set of sample resources for testing (HTTP services, Docker images, groups). Requires ADMIN role.",
               tags = "Resources",
               security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Template resources created successfully"),
        @ApiResponse(responseCode = "403", description = "Caller does not hold the ADMIN role", content = @Content)
    })
    @PostMapping("/resources/templates")
    public ResponseEntity<Map<String, Object>> createTemplateResources() {
        // Create groups
        ResourceGroup webServicesGroup = resourceGroupService.save(
            ResourceGroup.builder().name("Web Services").displayOrder(1).build()
        );
        ResourceGroup dockerServicesGroup = resourceGroupService.save(
            ResourceGroup.builder().name("Docker Images").displayOrder(2).build()
        );

        // Create HTTP resources
        MonitoredResource httpGoogle = MonitoredResource.builder()
                .name("Google DNS")
                .resourceType(tech.wenisch.kairos.entity.ResourceType.HTTP)
                .target("https://dns.google")
                .skipTls(false)
                .displayOrder(1)
                .active(true)
                .build();
        httpGoogle.getGroups().add(webServicesGroup);
        resourceService.save(httpGoogle);

        MonitoredResource httpGithub = MonitoredResource.builder()
                .name("GitHub Status")
                .resourceType(tech.wenisch.kairos.entity.ResourceType.HTTP)
                .target("https://status.github.com")
                .skipTls(false)
                .displayOrder(2)
                .active(true)
                .build();
        httpGithub.getGroups().add(webServicesGroup);
        resourceService.save(httpGithub);

        MonitoredResource httpExample = MonitoredResource.builder()
                .name("Example.com")
                .resourceType(tech.wenisch.kairos.entity.ResourceType.HTTP)
                .target("https://example.com")
                .skipTls(false)
                .displayOrder(3)
                .active(true)
                .build();
        httpExample.getGroups().add(webServicesGroup);
        resourceService.save(httpExample);

        // Create Docker image resources
        MonitoredResource dockerNginx = MonitoredResource.builder()
                .name("Nginx Latest")
                .resourceType(tech.wenisch.kairos.entity.ResourceType.DOCKER)
                .target("docker.io/library/nginx:latest")
                .skipTls(false)
                .displayOrder(1)
                .active(true)
                .build();
        dockerNginx.getGroups().add(dockerServicesGroup);
        resourceService.save(dockerNginx);

        MonitoredResource dockerPostgres = MonitoredResource.builder()
                .name("PostgreSQL 15")
                .resourceType(tech.wenisch.kairos.entity.ResourceType.DOCKER)
                .target("docker.io/library/postgres:15-alpine")
                .skipTls(false)
                .displayOrder(2)
                .active(true)
                .build();
        dockerPostgres.getGroups().add(dockerServicesGroup);
        resourceService.save(dockerPostgres);

        MonitoredResource dockerRedis = MonitoredResource.builder()
                .name("Redis Latest")
                .resourceType(tech.wenisch.kairos.entity.ResourceType.DOCKER)
                .target("docker.io/library/redis:latest")
                .skipTls(false)
                .displayOrder(3)
                .active(true)
                .build();
        dockerRedis.getGroups().add(dockerServicesGroup);
        resourceService.save(dockerRedis);

        // Trigger immediate checks
        checkExecutorService.runImmediateCheck(dockerNginx);
        checkExecutorService.runImmediateCheck(dockerPostgres);
        checkExecutorService.runImmediateCheck(dockerRedis);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "message", "Template resources created",
            "groupsCreated", 2,
            "resourcesCreated", 6
        ));
    }

    /**
     * Permanently deletes a monitored resource and all associated check history.
     *
     * <p>Requires {@code ADMIN} role.
     *
     * @param id the unique identifier of the resource to remove
     * @return a JSON object {@code {"status":"deleted"}}
     */
    @Operation(summary = "Delete a resource",
               description = "Permanently removes a monitored resource and its entire check history. Requires ADMIN role.",
               tags = "Resources",
               security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resource deleted"),
        @ApiResponse(responseCode = "403", description = "Caller does not hold the ADMIN role", content = @Content)
    })
    @DeleteMapping("/resources/{id}")
    public ResponseEntity<Map<String, String>> deleteResource(
            @Parameter(description = "Unique ID of the resource to delete", required = true, example = "1")
            @PathVariable Long id) {
        resourceService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    /**
     * Returns the full check-result history for a monitored resource, ordered from
     * newest to oldest.
     *
     * <p>Requires an authenticated session (any role).
     *
     * @param id the unique identifier of the monitored resource
     * @return ordered list of {@link CheckResult} records, or {@code 404} when
     *         no resource with the given id exists
     */
    @Operation(summary = "Get check history",
               description = "Returns every historical health-check result for the specified resource, newest first. Requires authentication.",
               tags = "Resources",
               security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "History returned (may be empty)"),
        @ApiResponse(responseCode = "401", description = "Not authenticated", content = @Content),
        @ApiResponse(responseCode = "404", description = "No resource with the given ID exists", content = @Content)
    })
    @GetMapping("/resources/{id}/history")
    public ResponseEntity<List<CheckResult>> getHistory(
            @Parameter(description = "Unique ID of the monitored resource", required = true, example = "1")
            @PathVariable Long id) {
        return resourceService.findById(id)
                .map(r -> ResponseEntity.ok(resourceService.getFullHistory(id)))
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // Announcements
    // -------------------------------------------------------------------------

    /**
     * Returns all announcements ordered by creation date descending.
     *
     * <p>This endpoint is public and does not require authentication.
     *
     * @return list of all announcements (active and inactive)
     */
    @Operation(summary = "List all announcements",
               description = "Returns every announcement ordered newest first. No authentication required.",
               tags = "Announcements")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful – list of announcements (may be empty)")
    })
    @GetMapping("/announcements")
    public ResponseEntity<List<Announcement>> listAnnouncements() {
        return ResponseEntity.ok(announcementService.findAllOrderedByCreatedAtDesc());
    }

    /**
     * Returns a single announcement by its ID.
     *
     * <p>This endpoint is public and does not require authentication.
     *
     * @param id the unique identifier of the announcement
     * @return the {@link Announcement}, or {@code 404} if not found
     */
    @Operation(summary = "Get announcement by ID",
               description = "Returns a single announcement by its ID. No authentication required.",
               tags = "Announcements")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Announcement found",
                     content = @Content(schema = @Schema(implementation = Announcement.class))),
        @ApiResponse(responseCode = "404", description = "No announcement with the given ID exists", content = @Content)
    })
    @GetMapping("/announcements/{id}")
    public ResponseEntity<Announcement> getAnnouncementById(
            @Parameter(description = "Unique ID of the announcement", required = true, example = "1")
            @PathVariable Long id) {
        return announcementService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Creates a new announcement.
     *
     * <p>The {@code createdBy} field is automatically set to the authenticated user's name.
     * Requires {@code ADMIN} role.
     *
     * @param dto            the announcement payload
     * @param authentication the current security principal
     * @return the persisted {@link Announcement} including its generated id
     */
    @Operation(summary = "Create an announcement",
               description = "Creates a new announcement. createdBy is set from the authenticated user. Requires ADMIN role.",
               tags = "Announcements",
               security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Announcement created",
                     content = @Content(schema = @Schema(implementation = Announcement.class))),
        @ApiResponse(responseCode = "403", description = "Caller does not hold the ADMIN role", content = @Content)
    })
    @PostMapping("/announcements")
    public ResponseEntity<Announcement> createAnnouncement(
            @RequestBody AnnouncementDTO dto,
            Authentication authentication) {
        Announcement announcement = Announcement.builder()
                .kind(dto.kind())
                .content(dto.content())
                .active(dto.active())
                .activeUntil(dto.activeUntil())
                .createdBy(authentication != null ? authentication.getName() : "api")
                .build();
        return ResponseEntity.ok(announcementService.save(announcement));
    }

    /**
     * Updates an existing announcement.
     *
     * <p>All fields supplied in the request body overwrite the stored values.
     * {@code createdBy} and {@code createdAt} are preserved from the original record.
     * Requires {@code ADMIN} role.
     *
     * @param id  the unique identifier of the announcement to update
     * @param dto the updated announcement payload
     * @return the updated {@link Announcement}, or {@code 404} if not found
     */
    @Operation(summary = "Update an announcement",
               description = "Fully replaces a stored announcement's fields. createdBy and createdAt are preserved. Requires ADMIN role.",
               tags = "Announcements",
               security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Announcement updated",
                     content = @Content(schema = @Schema(implementation = Announcement.class))),
        @ApiResponse(responseCode = "403", description = "Caller does not hold the ADMIN role", content = @Content),
        @ApiResponse(responseCode = "404", description = "No announcement with the given ID exists", content = @Content)
    })
    @PutMapping("/announcements/{id}")
    public ResponseEntity<Announcement> updateAnnouncement(
            @Parameter(description = "Unique ID of the announcement to update", required = true, example = "1")
            @PathVariable Long id,
            @RequestBody AnnouncementDTO dto) {
        return announcementService.findById(id)
                .map(existing -> {
                    existing.setKind(dto.kind());
                    existing.setContent(dto.content());
                    existing.setActive(dto.active());
                    existing.setActiveUntil(dto.activeUntil());
                    return ResponseEntity.ok(announcementService.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Permanently deletes an announcement.
     *
     * <p>Requires {@code ADMIN} role.
     *
     * @param id the unique identifier of the announcement to delete
     * @return a JSON object {@code {"status":"deleted"}}
     */
    @Operation(summary = "Delete an announcement",
               description = "Permanently removes an announcement. Requires ADMIN role.",
               tags = "Announcements",
               security = {@SecurityRequirement(name = "cookieAuth"), @SecurityRequirement(name = "apiKeyAuth")})
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Announcement deleted"),
        @ApiResponse(responseCode = "403", description = "Caller does not hold the ADMIN role", content = @Content)
    })
    @DeleteMapping("/announcements/{id}")
    public ResponseEntity<Map<String, String>> deleteAnnouncement(
            @Parameter(description = "Unique ID of the announcement to delete", required = true, example = "1")
            @PathVariable Long id) {
        announcementService.delete(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}

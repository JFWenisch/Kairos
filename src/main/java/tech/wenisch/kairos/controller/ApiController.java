package tech.wenisch.kairos.controller;

import tech.wenisch.kairos.dto.ResourceDTO;
import tech.wenisch.kairos.dto.ResourceDetailsDTO;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
@Tag(name = "Resources", description = "Manage and query monitored resources and their health history")
public class ApiController {

    private final ResourceService resourceService;

    /**
     * Returns all currently active monitored resources.
     *
     * <p>This endpoint is public and does not require authentication.
     *
     * @return list of active {@link MonitoredResource} objects
     */
    @Operation(summary = "List active resources",
               description = "Returns all monitored resources that are currently marked as active. No authentication required.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful – list of active resources (may be empty)")
    })
    @GetMapping("/resources")
    public ResponseEntity<List<MonitoredResource>> listResources() {
        return ResponseEntity.ok(resourceService.findAllActive());
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
               description = "Returns general information about a monitored resource together with its latest health-check result. No authentication required.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resource found",
                     content = @Content(schema = @Schema(implementation = ResourceDetailsDTO.class))),
        @ApiResponse(responseCode = "404", description = "No resource with the given ID exists", content = @Content)
    })
    @GetMapping("/resources/{id}")
    public ResponseEntity<ResourceDetailsDTO> getResourceById(
            @Parameter(description = "Unique ID of the monitored resource", required = true, example = "1")
            @PathVariable Long id) {
        return resourceService.findById(id)
                .map(resource -> {
                    Optional<CheckResult> latestCheckResult = resourceService.getLatestCheckResult(resource);
                    ResourceDetailsDTO response = new ResourceDetailsDTO(
                            resource.getId(),
                            resource.getName(),
                            resource.getResourceType(),
                            resource.getTarget(),
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
               security = @SecurityRequirement(name = "cookieAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Resource created successfully",
                     content = @Content(schema = @Schema(implementation = MonitoredResource.class))),
        @ApiResponse(responseCode = "403", description = "Caller does not hold the ADMIN role", content = @Content)
    })
    @PostMapping("/resources")
    public ResponseEntity<MonitoredResource> addResource(@RequestBody ResourceDTO dto) {
        MonitoredResource resource = MonitoredResource.builder()
                .name(dto.getName())
                .resourceType(dto.getResourceType())
                .target(dto.getTarget())
                .active(true)
                .build();
        return ResponseEntity.ok(resourceService.save(resource));
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
               security = @SecurityRequirement(name = "cookieAuth"))
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
               security = @SecurityRequirement(name = "cookieAuth"))
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
}

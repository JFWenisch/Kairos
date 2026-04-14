package tech.wenisch.kairos.controller;

import tech.wenisch.kairos.dto.AdminResourceGroupViewModel;
import tech.wenisch.kairos.entity.*;
import tech.wenisch.kairos.repository.CorsAllowedOriginRepository;
import tech.wenisch.kairos.repository.ResourceTypeAuthRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;
import tech.wenisch.kairos.service.AnnouncementService;
import tech.wenisch.kairos.service.ApiKeyService;
import tech.wenisch.kairos.service.CheckExecutorService;
import tech.wenisch.kairos.service.ResourceExchangeService;
import tech.wenisch.kairos.service.ResourceGroupService;
import tech.wenisch.kairos.service.ResourceService;
import tech.wenisch.kairos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ResourceService resourceService;
    private final UserService userService;
    private final AnnouncementService announcementService;
    private final ApiKeyService apiKeyService;
    private final CheckExecutorService checkExecutorService;
    private final ResourceExchangeService resourceExchangeService;
    private final ResourceGroupService resourceGroupService;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;
    private final ResourceTypeAuthRepository resourceTypeAuthRepository;
    private final CorsAllowedOriginRepository corsAllowedOriginRepository;

    @GetMapping
    public String admin() {
        return "redirect:/admin/settings";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        boolean allowPublicAccess = configs.stream().allMatch(ResourceTypeConfig::isAllowPublicAccess);
        boolean allowPublicAdd = configs.stream().anyMatch(ResourceTypeConfig::isAllowPublicAdd);
        boolean allowPublicCheckNow = configs.stream().anyMatch(ResourceTypeConfig::isAllowPublicCheckNow);
        boolean alwaysDisplayUrl = configs.stream().anyMatch(ResourceTypeConfig::isAlwaysDisplayUrl);
        boolean checkHistoryRetentionEnabled = configs.stream().anyMatch(ResourceTypeConfig::isCheckHistoryRetentionEnabled);
        int checkHistoryRetentionIntervalMinutes = configs.stream()
                .map(ResourceTypeConfig::getCheckHistoryRetentionIntervalMinutes)
                .findFirst()
                .orElse(60);
        int checkHistoryRetentionDays = configs.stream()
                .map(ResourceTypeConfig::getCheckHistoryRetentionDays)
                .findFirst()
                .orElse(31);
        boolean deleteOutagesOnResourceDelete = configs.stream().anyMatch(ResourceTypeConfig::isDeleteOutagesOnResourceDelete);
        model.addAttribute("allowPublicAccess", allowPublicAccess);
        model.addAttribute("allowPublicAdd", allowPublicAdd);
        model.addAttribute("allowPublicCheckNow", allowPublicCheckNow);
        model.addAttribute("alwaysDisplayUrl", alwaysDisplayUrl);
        model.addAttribute("checkHistoryRetentionEnabled", checkHistoryRetentionEnabled);
        model.addAttribute("checkHistoryRetentionIntervalMinutes", checkHistoryRetentionIntervalMinutes);
        model.addAttribute("checkHistoryRetentionDays", checkHistoryRetentionDays);
        model.addAttribute("deleteOutagesOnResourceDelete", deleteOutagesOnResourceDelete);
        model.addAttribute("corsAllowedOrigins", corsAllowedOriginRepository.findAll());
        model.addAttribute("configs", configs);
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam(defaultValue = "false") boolean allowPublicAccess,
                               @RequestParam(defaultValue = "false") boolean allowPublicAdd,
                               @RequestParam(defaultValue = "false") boolean allowPublicCheckNow,
                               @RequestParam(defaultValue = "false") boolean alwaysDisplayUrl,
                               @RequestParam(defaultValue = "false") boolean checkHistoryRetentionEnabled,
                               @RequestParam(defaultValue = "60") int checkHistoryRetentionIntervalMinutes,
                               @RequestParam(defaultValue = "31") int checkHistoryRetentionDays,
                               @RequestParam(defaultValue = "false") boolean deleteOutagesOnResourceDelete,
                               RedirectAttributes redirectAttributes) {
        int sanitizedRetentionIntervalMinutes = Math.max(1, checkHistoryRetentionIntervalMinutes);
        int sanitizedRetentionDays = Math.max(1, checkHistoryRetentionDays);
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        for (ResourceTypeConfig config : configs) {
            config.setAllowPublicAccess(allowPublicAccess);
            config.setAllowPublicAdd(allowPublicAdd);
            config.setAllowPublicCheckNow(allowPublicCheckNow);
            config.setAlwaysDisplayUrl(alwaysDisplayUrl);
            config.setCheckHistoryRetentionEnabled(checkHistoryRetentionEnabled);
            config.setCheckHistoryRetentionIntervalMinutes(sanitizedRetentionIntervalMinutes);
            config.setCheckHistoryRetentionDays(sanitizedRetentionDays);
            config.setDeleteOutagesOnResourceDelete(deleteOutagesOnResourceDelete);
            resourceTypeConfigRepository.save(config);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully");
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/cors/add")
    public String addCorsOrigin(@RequestParam String origin, RedirectAttributes redirectAttributes) {
        origin = origin.trim();
        if (origin.isBlank() || (!origin.startsWith("http://") && !origin.startsWith("https://"))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Invalid origin. Use format: https://example.com");
            return "redirect:/admin/settings";
        }
        if (corsAllowedOriginRepository.existsByOrigin(origin)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Origin already exists: " + origin);
        } else {
            corsAllowedOriginRepository.save(CorsAllowedOrigin.builder().origin(origin).build());
            redirectAttributes.addFlashAttribute("successMessage", "CORS origin added: " + origin);
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/settings/cors/remove/{id}")
    public String removeCorsOrigin(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        corsAllowedOriginRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("successMessage", "CORS origin removed.");
        return "redirect:/admin/settings";
    }

    @GetMapping("/resources")
    public String resources(Model model) {
        List<MonitoredResource> resources = resourceService.findAll();
        List<ResourceGroup> groups = resourceGroupService.findAllOrdered();

        model.addAttribute("resources", resources);
        model.addAttribute("resourceGroups", resourceGroupService.findAllOrdered());
        model.addAttribute("adminResourceGroups", buildAdminResourceGroups(resources, groups));
        model.addAttribute("resourceTypes", ResourceType.values());
        return "admin/resources";
    }

    @PostMapping("/resources/reorder")
    public String reorderResources(@RequestParam(required = false) Long groupId,
                                   @RequestParam String orderedResourceIds,
                                   RedirectAttributes redirectAttributes) {
        if (orderedResourceIds == null || orderedResourceIds.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "No resource order provided.");
            return "redirect:/admin/resources";
        }

        Map<Long, MonitoredResource> resourcesById = resourceService.findAll().stream()
                .collect(Collectors.toMap(MonitoredResource::getId, r -> r));

        ResourceGroup targetGroup = resolveGroup(groupId);
        int updated = 0;
        int reassigned = 0;
        int nextOrder = 0;
        String[] ids = orderedResourceIds.split(",");

        for (String rawId : ids) {
            String trimmed = rawId.trim();
            if (trimmed.isBlank()) {
                continue;
            }

            Long resourceId;
            try {
                resourceId = Long.parseLong(trimmed);
            } catch (NumberFormatException ex) {
                continue;
            }

            MonitoredResource resource = resourcesById.get(resourceId);
            if (resource == null) {
                continue;
            }

            Long resourceGroupId = resource.getGroup() != null ? resource.getGroup().getId() : null;
            if (!Objects.equals(resourceGroupId, groupId)) {
                resource.setGroup(targetGroup);
                reassigned++;
            }

            resource.setDisplayOrder(nextOrder);
            resourceService.save(resource);
            nextOrder += 10;
            updated++;
        }

        redirectAttributes.addFlashAttribute(
                "successMessage",
                "Updated order for " + updated + " resources"
                        + (reassigned > 0 ? " and reassigned " + reassigned + "." : ".")
        );
        return "redirect:/admin/resources";
    }

    @PostMapping("/resource-groups/add")
    public String addResourceGroup(@RequestParam String name,
                                   @RequestParam(defaultValue = "0") int displayOrder,
                                   RedirectAttributes redirectAttributes) {
        if (name == null || name.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Group name is required.");
            return "redirect:/admin/resources";
        }

        ResourceGroup group = ResourceGroup.builder()
                .name(name.trim())
                .displayOrder(displayOrder)
                .build();
        resourceGroupService.save(group);
        redirectAttributes.addFlashAttribute("successMessage", "Group added: " + group.getName());
        return "redirect:/admin/resources";
    }

    @PostMapping("/resource-groups/update/{id}")
    public String updateResourceGroup(@PathVariable Long id,
                                      @RequestParam String name,
                                      @RequestParam(defaultValue = "0") int displayOrder,
                                      RedirectAttributes redirectAttributes) {
        resourceGroupService.findById(id).ifPresent(group -> {
            group.setName(name);
            group.setDisplayOrder(displayOrder);
            resourceGroupService.save(group);
            redirectAttributes.addFlashAttribute("successMessage", "Group updated: " + group.getName());
        });
        return "redirect:/admin/resources";
    }

    @PostMapping("/resource-groups/delete/{id}")
    public String deleteResourceGroup(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        int unassignedCount = resourceService.clearGroupAssignment(id);
        resourceGroupService.findById(id).ifPresent(group -> {
            resourceGroupService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Group deleted: " + group.getName() + " (" + unassignedCount + " resources unassigned)");
        });
        return "redirect:/admin/resources";
    }

    @GetMapping("/resources/export")
    public ResponseEntity<byte[]> exportResources() throws Exception {
        String yaml = resourceExchangeService.exportResourcesAsYaml();
        String fileName = "kairos-resources-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".yaml";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/x-yaml"))
                .body(yaml.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/resources/import")
    public String importResources(@RequestParam("file") MultipartFile file,
                                  RedirectAttributes redirectAttributes) {
        try {
            ResourceExchangeService.ImportResult result = resourceExchangeService.importResourcesFromYaml(file);
            String message = "Import finished: " + result.getImported() + " created, "
                    + result.getUpdated() + " updated, " + result.getSkipped() + " skipped.";
            redirectAttributes.addFlashAttribute("successMessage", message);

            if (!result.getNotes().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", String.join(" | ", result.getNotes()));
            }
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not import YAML: " + ex.getMessage());
        }
        return "redirect:/admin/resources";
    }

    @PostMapping("/resources/add")
    public String addResource(@RequestParam String name,
                              @RequestParam ResourceType resourceType,
                              @RequestParam String target,
                              @RequestParam(name = "skipTLS", defaultValue = "false") boolean skipTls,
                      @RequestParam(name = "recursive", defaultValue = "false") boolean recursive,
                              @RequestParam(required = false) Long groupId,
                              @RequestParam(defaultValue = "0") int displayOrder,
                              RedirectAttributes redirectAttributes) {
        MonitoredResource resource = MonitoredResource.builder()
                .name(name)
                .resourceType(resourceType)
                .target(target)
                .skipTls(skipTls)
                .recursive(recursive)
                .active(true)
                .displayOrder(displayOrder)
                .group(resolveGroup(groupId))
                .build();
        MonitoredResource saved = resourceService.save(resource);
        checkExecutorService.runImmediateCheck(saved);
        redirectAttributes.addFlashAttribute("successMessage", "Resource added: " + name);
        return "redirect:/admin/resources";
    }

    @PostMapping("/resources/update/{id}")
    public String updateResourceGrouping(@PathVariable Long id,
                                         @RequestParam(required = false) Long groupId,
                                         @RequestParam(defaultValue = "0") int displayOrder,
                                         RedirectAttributes redirectAttributes) {
        resourceService.findById(id).ifPresent(resource -> {
            resource.setGroup(resolveGroup(groupId));
            resource.setDisplayOrder(displayOrder);
            resourceService.save(resource);
            redirectAttributes.addFlashAttribute("successMessage", "Resource order updated: " + resource.getName());
        });
        return "redirect:/admin/resources";
    }

    @GetMapping("/resources/edit/{id}")
    public String editResource(@PathVariable Long id,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        return resourceService.findById(id)
                .map(resource -> {
                    model.addAttribute("resource", resource);
                    model.addAttribute("resourceGroups", resourceGroupService.findAllOrdered());
                    model.addAttribute("resourceTypes", ResourceType.values());
                    return "admin/resource-edit";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "Resource not found.");
                    return "redirect:/admin/resources";
                });
    }

    @PostMapping("/resources/edit/{id}")
    public String updateResource(@PathVariable Long id,
                                 @RequestParam String name,
                                 @RequestParam ResourceType resourceType,
                                 @RequestParam String target,
                                 @RequestParam(name = "skipTLS", defaultValue = "false") boolean skipTls,
                                 @RequestParam(name = "recursive", defaultValue = "false") boolean recursive,
                                 @RequestParam(required = false) Long groupId,
                                 @RequestParam(defaultValue = "0") int displayOrder,
                                 RedirectAttributes redirectAttributes) {
        resourceService.findById(id).ifPresentOrElse(resource -> {
            resource.setName(name == null ? "" : name.trim());
            resource.setResourceType(resourceType);
            resource.setTarget(target == null ? "" : target.trim());
            resource.setSkipTls(skipTls);
            resource.setRecursive(recursive);
            resource.setGroup(resolveGroup(groupId));
            resource.setDisplayOrder(displayOrder);
            MonitoredResource saved = resourceService.save(resource);
            checkExecutorService.runImmediateCheck(saved);
            redirectAttributes.addFlashAttribute("successMessage", "Resource updated: " + resource.getName());
        }, () -> redirectAttributes.addFlashAttribute("errorMessage", "Resource not found."));
        return "redirect:/admin/resources";
    }

    @PostMapping("/resources/delete/{id}")
    public String deleteResource(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        resourceService.findById(id).ifPresent(r -> {
            resourceService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Resource deleted: " + r.getName());
        });
        return "redirect:/admin/resources";
    }

    @GetMapping("/resource-types")
    public String resourceTypes(Model model) {
        model.addAttribute("configs", resourceTypeConfigRepository.findAll());
        return "admin/resource-types";
    }

    @PostMapping("/resource-types/update")
    public String updateResourceType(@RequestParam Long id,
                                     @RequestParam int checkIntervalMinutes,
                                     @RequestParam int parallelism,
                                     @RequestParam(defaultValue = "3") int outageThreshold,
                                     @RequestParam(defaultValue = "2") int recoveryThreshold,
                                     RedirectAttributes redirectAttributes) {
        resourceTypeConfigRepository.findById(id).ifPresent(config -> {
            config.setCheckIntervalMinutes(checkIntervalMinutes);
            config.setParallelism(parallelism);
            config.setOutageThreshold(Math.max(1, outageThreshold));
            config.setRecoveryThreshold(Math.max(1, recoveryThreshold));
            resourceTypeConfigRepository.save(config);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Configuration updated");
        return "redirect:/admin/resource-types";
    }

    @PostMapping("/resource-types/{configId}/auth/add")
    public String addAuth(@PathVariable Long configId,
                          @RequestParam String name,
                          @RequestParam String urlPattern,
                          @RequestParam String username,
                          @RequestParam String password,
                          RedirectAttributes redirectAttributes) {
        resourceTypeConfigRepository.findById(configId).ifPresent(config -> {
            ResourceTypeAuth auth = ResourceTypeAuth.builder()
                    .resourceTypeConfig(config)
                    .name(name)
                    .authType(AuthType.BASIC)
                    .urlPattern(urlPattern)
                    .username(username)
                    .password(password)
                    .build();
            resourceTypeAuthRepository.save(auth);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Authentication '" + name + "' added to " + config.getTypeName());
        });
        return "redirect:/admin/resource-types";
    }

    @PostMapping("/resource-types/auth/delete/{authId}")
    public String deleteAuth(@PathVariable Long authId, RedirectAttributes redirectAttributes) {
        resourceTypeAuthRepository.findById(authId).ifPresent(auth -> {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Authentication '" + auth.getName() + "' deleted");
            resourceTypeAuthRepository.delete(auth);
        });
        return "redirect:/admin/resource-types";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users";
    }

    @GetMapping("/api-keys")
    public String apiKeys(Model model) {
        model.addAttribute("apiKeys", apiKeyService.findAllOrderedByCreatedAtDesc());
        return "admin/api-keys";
    }

    @PostMapping("/api-keys/add")
    public String addApiKey(@RequestParam String name,
                            Authentication authentication,
                            RedirectAttributes redirectAttributes) {
        String creator = authentication != null ? authentication.getName() : "system";
        ApiKeyService.CreatedApiKey createdApiKey = apiKeyService.create(name, creator);
        redirectAttributes.addFlashAttribute("successMessage", "API key created: " + createdApiKey.apiKey().getName());
        redirectAttributes.addFlashAttribute("newApiKeyToken", createdApiKey.token());
        return "redirect:/admin/api-keys";
    }

    @PostMapping("/api-keys/delete/{id}")
    public String deleteApiKey(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        apiKeyService.findById(id).ifPresent(apiKey -> {
            apiKeyService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "API key deleted: " + apiKey.getName());
        });
        return "redirect:/admin/api-keys";
    }

    @GetMapping("/announcements")
    public String announcements(Model model) {
        model.addAttribute("announcements", announcementService.findAllOrderedByCreatedAtDesc());
        return "admin/announcements";
    }

    @GetMapping("/announcements/new")
    public String newAnnouncement(Model model) {
        Announcement announcement = Announcement.builder()
                .active(true)
                .kind(AnnouncementKind.INFORMATION)
                .build();
        model.addAttribute("announcement", announcement);
        model.addAttribute("announcementKinds", AnnouncementKind.values());
        model.addAttribute("formAction", "/admin/announcements/add");
        model.addAttribute("pageTitle", "Create Announcement");
        model.addAttribute("submitLabel", "Create Announcement");
        return "admin/announcement-form";
    }

    @PostMapping("/announcements/add")
    public String addAnnouncement(@RequestParam AnnouncementKind kind,
                                  @RequestParam String content,
                                  @RequestParam(defaultValue = "false") boolean active,
                                  @RequestParam(required = false) String activeUntil,
                      Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        Announcement announcement = Announcement.builder()
                .kind(kind)
                .content(content)
            .createdBy(authentication != null ? authentication.getName() : "system")
                .active(active)
                .activeUntil(parseDateTime(activeUntil))
                .build();
        announcementService.save(announcement);
        redirectAttributes.addFlashAttribute("successMessage", "Announcement created");
        return "redirect:/admin/announcements";
    }

    @GetMapping("/announcements/edit/{id}")
    public String editAnnouncement(@PathVariable Long id,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        return announcementService.findById(id).map(announcement -> {
            model.addAttribute("announcement", announcement);
            model.addAttribute("announcementKinds", AnnouncementKind.values());
            model.addAttribute("formAction", "/admin/announcements/update/" + id);
            model.addAttribute("pageTitle", "Edit Announcement");
            model.addAttribute("submitLabel", "Update Announcement");
            return "admin/announcement-form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Announcement not found");
            return "redirect:/admin/announcements";
        });
    }

    @PostMapping("/announcements/update/{id}")
    public String updateAnnouncement(@PathVariable Long id,
                                     @RequestParam AnnouncementKind kind,
                                     @RequestParam String content,
                                     @RequestParam(defaultValue = "false") boolean active,
                                     @RequestParam(required = false) String activeUntil,
                                     RedirectAttributes redirectAttributes) {
        announcementService.findById(id).ifPresent(announcement -> {
            announcement.setKind(kind);
            announcement.setContent(content);
            announcement.setActive(active);
            announcement.setActiveUntil(parseDateTime(activeUntil));
            announcementService.save(announcement);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Announcement updated");
        return "redirect:/admin/announcements";
    }

    @PostMapping("/announcements/delete/{id}")
    public String deleteAnnouncement(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        announcementService.findById(id).ifPresent(a -> {
            announcementService.delete(id);
            redirectAttributes.addFlashAttribute("successMessage", "Announcement deleted");
        });
        return "redirect:/admin/announcements";
    }

    @PostMapping("/users/add")
    public String addUser(@RequestParam String email,
                          @RequestParam String password,
                          @RequestParam(defaultValue = "USER") UserRole role,
                          RedirectAttributes redirectAttributes) {
        try {
            userService.createUser(email, password, role);
            redirectAttributes.addFlashAttribute("successMessage", "User created: " + email);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error creating user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/users/delete/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        userService.findById(id).ifPresent(u -> {
            userService.deleteUser(id);
            redirectAttributes.addFlashAttribute("successMessage", "User deleted: " + u.getEmail());
        });
        return "redirect:/admin/users";
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value);
    }

    private ResourceGroup resolveGroup(Long groupId) {
        if (groupId == null || groupId <= 0) {
            return null;
        }
        return resourceGroupService.findById(groupId).orElse(null);
    }

    private List<AdminResourceGroupViewModel> buildAdminResourceGroups(List<MonitoredResource> resources,
                                                                        List<ResourceGroup> groups) {
        Map<Long, List<MonitoredResource>> byGroupId = new LinkedHashMap<>();
        Map<String, MonitoredResource> dockerRepositoryByManagedGroupName = new LinkedHashMap<>();
        List<MonitoredResource> ungrouped = new ArrayList<>();

        for (MonitoredResource resource : resources) {
            if (resource.getResourceType() == ResourceType.DOCKERREPOSITORY) {
                dockerRepositoryByManagedGroupName.put(managedGroupName(resource.getTarget()), resource);
                continue;
            }

            if (resource.getGroup() == null) {
                ungrouped.add(resource);
                continue;
            }
            byGroupId.computeIfAbsent(resource.getGroup().getId(), ignored -> new ArrayList<>()).add(resource);
        }

        List<AdminResourceGroupViewModel> result = new ArrayList<>();
        result.add(AdminResourceGroupViewModel.builder()
                .groupId(null)
                .groupName("Ungrouped")
                .ungrouped(true)
                .resources(ungrouped)
                .build());

        for (ResourceGroup group : groups) {
            List<MonitoredResource> groupedResources = byGroupId.getOrDefault(group.getId(), new ArrayList<>());
            result.add(AdminResourceGroupViewModel.builder()
                    .groupId(group.getId())
                    .groupName(group.getName())
                    .ungrouped(false)
                    .resources(groupedResources)
                    .dockerRepositoryResource(dockerRepositoryByManagedGroupName.get(group.getName()))
                    .build());
        }

        return result;
    }

    private String managedGroupName(String target) {
        String normalizedTarget = target == null ? "" : target.trim();
        return "Dockerrepository: " + normalizedTarget;
    }
}

package tech.wenisch.kairos.controller;

import tech.wenisch.kairos.dto.DashboardGroupShell;
import tech.wenisch.kairos.dto.ResourceViewModel;
import tech.wenisch.kairos.dto.TimelineBlockDTO;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.EmbedPolicy;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.entity.ResourceGroupVisibility;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;
import tech.wenisch.kairos.service.AnnouncementService;
import tech.wenisch.kairos.service.ApplicationVersionService;
import tech.wenisch.kairos.service.CheckExecutorService;
import tech.wenisch.kairos.service.EmbedSettingsService;
import tech.wenisch.kairos.service.InstantCheckService;
import tech.wenisch.kairos.service.OutageService;
import tech.wenisch.kairos.service.ResourceGroupService;
import tech.wenisch.kairos.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#?([0-9a-fA-F]{6}|[0-9a-fA-F]{3})$");

    private final ResourceService resourceService;
    private final CheckExecutorService checkExecutorService;
    private final AnnouncementService announcementService;
    private final ApplicationVersionService applicationVersionService;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;
    private final OutageService outageService;
    private final EmbedSettingsService embedSettingsService;
    private final ResourceGroupService resourceGroupService;
    private final InstantCheckService instantCheckService;

    @GetMapping("/")
    public String index(Authentication authentication, Model model) {
        if (!isPublicAccessAllowed() && !isAuthenticated(authentication)) {
            return "redirect:/login";
        }

        boolean authenticated = isAuthenticated(authentication);
        List<MonitoredResource> resources = resourceService.findAllActive().stream()
                .filter(resource -> isVisibleByGroupPolicy(resource, authenticated))
                .toList();

        List<MonitoredResource> ungroupedResources = new ArrayList<>();
        Map<Long, ResourceGroup> groupsById = new LinkedHashMap<>();
        Map<Long, List<MonitoredResource>> groupedResourceMap = new LinkedHashMap<>();

        for (MonitoredResource resource : resources) {
            if (resource.getGroups().isEmpty()) {
                ungroupedResources.add(resource);
            } else {
                for (ResourceGroup group : resource.getGroups()) {
                    groupsById.putIfAbsent(group.getId(), group);
                    groupedResourceMap.computeIfAbsent(group.getId(), ignored -> new ArrayList<>()).add(resource);
                }
            }
        }

        List<DashboardGroupShell> groupedResources = groupedResourceMap.entrySet().stream()
                .map(entry -> new DashboardGroupShell(groupsById.get(entry.getKey()), entry.getValue()))
                .toList();

        boolean allowPublicAdd = isPublicAddAllowed();
        boolean allowResourceSubmit = allowPublicAdd || authenticated;

        model.addAttribute("totalResourceCount", resources.size());
        model.addAttribute("ungroupedResources", ungroupedResources);
        model.addAttribute("groupedResources", groupedResources);
        model.addAttribute("announcements", announcementService.findAllActiveForPublicView());
        model.addAttribute("allowPublicAdd", allowPublicAdd);
        model.addAttribute("allowResourceSubmit", allowResourceSubmit);
        model.addAttribute("showResourceUrl", shouldShowResourceUrl(authentication));
        model.addAttribute("resourceTypes", ResourceType.values());
        InstantCheckService.InstantCheckSettings instantCheckSettings = instantCheckService.getSettings();
        model.addAttribute("instantCheckEnabled", instantCheckSettings.enabled());
        model.addAttribute("instantCheckAvailableForCurrentUser",
            instantCheckSettings.enabled() && (instantCheckSettings.allowPublic() || authenticated));
        model.addAttribute("instantCheckRequiresAuth",
            instantCheckSettings.enabled() && !instantCheckSettings.allowPublic() && !authenticated);
        model.addAttribute("appVersion", applicationVersionService.getVersion());
        return "index";
    }

    @GetMapping("/outages")
    public String outages(@RequestParam(defaultValue = "active") String status,
                          @RequestParam(defaultValue = "24h") String range,
                          Model model) {
        int rangeHours = parseRangeHours(range);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rangeStart = now.minusHours(rangeHours);

        String normalizedStatus = normalizeOutageStatus(status);
        List<OutageRowViewModel> rows = outageService.findAll().stream()
                .filter(outage -> matchesStatus(outage, normalizedStatus))
                .filter(outage -> overlaps(outage, rangeStart, now))
                .map(outage -> toOutageRow(outage, rangeStart, now))
                .toList();

        List<OutageGanttTick> ticks = buildTicks(rangeStart, now, rangeHours);

        model.addAttribute("rows", rows);
        model.addAttribute("selectedStatus", normalizedStatus);
        model.addAttribute("selectedRange", formatRangeKey(rangeHours));
        model.addAttribute("rangeLabel", formatRangeLabel(rangeHours));
        model.addAttribute("ticks", ticks);
        model.addAttribute("now", now);
        return "outages";
    }

    @GetMapping("/groups/{id}")
    public String groupDashboard(@PathVariable Long id, Authentication authentication, Model model) {
        ResourceGroup group = resourceGroupService.findById(id).orElse(null);
        if (group == null) {
            return "redirect:/";
        }

        boolean authenticated = isAuthenticated(authentication);
        ResourceGroupVisibility groupVisibility = group.getVisibilityOrDefault();
        if (groupVisibility == ResourceGroupVisibility.AUTHENTICATED && !authenticated) {
            return "redirect:/login";
        }

        if (groupVisibility == ResourceGroupVisibility.HIDDEN || !isGroupVisibleByPolicy(group, authenticated)) {
            return "redirect:/";
        }

        List<MonitoredResource> groupResources = resourceService.findAllActive().stream()
                .filter(resource -> resource.getGroups().stream().anyMatch(g -> g.getId().equals(id)))
                .filter(resource -> isVisibleByGroupPolicy(resource, authenticated))
                .toList();

        model.addAttribute("group", group);
        model.addAttribute("totalResourceCount", groupResources.size());
        model.addAttribute("groupResources", groupResources);
        model.addAttribute("showResourceUrl", shouldShowResourceUrl(authentication));
        model.addAttribute("appVersion", applicationVersionService.getVersion());
        return "group-dashboard";
    }

    @PostMapping("/resources/submit")
    public String submitResource(
            @RequestParam String name,
            @RequestParam ResourceType resourceType,
            @RequestParam String target,
            @RequestParam(name = "skipTLS", defaultValue = "false") boolean skipTls,
            @RequestParam(name = "recursive", defaultValue = "false") boolean recursive,
            Authentication authentication,
            RedirectAttributes redirectAttributes
    ) {
        if (!isPublicAddAllowed() && !isAuthenticated(authentication)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Authentication is required to submit resources.");
            return "redirect:/";
        }

        if (name == null || name.isBlank() || target == null || target.isBlank()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Name and target are required.");
            return "redirect:/";
        }

        MonitoredResource resource = MonitoredResource.builder()
                .name(name.trim())
                .resourceType(resourceType)
                .target(target.trim())
                .skipTls(skipTls)
                .recursive(recursive)
                .active(true)
                .build();
        MonitoredResource saved = resourceService.save(resource);
        checkExecutorService.runImmediateCheck(saved);
        redirectAttributes.addFlashAttribute("successMessage", "Resource submitted successfully.");
        return "redirect:/";
    }

    @GetMapping("/announcements")
    public String announcements(Model model) {
        model.addAttribute("announcements", announcementService.findAllOrderedByCreatedAtDesc());
        return "announcements";
    }

    @GetMapping("/embed/status")
    public String embedStatus(@RequestParam(name = "refresh", defaultValue = "30") int refreshSeconds,
                              @RequestParam(name = "mode", required = false) String mode,
                              @RequestParam(name = "fontSize", defaultValue = "15") int fontSize,
                              @RequestParam(name = "fontColor", required = false) String fontColor,
                              @RequestParam(name = "bgColor", required = false) String bgColor,
                              @RequestParam(name = "showScope", defaultValue = "false") boolean showScope,
                              @RequestParam(name = "groupId", required = false) Long groupId,
                              Authentication authentication,
                              Model model) {
        if (embedSettingsService.getPolicy() == EmbedPolicy.DISABLED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        int sanitizedRefreshSeconds = Math.min(3600, Math.max(10, refreshSeconds));
        int sanitizedFontSize = Math.min(32, Math.max(6, fontSize));
        String normalizedMode = "dark".equalsIgnoreCase(mode) ? "dark" : "light";
        String normalizedFontColor = normalizeHexColor(fontColor);
        String normalizedBackgroundColor = normalizeHexColor(bgColor);
        if ((normalizedBackgroundColor == null || normalizedBackgroundColor.isBlank()) && "dark".equals(normalizedMode)) {
            // Use a lighter shade that matches the translucent wrapper surface in dark mode.
            normalizedBackgroundColor = "#252930";
        }
        boolean authenticated = isAuthenticated(authentication);

        long activeOutages;
        String embedTargetUrl;
        String statusScopeLabel;

        if (groupId == null) {
            activeOutages = outageService.countActiveOutages();
            embedTargetUrl = "/";
            statusScopeLabel = "All resources";
        } else {
            ResourceGroup group = resourceGroupService.findById(groupId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (!isGroupVisibleByPolicy(group, authenticated)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN);
            }

            List<MonitoredResource> groupResources = resourceService.findAllActive().stream()
                    .filter(resource -> resource.getGroups().stream().anyMatch(g -> g.getId().equals(groupId)))
                    .filter(resource -> isVisibleByGroupPolicy(resource, authenticated))
                    .toList();

            java.util.Set<Long> groupResourceIds = groupResources.stream()
                    .map(MonitoredResource::getId)
                    .collect(java.util.stream.Collectors.toSet());

            activeOutages = outageService.findAll().stream()
                    .filter(Outage::isActive)
                    .map(Outage::getResource)
                    .filter(java.util.Objects::nonNull)
                    .map(MonitoredResource::getId)
                    .filter(groupResourceIds::contains)
                    .count();

            embedTargetUrl = "/groups/" + groupId;
            statusScopeLabel = group.getName();
        }

        boolean hasActiveIncidents = activeOutages > 0;

        model.addAttribute("refreshSeconds", sanitizedRefreshSeconds);
        model.addAttribute("mode", normalizedMode);
        model.addAttribute("fontSize", sanitizedFontSize);
        model.addAttribute("fontColor", normalizedFontColor);
        model.addAttribute("backgroundColor", normalizedBackgroundColor);
        model.addAttribute("hasActiveIncidents", hasActiveIncidents);
        model.addAttribute("activeOutages", activeOutages);
        model.addAttribute("embedTargetUrl", embedTargetUrl);
        model.addAttribute("statusScopeLabel", statusScopeLabel);
        model.addAttribute("showScope", showScope);
        return "embed-status";
    }

    @PostMapping("/resources/{id}/check")
    public String runManualCheck(@PathVariable Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        MonitoredResource resource = resourceService.findById(id).orElse(null);
        if (resource == null || !isVisibleByGroupPolicy(resource, isAuthenticated(authentication))) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resource not found.");
            return "redirect:/";
        }

        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (!isAdmin && !isPublicCheckNowAllowed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Manual checks are not available for public users.");
            return "redirect:/resources/" + id;
        }
        boolean triggered = checkExecutorService.runImmediateCheck(id);
        if (triggered) {
            redirectAttributes.addFlashAttribute("successMessage", "Check started immediately.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Resource not found, inactive, or unsupported type.");
        }
        return "redirect:/resources/" + id;
    }

    @GetMapping("/resources/{id}")
    public String detail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message,
            @RequestParam(name = "outagePage", defaultValue = "0") int outagePageNumber,
            @RequestParam(name = "outageSize", defaultValue = "10") int outageSize,
            @RequestParam(name = "outageStatus", defaultValue = "all") String outageStatus,
            @RequestParam(name = "outageRange", defaultValue = "30d") String outageRange,
            Authentication authentication,
            Model model
    ) {
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        model.addAttribute("allowCheckNow", isAdmin || isPublicCheckNowAllowed());
        model.addAttribute("showResourceUrl", shouldShowResourceUrl(authentication));
        boolean authenticated = isAuthenticated(authentication);

        return resourceService.findById(id).map(resource -> {
            if (!isVisibleByGroupPolicy(resource, authenticated)) {
                return "redirect:/";
            }

            int sanitizedPage = Math.max(0, page);
            int sanitizedSize = normalizePageSize(size);
            CheckStatus statusFilter = parseStatus(status);
            int sanitizedOutageSize = normalizeOutagePageSize(outageSize);
            int sanitizedOutagePageNumber = Math.max(0, outagePageNumber);
            String normalizedOutageStatus = normalizeOutageStatus(outageStatus);
            int outageRangeHours = parseRangeHours(outageRange);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime outageRangeStart = now.minusHours(outageRangeHours);

            String currentStatus = resourceService.getCurrentStatus(resource);
            List<TimelineBlockDTO> timelineBlocks = resourceService.getTimelineBlocks(resource);
            double uptime24h = resourceService.getUptimePercentage(resource, 24);
            double uptime7d = resourceService.getUptimePercentage(resource, 168);
            double uptime30d = resourceService.getUptimePercentage(resource, 720);
                List<CheckResult> fullHistory = resourceService.getFullHistory(id);
            Page<CheckResult> historyPage = resourceService.getHistoryPage(
                    id,
                    sanitizedPage,
                    sanitizedSize,
                    statusFilter,
                    code,
                    message
            );

                List<Outage> allOutages = outageService.findByResource(resource);
                List<Outage> filteredOutages = allOutages.stream()
                    .filter(outage -> matchesStatus(outage, normalizedOutageStatus))
                    .filter(outage -> overlaps(outage, outageRangeStart, now))
                    .toList();

            int outageTotal = filteredOutages.size();
            int outageTotalPages = outageTotal == 0 ? 1 : (int) Math.ceil((double) outageTotal / sanitizedOutageSize);
            int effectiveOutagePage = Math.min(sanitizedOutagePageNumber, Math.max(0, outageTotalPages - 1));
            int outageFromIndex = Math.min(effectiveOutagePage * sanitizedOutageSize, outageTotal);
            int outageToIndex = Math.min(outageFromIndex + sanitizedOutageSize, outageTotal);

            List<Outage> outagePageContent = filteredOutages.subList(outageFromIndex, outageToIndex);
            Page<Outage> resourceOutagePage = new PageImpl<>(
                    outagePageContent,
                    PageRequest.of(effectiveOutagePage, sanitizedOutageSize),
                    outageTotal
            );

            Map<Long, String> resourceOutageDurations = outagePageContent.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            Outage::getId,
                            outage -> formatDuration(
                                    outage.getStartDate(),
                                    outage.getEndDate() != null ? outage.getEndDate() : now
                            )
                    ));

            ResourceViewModel viewModel = ResourceViewModel.builder()
                    .resource(resource)
                    .currentStatus(currentStatus)
                    .timelineBlocks(timelineBlocks)
                    .uptimePercentage(uptime24h)
                    .build();

            var activeOutage = outageService.findActiveOutage(resource);
                String detailSummaryText = buildDetailSummary(
                    resource,
                    currentStatus,
                    uptime24h,
                    uptime7d,
                    uptime30d,
                    timelineBlocks,
                    fullHistory,
                    allOutages,
                    activeOutage.orElse(null)
                );

            model.addAttribute("vm", viewModel);
            model.addAttribute("uptime24h", uptime24h);
            model.addAttribute("uptime7d", uptime7d);
            model.addAttribute("uptime30d", uptime30d);
            model.addAttribute("detailSummaryText", detailSummaryText);
            model.addAttribute("activeOutage", activeOutage.orElse(null));
            activeOutage.ifPresent(outage ->
                    model.addAttribute("activeOutageDuration", formatDuration(outage.getStartDate(), LocalDateTime.now())));
            model.addAttribute("recentHistory", historyPage.getContent());
            model.addAttribute("historyPage", historyPage);
            model.addAttribute("historyStatus", statusFilter != null ? statusFilter.name() : "");
            model.addAttribute("historyCode", normalizeTextFilter(code));
            model.addAttribute("historyMessage", normalizeTextFilter(message));
            model.addAttribute("historySize", sanitizedSize);
            model.addAttribute("historyFrom", historyPage.getTotalElements() == 0 ? 0 : (long) historyPage.getNumber() * historyPage.getSize() + 1);
            model.addAttribute("historyTo", historyPage.getTotalElements() == 0 ? 0 : Math.min((long) (historyPage.getNumber() + 1) * historyPage.getSize(), historyPage.getTotalElements()));
            model.addAttribute("groupStatusSummaries", buildGroupStatusSummaries(resource, authenticated));

            model.addAttribute("resourceOutages", outagePageContent);
            model.addAttribute("resourceOutagePage", resourceOutagePage);
            model.addAttribute("outageDurations", resourceOutageDurations);
            model.addAttribute("outageFilterStatus", normalizedOutageStatus);
            model.addAttribute("outageFilterRange", formatRangeKey(outageRangeHours));
            model.addAttribute("outageFilterSize", sanitizedOutageSize);
            model.addAttribute("outageFrom", outageTotal == 0 ? 0 : (long) resourceOutagePage.getNumber() * resourceOutagePage.getSize() + 1);
            model.addAttribute("outageTo", outageTotal == 0 ? 0 : Math.min((long) (resourceOutagePage.getNumber() + 1) * resourceOutagePage.getSize(), resourceOutagePage.getTotalElements()));
            return "detail";
        }).orElse("redirect:/");
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    private int normalizePageSize(int size) {
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        if (size <= 50) return 50;
        return 100;
    }

    private int normalizeOutagePageSize(int size) {
        if (size <= 5) return 5;
        if (size <= 10) return 10;
        if (size <= 20) return 20;
        return 50;
    }

    private String normalizeTextFilter(String value) {
        return value == null ? "" : value.trim();
    }

    private CheckStatus parseStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }
        try {
            return CheckStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String formatDuration(LocalDateTime start, LocalDateTime end) {
        Duration duration = Duration.between(start, end);
        long totalMinutes = Math.max(0, duration.toMinutes());
        long days = totalMinutes / (24 * 60);
        long hours = (totalMinutes % (24 * 60)) / 60;
        long minutes = totalMinutes % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        }
        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        }
        return String.format("%dm", minutes);
    }

    private boolean matchesStatus(Outage outage, String status) {
        return switch (status) {
            case "resolved" -> !outage.isActive();
            case "all" -> true;
            default -> outage.isActive();
        };
    }

    private boolean overlaps(Outage outage, LocalDateTime rangeStart, LocalDateTime now) {
        LocalDateTime outageEnd = outage.getEndDate() != null ? outage.getEndDate() : now;
        return !outage.getStartDate().isAfter(now) && !outageEnd.isBefore(rangeStart);
    }

    private OutageRowViewModel toOutageRow(Outage outage, LocalDateTime rangeStart, LocalDateTime now) {
        LocalDateTime effectiveEnd = outage.getEndDate() != null ? outage.getEndDate() : now;
        LocalDateTime clampedStart = outage.getStartDate().isBefore(rangeStart) ? rangeStart : outage.getStartDate();
        LocalDateTime clampedEnd = effectiveEnd.isAfter(now) ? now : effectiveEnd;

        long totalWindowSeconds = Math.max(1, Duration.between(rangeStart, now).getSeconds());
        long startOffsetSeconds = Math.max(0, Duration.between(rangeStart, clampedStart).getSeconds());
        long barSeconds = Math.max(1, Duration.between(clampedStart, clampedEnd).getSeconds());

        double leftPercent = (startOffsetSeconds * 100.0) / totalWindowSeconds;
        double widthPercent = Math.max(0.75, (barSeconds * 100.0) / totalWindowSeconds);

        return new OutageRowViewModel(
                outage,
                formatDuration(outage.getStartDate(), effectiveEnd),
                String.format(Locale.US, "%.2f%%", Math.min(100.0, leftPercent)),
                String.format(Locale.US, "%.2f%%", Math.min(100.0, widthPercent))
        );
    }

    private List<OutageGanttTick> buildTicks(LocalDateTime rangeStart, LocalDateTime now, int rangeHours) {
        return IntStream.rangeClosed(0, 6)
                .mapToObj(index -> {
                    LocalDateTime point = rangeStart.plusSeconds(Duration.between(rangeStart, now).getSeconds() * index / 6);
                    String labelPattern = rangeHours <= 24 ? "HH:mm" : "MM-dd HH:mm";
                    return new OutageGanttTick(
                            String.format(Locale.US, "%.2f%%", (index * 100.0) / 6.0),
                            point.format(java.time.format.DateTimeFormatter.ofPattern(labelPattern))
                    );
                })
                .toList();
    }

    private int parseRangeHours(String range) {
        if ("7d".equalsIgnoreCase(range)) {
            return 24 * 7;
        }
        if ("30d".equalsIgnoreCase(range)) {
            return 24 * 30;
        }
        return 24;
    }

    private String formatRangeKey(int hours) {
        if (hours == 24 * 7) {
            return "7d";
        }
        if (hours == 24 * 30) {
            return "30d";
        }
        return "24h";
    }

    private String formatRangeLabel(int hours) {
        if (hours == 24 * 7) {
            return "last 7 days";
        }
        if (hours == 24 * 30) {
            return "last 30 days";
        }
        return "last 24 hours";
    }

    private String normalizeOutageStatus(String status) {
        if (status == null) {
            return "active";
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "all", "resolved", "active" -> normalized;
            default -> "active";
        };
    }

    private boolean isPublicAddAllowed() {
        return resourceTypeConfigRepository.findAll().stream().anyMatch(ResourceTypeConfig::isAllowPublicAdd);
    }

    private boolean isPublicAccessAllowed() {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        if (configs.isEmpty()) {
            return true;
        }
        return configs.stream().allMatch(ResourceTypeConfig::isAllowPublicAccess);
    }

    private boolean isPublicCheckNowAllowed() {
        return resourceTypeConfigRepository.findAll().stream().anyMatch(ResourceTypeConfig::isAllowPublicCheckNow);
    }

    private boolean isAlwaysDisplayUrlEnabled() {
        return resourceTypeConfigRepository.findAll().stream().anyMatch(ResourceTypeConfig::isAlwaysDisplayUrl);
    }

    private boolean shouldShowResourceUrl(Authentication authentication) {
        return isAlwaysDisplayUrlEnabled() || isAuthenticated(authentication);
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
                .min(Comparator.comparingInt(ResourceGroupVisibility::ordinal))
                .orElse(ResourceGroupVisibility.PUBLIC);
        return switch (effective) {
            case PUBLIC -> true;
            case AUTHENTICATED -> authenticated;
            case HIDDEN -> false;
        };
    }

    private boolean isGroupVisibleByPolicy(ResourceGroup group, boolean authenticated) {
        ResourceGroupVisibility visibility = group.getVisibilityOrDefault();
        return switch (visibility) {
            case PUBLIC -> true;
            case AUTHENTICATED -> authenticated;
            case HIDDEN -> false;
        };
    }

    private Map<Long, GroupStatusSummary> buildGroupStatusSummaries(MonitoredResource detailResource, boolean authenticated) {
        if (detailResource.getGroups().isEmpty()) {
            return Map.of();
        }

        Map<Long, long[]> counters = new LinkedHashMap<>();
        detailResource.getGroups().forEach(group -> counters.put(group.getId(), new long[]{0, 0, 0}));

        List<MonitoredResource> visibleResources = resourceService.findAllActive().stream()
                .filter(resource -> isVisibleByGroupPolicy(resource, authenticated))
                .toList();

        for (MonitoredResource resource : visibleResources) {
            String status = resourceService.getCurrentStatus(resource);
            for (ResourceGroup group : resource.getGroups()) {
                long[] values = counters.get(group.getId());
                if (values == null) {
                    continue;
                }

                if ("available".equals(status)) {
                    values[0] += 1;
                } else if ("not-available".equals(status)) {
                    values[1] += 1;
                } else {
                    values[2] += 1;
                }
            }
        }

        Map<Long, GroupStatusSummary> summaries = new LinkedHashMap<>();
        counters.forEach((groupId, values) -> summaries.put(groupId, new GroupStatusSummary(values[0], values[1], values[2])));
        return summaries;
    }

    private String normalizeHexColor(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (!HEX_COLOR_PATTERN.matcher(trimmed).matches()) {
            return "";
        }
        return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
    }

    private String buildDetailSummary(MonitoredResource resource,
                                      String currentStatus,
                                      double uptime24h,
                          double uptime7d,
                          double uptime30d,
                                      List<TimelineBlockDTO> timelineBlocks,
                                      List<CheckResult> fullHistory,
                          List<Outage> allOutages,
                                      Outage activeOutage) {
        LocalDateTime now = LocalDateTime.now();
        java.time.format.DateTimeFormatter summaryTimestampFormat = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String createdAtText = resource.getCreatedAt() == null
                ? "an unknown date"
            : resource.getCreatedAt().format(summaryTimestampFormat);
        String ageText = resource.getCreatedAt() == null
                ? "for an unknown duration"
                : formatDuration(resource.getCreatedAt(), now);

        long totalChecks = fullHistory.size();
        long availableChecks = fullHistory.stream().filter(result -> result.getStatus() == CheckStatus.AVAILABLE).count();
        long downChecks = fullHistory.stream().filter(result -> result.getStatus() == CheckStatus.NOT_AVAILABLE).count();
        long unknownChecks = fullHistory.stream().filter(result -> result.getStatus() == CheckStatus.UNKNOWN).count();
        long relevantChecks = fullHistory.stream().filter(result -> result.getStatus() != CheckStatus.UNKNOWN).count();
        double lifetimeAvailability = relevantChecks == 0 ? 0.0 : ((double) availableChecks / (double) relevantChecks) * 100.0;

        LocalDateTime lastCheckAt = fullHistory.stream()
            .map(CheckResult::getCheckedAt)
            .filter(java.util.Objects::nonNull)
            .max(LocalDateTime::compareTo)
            .orElse(null);

        Long latestLatencyMs = fullHistory.stream()
            .filter(result -> result.getCheckedAt() != null)
            .max(Comparator.comparing(CheckResult::getCheckedAt))
            .map(CheckResult::getLatencyMs)
            .orElse(null);

        List<Long> latencyCandidates = fullHistory.stream()
                .map(CheckResult::getLatencyMs)
                .filter(latency -> latency != null && latency >= 0)
                .toList();
        if (latencyCandidates.isEmpty()) {
            latencyCandidates = timelineBlocks.stream()
                    .map(TimelineBlockDTO::latencyMs)
                    .filter(latency -> latency != null && latency >= 0)
                    .toList();
        }

        String averageLatencyText;
        if (latencyCandidates.isEmpty()) {
            averageLatencyText = "no latency data yet";
        } else {
            double averageLatency = latencyCandidates.stream().mapToLong(Long::longValue).average().orElse(0.0);
            averageLatencyText = String.format(Locale.US, "%.0f ms", averageLatency);
        }

        String latencyPercentileText = "n/a";
        if (!latencyCandidates.isEmpty()) {
            List<Long> sortedLatencies = latencyCandidates.stream().sorted().toList();
            int percentileIndex = (int) Math.ceil(0.95 * sortedLatencies.size()) - 1;
            int safePercentileIndex = Math.min(sortedLatencies.size() - 1, Math.max(0, percentileIndex));
            latencyPercentileText = sortedLatencies.get(safePercentileIndex) + " ms";
        }

        long activeOutageCount = allOutages.stream().filter(Outage::isActive).count();
        long resolvedOutageCount = allOutages.stream().filter(outage -> !outage.isActive()).count();
        long longestOutageMinutes = allOutages.stream()
                .mapToLong(outage -> {
                    LocalDateTime endDate = outage.getEndDate() != null ? outage.getEndDate() : now;
                    return Math.max(0L, Duration.between(outage.getStartDate(), endDate).toMinutes());
                })
                .max()
                .orElse(0L);

        String lastCheckText = lastCheckAt == null ? "no completed checks yet" : lastCheckAt.format(summaryTimestampFormat);
        String latestLatencyText = latestLatencyMs == null || latestLatencyMs < 0 ? "n/a" : latestLatencyMs + " ms";

        String statusText = switch (normalizeStatusLabel(currentStatus)) {
            case "available" -> "available";
            case "not-available" -> "currently down";
            default -> "in an unknown state";
        };

        String outageText = activeOutage == null
                ? "No active outage is currently detected."
                : "An active outage is ongoing since "
                + activeOutage.getStartDate().format(summaryTimestampFormat)
                + ".";

        return "This resource was added on " + createdAtText + " and has been monitored " + ageText + ". "
                + "It is " + statusText + " right now, with a lifetime availability of "
                + String.format(Locale.US, "%.1f", lifetimeAvailability) + "% across " + relevantChecks + " evaluated checks"
                + " (" + totalChecks + " total checks, " + unknownChecks + " unknown, " + downChecks + " down). "
                + "Availability trends are "
                + String.format(Locale.US, "%.1f", uptime24h) + "% over 24h, "
                + String.format(Locale.US, "%.1f", uptime7d) + "% over 7d, and "
                + String.format(Locale.US, "%.1f", uptime30d) + "% over 30d. "
                + "Latency currently sits at " + latestLatencyText + ", averages " + averageLatencyText + ", and p95 is " + latencyPercentileText + ". "
                + "Last check was recorded at " + lastCheckText + ". "
                + "Outage history includes " + allOutages.size() + " incidents ("
                + activeOutageCount + " active, " + resolvedOutageCount + " resolved), with the longest lasting about "
                + longestOutageMinutes + " minutes. "
                + outageText;
    }

    private String normalizeStatusLabel(String status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status.trim().toLowerCase(Locale.ROOT)) {
            case "available" -> "available";
            case "not-available" -> "not-available";
            default -> "unknown";
        };
    }

    private record OutageRowViewModel(
            Outage outage,
            String duration,
            String leftPercent,
            String widthPercent
    ) {
    }

    private record OutageGanttTick(
            String leftPercent,
            String label
    ) {
    }

        private record GroupStatusSummary(
            long available,
            long down,
            long unknown
        ) {
        }
}

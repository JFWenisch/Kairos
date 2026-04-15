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
import tech.wenisch.kairos.service.OutageService;
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
            ResourceGroup group = resource.getGroup();
            if (group == null) {
                ungroupedResources.add(resource);
            } else {
                groupsById.putIfAbsent(group.getId(), group);
                groupedResourceMap.computeIfAbsent(group.getId(), ignored -> new ArrayList<>()).add(resource);
            }
        }

        List<DashboardGroupShell> groupedResources = groupedResourceMap.entrySet().stream()
                .map(entry -> new DashboardGroupShell(groupsById.get(entry.getKey()), entry.getValue()))
                .toList();

        model.addAttribute("totalResourceCount", resources.size());
        model.addAttribute("ungroupedResources", ungroupedResources);
        model.addAttribute("groupedResources", groupedResources);
        model.addAttribute("announcements", announcementService.findAllActiveForPublicView());
        model.addAttribute("allowPublicAdd", isPublicAddAllowed());
        model.addAttribute("showResourceUrl", shouldShowResourceUrl(authentication));
        model.addAttribute("resourceTypes", ResourceType.values());
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

    @PostMapping("/resources/submit")
    public String submitResource(
            @RequestParam String name,
            @RequestParam ResourceType resourceType,
            @RequestParam String target,
            @RequestParam(name = "skipTLS", defaultValue = "false") boolean skipTls,
            @RequestParam(name = "recursive", defaultValue = "false") boolean recursive,
            RedirectAttributes redirectAttributes
    ) {
        if (!isPublicAddAllowed()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Public resource submission is currently disabled.");
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
                              Model model) {
        if (embedSettingsService.getPolicy() == EmbedPolicy.DISABLED) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        int sanitizedRefreshSeconds = Math.min(3600, Math.max(10, refreshSeconds));
        int sanitizedFontSize = Math.min(32, Math.max(6, fontSize));
        String normalizedMode = "dark".equalsIgnoreCase(mode) ? "dark" : "light";
        String normalizedFontColor = normalizeHexColor(fontColor);
        long activeOutages = outageService.countActiveOutages();
        boolean hasActiveIncidents = activeOutages > 0;

        model.addAttribute("refreshSeconds", sanitizedRefreshSeconds);
        model.addAttribute("mode", normalizedMode);
        model.addAttribute("fontSize", sanitizedFontSize);
        model.addAttribute("fontColor", normalizedFontColor);
        model.addAttribute("hasActiveIncidents", hasActiveIncidents);
        model.addAttribute("activeOutages", activeOutages);
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
            Page<CheckResult> historyPage = resourceService.getHistoryPage(
                    id,
                    sanitizedPage,
                    sanitizedSize,
                    statusFilter,
                    code,
                    message
            );

            List<Outage> filteredOutages = outageService.findByResource(resource).stream()
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

            model.addAttribute("vm", viewModel);
            model.addAttribute("uptime24h", uptime24h);
            model.addAttribute("uptime7d", uptime7d);
            model.addAttribute("uptime30d", uptime30d);
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
        ResourceGroup group = resource.getGroup();
        if (group == null) {
            return true;
        }

        ResourceGroupVisibility visibility = group.getVisibilityOrDefault();
        return switch (visibility) {
            case PUBLIC -> true;
            case AUTHENTICATED -> authenticated;
            case HIDDEN -> false;
        };
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
}

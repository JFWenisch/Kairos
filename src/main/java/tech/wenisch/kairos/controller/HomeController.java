package tech.wenisch.kairos.controller;

import tech.wenisch.kairos.dto.ResourceViewModel;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.service.AnnouncementService;
import tech.wenisch.kairos.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final ResourceService resourceService;
    private final AnnouncementService announcementService;

    @GetMapping("/")
    public String index(Model model) {
        List<MonitoredResource> resources = resourceService.findAllActive();

        List<ResourceViewModel> viewModels = resources.stream().map(resource -> {
            String currentStatus = resourceService.getCurrentStatus(resource);
            List<String> timelineBlocks = resourceService.getTimelineBlocks(resource);
            double uptime = resourceService.getUptimePercentage(resource, 24);

            return ResourceViewModel.builder()
                    .resource(resource)
                    .currentStatus(currentStatus)
                    .timelineBlocks(timelineBlocks)
                    .uptimePercentage(uptime)
                    .build();
        }).sorted(Comparator.comparing(vm -> {
            if ("not-available".equals(vm.getCurrentStatus())) return 0;
            if ("available".equals(vm.getCurrentStatus())) return 2;
            return 1;
        })).collect(Collectors.toList());

        model.addAttribute("resources", viewModels);
        model.addAttribute("announcements", announcementService.findAllActiveForPublicView());
        return "index";
    }

    @GetMapping("/announcements")
    public String announcements(Model model) {
        model.addAttribute("announcements", announcementService.findAllOrderedByCreatedAtDesc());
        return "announcements";
    }

        @GetMapping("/resources/{id}")
        public String detail(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String message,
            Model model
        ) {
        return resourceService.findById(id).map(resource -> {
            int sanitizedPage = Math.max(0, page);
            int sanitizedSize = normalizePageSize(size);
            CheckStatus statusFilter = parseStatus(status);

            String currentStatus = resourceService.getCurrentStatus(resource);
            List<String> timelineBlocks = resourceService.getTimelineBlocks(resource);
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

            ResourceViewModel viewModel = ResourceViewModel.builder()
                    .resource(resource)
                    .currentStatus(currentStatus)
                    .timelineBlocks(timelineBlocks)
                    .uptimePercentage(uptime24h)
                    .build();

            model.addAttribute("vm", viewModel);
            model.addAttribute("uptime24h", uptime24h);
            model.addAttribute("uptime7d", uptime7d);
            model.addAttribute("uptime30d", uptime30d);
            model.addAttribute("recentHistory", historyPage.getContent());
            model.addAttribute("historyPage", historyPage);
            model.addAttribute("historyStatus", statusFilter != null ? statusFilter.name() : "");
            model.addAttribute("historyCode", normalizeTextFilter(code));
            model.addAttribute("historyMessage", normalizeTextFilter(message));
            model.addAttribute("historySize", sanitizedSize);
            model.addAttribute("historyFrom", historyPage.getTotalElements() == 0 ? 0 : (long) historyPage.getNumber() * historyPage.getSize() + 1);
            model.addAttribute("historyTo", historyPage.getTotalElements() == 0 ? 0 : Math.min((long) (historyPage.getNumber() + 1) * historyPage.getSize(), historyPage.getTotalElements()));
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
}

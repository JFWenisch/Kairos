package com.jfwendisch.kairos.controller;

import com.jfwendisch.kairos.dto.ResourceViewModel;
import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.service.AnnouncementService;
import com.jfwendisch.kairos.service.ResourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Comparator;
import java.util.List;
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
    public String detail(@PathVariable Long id, Model model) {
        return resourceService.findById(id).map(resource -> {
            String currentStatus = resourceService.getCurrentStatus(resource);
            List<String> timelineBlocks = resourceService.getTimelineBlocks(resource);
            double uptime24h = resourceService.getUptimePercentage(resource, 24);
            double uptime7d = resourceService.getUptimePercentage(resource, 168);
            double uptime30d = resourceService.getUptimePercentage(resource, 720);

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
            model.addAttribute("recentHistory", resourceService.getHistory(id, 50));
            return "detail";
        }).orElse("redirect:/");
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}

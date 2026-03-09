package com.jfwendisch.kairos.controller;

import com.jfwendisch.kairos.entity.*;
import com.jfwendisch.kairos.repository.ResourceTypeConfigRepository;
import com.jfwendisch.kairos.service.ResourceService;
import com.jfwendisch.kairos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ResourceService resourceService;
    private final UserService userService;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    @GetMapping
    public String admin() {
        return "redirect:/admin/settings";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        boolean allowPublicAdd = configs.stream().anyMatch(ResourceTypeConfig::isAllowPublicAdd);
        model.addAttribute("allowPublicAdd", allowPublicAdd);
        model.addAttribute("configs", configs);
        return "admin/settings";
    }

    @PostMapping("/settings")
    public String saveSettings(@RequestParam(defaultValue = "false") boolean allowPublicAdd,
                               RedirectAttributes redirectAttributes) {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        for (ResourceTypeConfig config : configs) {
            config.setAllowPublicAdd(allowPublicAdd);
            resourceTypeConfigRepository.save(config);
        }
        redirectAttributes.addFlashAttribute("successMessage", "Settings saved successfully");
        return "redirect:/admin/settings";
    }

    @GetMapping("/resources")
    public String resources(Model model) {
        model.addAttribute("resources", resourceService.findAll());
        model.addAttribute("resourceTypes", ResourceType.values());
        return "admin/resources";
    }

    @PostMapping("/resources/add")
    public String addResource(@RequestParam String name,
                              @RequestParam ResourceType resourceType,
                              @RequestParam String target,
                              RedirectAttributes redirectAttributes) {
        MonitoredResource resource = MonitoredResource.builder()
                .name(name)
                .resourceType(resourceType)
                .target(target)
                .active(true)
                .build();
        resourceService.save(resource);
        redirectAttributes.addFlashAttribute("successMessage", "Resource added: " + name);
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
                                     RedirectAttributes redirectAttributes) {
        resourceTypeConfigRepository.findById(id).ifPresent(config -> {
            config.setCheckIntervalMinutes(checkIntervalMinutes);
            config.setParallelism(parallelism);
            resourceTypeConfigRepository.save(config);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Configuration updated");
        return "redirect:/admin/resource-types";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userService.findAll());
        return "admin/users";
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
}

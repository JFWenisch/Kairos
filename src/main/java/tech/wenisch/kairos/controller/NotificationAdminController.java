package tech.wenisch.kairos.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import tech.wenisch.kairos.entity.NotificationPolicy;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.NotificationProviderType;
import tech.wenisch.kairos.entity.NotificationScopeType;
import tech.wenisch.kairos.service.ApplicationVersionService;
import tech.wenisch.kairos.service.NotificationPolicyService;
import tech.wenisch.kairos.service.NotificationProviderService;
import tech.wenisch.kairos.service.ResourceGroupService;
import tech.wenisch.kairos.service.ResourceService;

import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
public class NotificationAdminController {

    private final NotificationProviderService notificationProviderService;
    private final NotificationPolicyService notificationPolicyService;
    private final ResourceGroupService resourceGroupService;
    private final ResourceService resourceService;
    private final ApplicationVersionService applicationVersionService;

    @ModelAttribute("currentRequestUri")
    public String currentRequestUri(HttpServletRequest request) {
        return request != null ? request.getRequestURI() : "";
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return applicationVersionService.getVersion();
    }

    // ── Providers ──────────────────────────────────────────────────────────────

    @GetMapping("/notification-providers")
    public String listProviders(Model model) {
        model.addAttribute("providers", notificationProviderService.findAll());
        return "admin/notification-providers";
    }

    @GetMapping("/notification-providers/new")
    public String newProviderForm(Model model) {
        model.addAttribute("provider", NotificationProvider.builder().smtpUseTls(true).build());
        model.addAttribute("providerTypes", Arrays.asList(NotificationProviderType.values()));
        model.addAttribute("editMode", false);
        return "admin/notification-provider-form";
    }

    @PostMapping("/notification-providers/new")
    public String createProvider(
            @RequestParam String name,
            @RequestParam NotificationProviderType type,
            @RequestParam(required = false) String smtpHost,
            @RequestParam(required = false) Integer smtpPort,
            @RequestParam(required = false) String smtpUsername,
            @RequestParam(required = false) String smtpPassword,
            @RequestParam(defaultValue = "false") boolean smtpUseTls,
            @RequestParam(required = false) String senderEmail,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String webhookUrl,
            @RequestParam(required = false) String webhookBodyTemplate,
            @RequestParam(required = false) String discordWebhookUrl,
            RedirectAttributes redirectAttributes) {
        NotificationProvider provider = NotificationProvider.builder()
                .name(name)
                .type(type)
                .smtpHost(nullIfBlank(smtpHost))
                .smtpPort(smtpPort)
                .smtpUsername(nullIfBlank(smtpUsername))
                .smtpPassword(nullIfBlank(smtpPassword))
                .smtpUseTls(smtpUseTls)
                .senderEmail(nullIfBlank(senderEmail))
                .recipientEmail(nullIfBlank(recipientEmail))
                .webhookUrl(nullIfBlank(webhookUrl))
                .webhookBodyTemplate(nullIfBlank(webhookBodyTemplate))
                .discordWebhookUrl(nullIfBlank(discordWebhookUrl))
                .build();
        notificationProviderService.save(provider);
        redirectAttributes.addFlashAttribute("successMessage", "Notification provider '" + name + "' created.");
        return "redirect:/admin/notification-providers";
    }

    @GetMapping("/notification-providers/edit/{id}")
    public String editProviderForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return notificationProviderService.findById(id).map(provider -> {
            model.addAttribute("provider", provider);
            model.addAttribute("providerTypes", Arrays.asList(NotificationProviderType.values()));
            model.addAttribute("editMode", true);
            return "admin/notification-provider-form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Provider not found.");
            return "redirect:/admin/notification-providers";
        });
    }

    @PostMapping("/notification-providers/edit/{id}")
    public String updateProvider(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam NotificationProviderType type,
            @RequestParam(required = false) String smtpHost,
            @RequestParam(required = false) Integer smtpPort,
            @RequestParam(required = false) String smtpUsername,
            @RequestParam(required = false) String smtpPassword,
            @RequestParam(defaultValue = "false") boolean smtpUseTls,
            @RequestParam(required = false) String senderEmail,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String webhookUrl,
            @RequestParam(required = false) String webhookBodyTemplate,
            @RequestParam(required = false) String discordWebhookUrl,
            RedirectAttributes redirectAttributes) {
        notificationProviderService.findById(id).ifPresent(provider -> {
            provider.setName(name);
            provider.setType(type);
            provider.setSmtpHost(nullIfBlank(smtpHost));
            provider.setSmtpPort(smtpPort);
            provider.setSmtpUsername(nullIfBlank(smtpUsername));
            provider.setSmtpPassword(nullIfBlank(smtpPassword));
            provider.setSmtpUseTls(smtpUseTls);
            provider.setSenderEmail(nullIfBlank(senderEmail));
            provider.setRecipientEmail(nullIfBlank(recipientEmail));
            provider.setWebhookUrl(nullIfBlank(webhookUrl));
            provider.setWebhookBodyTemplate(nullIfBlank(webhookBodyTemplate));
            provider.setDiscordWebhookUrl(nullIfBlank(discordWebhookUrl));
            notificationProviderService.save(provider);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Notification provider updated.");
        return "redirect:/admin/notification-providers";
    }

    @PostMapping("/notification-providers/delete/{id}")
    public String deleteProvider(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        notificationProviderService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Notification provider deleted (including associated policies).");
        return "redirect:/admin/notification-providers";
    }

    @PostMapping("/notification-providers/{id}/test")
    public String testProvider(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            notificationProviderService.test(id);
            redirectAttributes.addFlashAttribute("successMessage", "Test notification sent successfully.");
        } catch (Exception e) {
            log.warn("Test notification failed for provider {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Test notification failed: " + e.getMessage());
        }
        return "redirect:/admin/notification-providers";
    }

    // ── Policies ───────────────────────────────────────────────────────────────

    @GetMapping("/notification-policies")
    public String listPolicies(Model model) {
        model.addAttribute("policies", notificationPolicyService.findAll());
        return "admin/notification-policies";
    }

    @GetMapping("/notification-policies/new")
    public String newPolicyForm(Model model) {
        model.addAttribute("policy", NotificationPolicy.builder().build());
        model.addAttribute("providers", notificationProviderService.findAll());
        model.addAttribute("allGroups", resourceGroupService.findAllOrdered());
        model.addAttribute("allResources", resourceService.findAll());
        model.addAttribute("editMode", false);
        return "admin/notification-policy-form";
    }

    @PostMapping("/notification-policies/new")
    public String createPolicy(
            @RequestParam String name,
            @RequestParam Long providerId,
            @RequestParam(defaultValue = "false") boolean notifyOnOutageStarted,
            @RequestParam(defaultValue = "false") boolean notifyOnOutageEnded,
            @RequestParam(defaultValue = "ALL") NotificationScopeType scopeType,
            @RequestParam(value = "scopedGroupIds", required = false) List<Long> scopedGroupIds,
            @RequestParam(value = "scopedResourceIds", required = false) List<Long> scopedResourceIds,
            RedirectAttributes redirectAttributes) {
        NotificationPolicy policy = NotificationPolicy.builder()
                .name(name)
                .notifyOnOutageStarted(notifyOnOutageStarted)
                .notifyOnOutageEnded(notifyOnOutageEnded)
                .scopeType(scopeType)
                .build();
        notificationPolicyService.save(policy, providerId, scopedGroupIds, scopedResourceIds);
        redirectAttributes.addFlashAttribute("successMessage", "Notification policy '" + name + "' created.");
        return "redirect:/admin/notification-policies";
    }

    @GetMapping("/notification-policies/edit/{id}")
    public String editPolicyForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return notificationPolicyService.findById(id).map(policy -> {
            model.addAttribute("policy", policy);
            model.addAttribute("providers", notificationProviderService.findAll());
            model.addAttribute("allGroups", resourceGroupService.findAllOrdered());
            model.addAttribute("allResources", resourceService.findAll());
            model.addAttribute("editMode", true);
            return "admin/notification-policy-form";
        }).orElseGet(() -> {
            redirectAttributes.addFlashAttribute("errorMessage", "Policy not found.");
            return "redirect:/admin/notification-policies";
        });
    }

    @PostMapping("/notification-policies/edit/{id}")
    public String updatePolicy(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam Long providerId,
            @RequestParam(defaultValue = "false") boolean notifyOnOutageStarted,
            @RequestParam(defaultValue = "false") boolean notifyOnOutageEnded,
            @RequestParam(defaultValue = "ALL") NotificationScopeType scopeType,
            @RequestParam(value = "scopedGroupIds", required = false) List<Long> scopedGroupIds,
            @RequestParam(value = "scopedResourceIds", required = false) List<Long> scopedResourceIds,
            RedirectAttributes redirectAttributes) {
        notificationPolicyService.findById(id).ifPresent(policy -> {
            policy.setName(name);
            policy.setNotifyOnOutageStarted(notifyOnOutageStarted);
            policy.setNotifyOnOutageEnded(notifyOnOutageEnded);
            policy.setScopeType(scopeType);
            notificationPolicyService.save(policy, providerId, scopedGroupIds, scopedResourceIds);
        });
        redirectAttributes.addFlashAttribute("successMessage", "Notification policy updated.");
        return "redirect:/admin/notification-policies";
    }

    @PostMapping("/notification-policies/delete/{id}")
    public String deletePolicy(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        notificationPolicyService.delete(id);
        redirectAttributes.addFlashAttribute("successMessage", "Notification policy deleted.");
        return "redirect:/admin/notification-policies";
    }

    private String nullIfBlank(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}

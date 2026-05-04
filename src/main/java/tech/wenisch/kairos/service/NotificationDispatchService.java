package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationEvent;
import tech.wenisch.kairos.entity.NotificationPolicy;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.NotificationScopeType;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.repository.NotificationPolicyRepository;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatchService {

    private final NotificationPolicyRepository notificationPolicyRepository;
    private final EmailNotificationService emailNotificationService;
    private final WebhookNotificationService webhookNotificationService;
    private final DiscordNotificationService discordNotificationService;
    private final GitLabNotificationService gitLabNotificationService;

    @Transactional(readOnly = true)
    public void dispatch(NotificationEvent event, Outage outage, MonitoredResource resource) {
        List<NotificationPolicy> policies = switch (event) {
            case OUTAGE_STARTED -> notificationPolicyRepository.findAllByNotifyOnOutageStartedTrue();
            case OUTAGE_ENDED   -> notificationPolicyRepository.findAllByNotifyOnOutageEndedTrue();
        };

        for (NotificationPolicy policy : policies) {
            if (!matchesScope(policy, resource)) {
                continue;
            }
            NotificationProvider provider = policy.getProvider();
            try {
                switch (provider.getType()) {
                    case EMAIL   -> emailNotificationService.sendOutageNotification(provider, event, outage, resource);
                    case WEBHOOK -> webhookNotificationService.sendOutageNotification(provider, event, outage, resource);
                    case DISCORD -> discordNotificationService.sendOutageNotification(provider, event, outage, resource);
                    case GITLAB  -> gitLabNotificationService.sendOutageNotification(provider, event, outage, resource);
                }
            } catch (Exception e) {
                log.error("Notification dispatch failed for policy '{}' (provider '{}', event {}): {}",
                        policy.getName(), provider.getName(), event, e.getMessage());
            }
        }
    }

    public void testProvider(NotificationProvider provider) {
        switch (provider.getType()) {
            case EMAIL   -> emailNotificationService.sendTestNotification(provider);
            case WEBHOOK -> webhookNotificationService.sendTestNotification(provider);
            case DISCORD -> discordNotificationService.sendTestNotification(provider);
            case GITLAB  -> { try { gitLabNotificationService.sendTestNotification(provider); } catch (Exception e) { throw new RuntimeException(e); } }
        }
    }

    private boolean matchesScope(NotificationPolicy policy, MonitoredResource resource) {
        if (policy.getScopeType() == NotificationScopeType.ALL) {
            return true;
        }
        // SCOPED: resource is in scopedResources OR any resource group overlaps with scopedGroups
        if (policy.getScopedResources().contains(resource)) {
            return true;
        }
        Set<Long> policyGroupIds = policy.getScopedGroups().stream()
                .map(ResourceGroup::getId)
                .collect(Collectors.toSet());
        return resource.getGroups().stream()
                .anyMatch(g -> policyGroupIds.contains(g.getId()));
    }
}

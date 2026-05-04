package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationPolicy;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.NotificationScopeType;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.NotificationPolicyRepository;
import tech.wenisch.kairos.repository.NotificationProviderRepository;
import tech.wenisch.kairos.repository.ResourceGroupRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class NotificationPolicyService {

    private final NotificationPolicyRepository notificationPolicyRepository;
    private final NotificationProviderRepository notificationProviderRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final MonitoredResourceRepository monitoredResourceRepository;

    public List<NotificationPolicy> findAll() {
        return notificationPolicyRepository.findAll();
    }

    public Optional<NotificationPolicy> findById(Long id) {
        return notificationPolicyRepository.findById(id);
    }

    @Transactional
    public NotificationPolicy save(NotificationPolicy policy, Long providerId,
                                   List<Long> scopedGroupIds, List<Long> scopedResourceIds) {
        NotificationProvider provider = notificationProviderRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
        policy.setProvider(provider);

        Set<ResourceGroup> groups = new HashSet<>();
        Set<MonitoredResource> resources = new HashSet<>();
        if (policy.getScopeType() == NotificationScopeType.SCOPED) {
            if (scopedGroupIds != null) {
                groups.addAll(resourceGroupRepository.findAllById(scopedGroupIds));
            }
            if (scopedResourceIds != null) {
                resources.addAll(monitoredResourceRepository.findAllById(scopedResourceIds));
            }
        }
        policy.setScopedGroups(groups);
        policy.setScopedResources(resources);

        return notificationPolicyRepository.save(policy);
    }

    public void delete(Long id) {
        notificationPolicyRepository.deleteById(id);
    }
}

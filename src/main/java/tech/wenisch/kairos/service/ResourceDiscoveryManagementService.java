package tech.wenisch.kairos.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tech.wenisch.kairos.entity.DiscoveryServiceAuth;
import tech.wenisch.kairos.entity.DiscoveryServiceConfig;
import tech.wenisch.kairos.entity.ResourceDiscovery;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.CheckResultRepository;
import tech.wenisch.kairos.repository.DiscoveryServiceAuthRepository;
import tech.wenisch.kairos.repository.DiscoveryServiceConfigRepository;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.OutageRepository;
import tech.wenisch.kairos.repository.ResourceDiscoveryRepository;
import tech.wenisch.kairos.repository.ResourceGroupRepository;

@Service
@RequiredArgsConstructor
public class ResourceDiscoveryManagementService {

    private final ResourceDiscoveryRepository discoveryRepository;
    private final DiscoveryServiceConfigRepository configRepository;
    private final DiscoveryServiceAuthRepository authRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final MonitoredResourceRepository resourceRepository;
    private final CheckResultRepository checkResultRepository;
    private final OutageRepository outageRepository;

    // ── CRUD ────────────────────────────────────────────────────────────────

    public List<ResourceDiscovery> findAll() {
        return discoveryRepository.findAll();
    }

    public Optional<ResourceDiscovery> findById(Long id) {
        return discoveryRepository.findById(id);
    }

    @Transactional
    public ResourceDiscovery save(ResourceDiscovery discovery) {
        if (discovery.getCreatedAt() == null) {
            discovery.setCreatedAt(LocalDateTime.now());
        }
        ResourceDiscovery existing = null;
        if (discovery.getId() != null) {
            existing = discoveryRepository.findById(discovery.getId()).orElse(null);
        }
        ResourceDiscovery saved = discoveryRepository.save(discovery);
        renameManagedGroupIfNeeded(existing, saved);
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        discoveryRepository.findById(id).ifPresent(discovery -> {
            deleteManagedDockerResources(discovery);
            discoveryRepository.delete(discovery);
        });
    }

    public DiscoveryServiceConfig saveConfig(DiscoveryServiceConfig config) {
        return configRepository.save(config);
    }

    // ── Group management ────────────────────────────────────────────────────

    public ResourceGroup findOrCreateManagedGroup(ResourceDiscovery discovery) {
        String preferredGroupName = managedGroupName(discovery);
        Optional<ResourceGroup> existingPreferred = resourceGroupRepository.findByNameIgnoreCase(preferredGroupName);
        if (existingPreferred.isPresent()) {
            return existingPreferred.get();
        }

        for (String candidateName : managedGroupNames(discovery)) {
            if (candidateName.equalsIgnoreCase(preferredGroupName)) {
                continue;
            }
            Optional<ResourceGroup> candidateGroup = resourceGroupRepository.findByNameIgnoreCase(candidateName);
            if (candidateGroup.isPresent()) {
                ResourceGroup group = candidateGroup.get();
                group.setName(preferredGroupName);
                return resourceGroupRepository.save(group);
            }
        }

        return resourceGroupRepository.save(ResourceGroup.builder()
                .name(preferredGroupName)
                .displayOrder(0)
                .build());
    }

    public List<String> managedGroupNames(ResourceDiscovery discovery) {
        LinkedHashSet<String> groupNames = new LinkedHashSet<>();
        groupNames.add(managedGroupName(discovery));
        String legacyGroupName = legacyManagedGroupName(discovery.getTarget());
        if (!legacyGroupName.isBlank()) {
            groupNames.add(legacyGroupName);
        }
        return new ArrayList<>(groupNames);
    }

    private String managedGroupName(ResourceDiscovery discovery) {
        String name = discovery.getName() == null ? "" : discovery.getName().trim();
        if (!name.isBlank()) {
            return name;
        }
        String target = discovery.getTarget() == null ? "" : discovery.getTarget().trim();
        return target;
    }

    private String legacyManagedGroupName(String target) {
        String normalizedTarget = target == null ? "" : target.trim();
        return normalizedTarget.isBlank() ? "" : "Dockerrepository: " + normalizedTarget;
    }

    private void renameManagedGroupIfNeeded(ResourceDiscovery existing, ResourceDiscovery saved) {
        String preferredGroupName = managedGroupName(saved);
        Optional<ResourceGroup> existingPreferred = resourceGroupRepository.findByNameIgnoreCase(preferredGroupName);
        if (existingPreferred.isPresent()) {
            return;
        }

        LinkedHashSet<String> candidateNames = new LinkedHashSet<>(managedGroupNames(saved));
        if (existing != null) {
            candidateNames.addAll(managedGroupNames(existing));
        }

        for (String candidateName : candidateNames) {
            if (candidateName.equalsIgnoreCase(preferredGroupName)) {
                continue;
            }
            Optional<ResourceGroup> candidateGroup = resourceGroupRepository.findByNameIgnoreCase(candidateName);
            if (candidateGroup.isPresent()) {
                ResourceGroup group = candidateGroup.get();
                group.setName(preferredGroupName);
                resourceGroupRepository.save(group);
                return;
            }
        }
    }

    private void deleteManagedDockerResources(ResourceDiscovery discovery) {
        Set<Long> processedGroupIds = new HashSet<>();
        for (String groupName : managedGroupNames(discovery)) {
            resourceGroupRepository.findByNameIgnoreCase(groupName).ifPresent(group -> {
                if (!processedGroupIds.add(group.getId())) {
                    return;
                }
                var managedResources = resourceRepository
                        .findByGroups_IdAndResourceType(group.getId(), ResourceType.DOCKER);
                for (var resource : managedResources) {
                    var outages = outageRepository.findByResourceOrderByStartDateDesc(resource);
                    outageRepository.deleteAll(outages);
                    checkResultRepository.findByResourceOrderByCheckedAtDesc(resource)
                            .forEach(checkResultRepository::delete);
                    resourceRepository.delete(resource);
                }
                if (resourceRepository.findByGroups_Id(group.getId()).isEmpty()) {
                    resourceGroupRepository.delete(group);
                }
            });
        }
    }

    // ── Auth lookup ─────────────────────────────────────────────────────────

    /**
     * Finds the best-matching credential for the given target and discovery type name.
     */
    public Optional<DiscoveryServiceAuth> findMatchingAuth(String target, String typeName) {
        List<DiscoveryServiceAuth> auths = authRepository.findByDiscoveryServiceConfig_TypeName(typeName);
        return auths.stream()
                .filter(auth -> matchesPattern(target, auth.getUrlPattern()))
                .max((left, right) -> Integer.compare(
                        patternSpecificity(left.getUrlPattern()),
                        patternSpecificity(right.getUrlPattern())
                ));
    }

    private boolean matchesPattern(String target, String pattern) {
        if (pattern == null || target == null) {
            return false;
        }
        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        try {
            return target.matches(regex);
        } catch (Exception e) {
            return false;
        }
    }

    private int patternSpecificity(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return -1;
        }
        return pattern.replace("*", "").length();
    }

    // ── Config access ────────────────────────────────────────────────────────

    public List<DiscoveryServiceConfig> findAllConfigs() {
        return configRepository.findAll();
    }

    public Optional<DiscoveryServiceConfig> findConfigById(Long id) {
        return configRepository.findById(id);
    }
}

package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceGroupVisibility;
import tech.wenisch.kairos.repository.ResourceGroupRepository;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResourceGroupService {

    private final ResourceGroupRepository resourceGroupRepository;

    public List<ResourceGroup> findAllOrdered() {
        return resourceGroupRepository.findAllByOrderByDisplayOrderAscNameAsc();
    }

    public Optional<ResourceGroup> findById(Long id) {
        return resourceGroupRepository.findById(id);
    }

    public ResourceGroup save(ResourceGroup resourceGroup) {
        if (resourceGroup.getName() != null) {
            resourceGroup.setName(resourceGroup.getName().trim());
        }
        if (resourceGroup.getVisibility() == null) {
            resourceGroup.setVisibility(ResourceGroupVisibility.PUBLIC);
        }
        return resourceGroupRepository.save(resourceGroup);
    }

    public void delete(Long id) {
        resourceGroupRepository.deleteById(id);
    }
}

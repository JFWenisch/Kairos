package tech.wenisch.kairos.dto;

import lombok.Builder;
import lombok.Data;
import tech.wenisch.kairos.entity.ResourceGroup;

import java.util.List;

@Data
@Builder
public class ResourceGroupViewModel {
    private ResourceGroup group;
    private List<ResourceViewModel> resources;
    private long availableCount;
    private long downCount;
    private long unknownCount;

    public long getTotalCount() {
        return availableCount + downCount + unknownCount;
    }
}

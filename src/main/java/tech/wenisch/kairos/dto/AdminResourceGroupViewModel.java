package tech.wenisch.kairos.dto;

import lombok.Builder;
import lombok.Data;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceDiscovery;

import java.util.List;

@Data
@Builder
public class AdminResourceGroupViewModel {
    private Long groupId;
    private String groupName;
    private boolean ungrouped;
    private List<MonitoredResource> resources;
    private ResourceDiscovery resourceDiscovery;
}

package tech.wenisch.kairos.dto;

import tech.wenisch.kairos.entity.ResourceType;
import lombok.Data;

@Data
public class ResourceDTO {
    private String name;
    private ResourceType resourceType;
    private String target;
}

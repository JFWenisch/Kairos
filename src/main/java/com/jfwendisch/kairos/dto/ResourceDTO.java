package com.jfwendisch.kairos.dto;

import com.jfwendisch.kairos.entity.ResourceType;
import lombok.Data;

@Data
public class ResourceDTO {
    private String name;
    private ResourceType resourceType;
    private String target;
}

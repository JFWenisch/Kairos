package com.jfwendisch.kairos.dto;

import com.jfwendisch.kairos.entity.MonitoredResource;
import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ResourceViewModel {
    private MonitoredResource resource;
    private double uptimePercentage;
    private List<String> timelineBlocks;
    private String currentStatus;
}

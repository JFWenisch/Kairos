package tech.wenisch.kairos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceGroup;

import java.util.List;

/**
 * Lightweight shell used by the dashboard home page to pass group structure
 * without loading per-resource status data (status/timeline/uptime are
 * fetched client-side via the API).
 */
@Data
@AllArgsConstructor
public class DashboardGroupShell {
    private ResourceGroup group;
    private List<MonitoredResource> resources;
}

package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MonitoredResourceRepository extends JpaRepository<MonitoredResource, Long> {
    List<MonitoredResource> findByActiveTrue();
    List<MonitoredResource> findByResourceTypeAndActiveTrue(ResourceType resourceType);
}

package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MonitoredResourceRepository extends JpaRepository<MonitoredResource, Long> {
    @Query("""
            select distinct r from MonitoredResource r
            left join fetch r.groups g
            where r.active = true
                and r.resourceType in (
                    tech.wenisch.kairos.entity.ResourceType.HTTP,
                    tech.wenisch.kairos.entity.ResourceType.DOCKER
                )
            order by r.displayOrder asc,
                     lower(r.name) asc
            """)
    List<MonitoredResource> findAllActiveForLanding();

    @Query("""
            select distinct r from MonitoredResource r
            left join fetch r.groups g
            order by r.displayOrder asc,
                     lower(r.name) asc
            """)
    List<MonitoredResource> findAllForAdmin();

    List<MonitoredResource> findByResourceTypeAndActiveTrue(ResourceType resourceType);
    List<MonitoredResource> findByResourceType(ResourceType resourceType);
    List<MonitoredResource> findByGroups_Id(Long groupId);
    List<MonitoredResource> findByGroups_IdAndResourceType(Long groupId, ResourceType resourceType);
}

package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.DiscoveryServiceType;
import tech.wenisch.kairos.entity.ResourceDiscovery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceDiscoveryRepository extends JpaRepository<ResourceDiscovery, Long> {
    List<ResourceDiscovery> findByTypeAndActiveTrue(DiscoveryServiceType type);
}

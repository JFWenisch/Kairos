package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.DiscoveryServiceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DiscoveryServiceConfigRepository extends JpaRepository<DiscoveryServiceConfig, Long> {
    Optional<DiscoveryServiceConfig> findByTypeName(String typeName);
}

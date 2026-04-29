package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.DiscoveryServiceAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DiscoveryServiceAuthRepository extends JpaRepository<DiscoveryServiceAuth, Long> {
    List<DiscoveryServiceAuth> findByDiscoveryServiceConfig_TypeName(String typeName);
}

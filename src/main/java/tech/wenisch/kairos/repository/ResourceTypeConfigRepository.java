package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.ResourceTypeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface ResourceTypeConfigRepository extends JpaRepository<ResourceTypeConfig, Long> {
    Optional<ResourceTypeConfig> findByTypeName(String typeName);
}

package tech.wenisch.kairos.repository;

import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResourceTypeAuthRepository extends JpaRepository<ResourceTypeAuth, Long> {

    List<ResourceTypeAuth> findByResourceTypeConfig(ResourceTypeConfig config);

    List<ResourceTypeAuth> findByResourceTypeConfig_TypeName(String typeName);
}

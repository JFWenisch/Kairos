package tech.wenisch.kairos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tech.wenisch.kairos.entity.ResourceGroup;

import java.util.List;

@Repository
public interface ResourceGroupRepository extends JpaRepository<ResourceGroup, Long> {
    List<ResourceGroup> findAllByOrderByDisplayOrderAscNameAsc();
    java.util.Optional<ResourceGroup> findByNameIgnoreCase(String name);
}

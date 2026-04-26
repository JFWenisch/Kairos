package tech.wenisch.kairos.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceGroupVisibility;
import tech.wenisch.kairos.entity.ResourceType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MonitoredResourceRepositoryTest {

    @Autowired
    private MonitoredResourceRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findAllActiveForLandingReturnsUniqueResourcesWithGroupsLoaded() {
        ResourceGroup publicGroup = entityManager.merge(ResourceGroup.builder()
                .name("Public")
                .displayOrder(1)
                .visibility(ResourceGroupVisibility.PUBLIC)
                .build());
        ResourceGroup authGroup = entityManager.merge(ResourceGroup.builder()
                .name("Authenticated")
                .displayOrder(2)
                .visibility(ResourceGroupVisibility.AUTHENTICATED)
                .build());

        MonitoredResource api = MonitoredResource.builder()
                .name("Api")
                .resourceType(ResourceType.HTTP)
                .target("https://example.test")
                .active(true)
                .displayOrder(5)
                .build();
        api.getGroups().add(publicGroup);
        api.getGroups().add(authGroup);
        entityManager.persist(api);

        MonitoredResource inactive = MonitoredResource.builder()
                .name("Inactive")
                .resourceType(ResourceType.HTTP)
                .target("https://inactive.test")
                .active(false)
                .displayOrder(1)
                .build();
        entityManager.persist(inactive);

        entityManager.flush();
        entityManager.clear();

        List<MonitoredResource> resources = repository.findAllActiveForLanding();
        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        MonitoredResource resource = resources.get(0);

        assertThat(resources).hasSize(1);
        assertThat(resource.getName()).isEqualTo("Api");
        assertThat(resource.getGroups())
                .extracting(ResourceGroup::getName)
                .containsExactlyInAnyOrder("Public", "Authenticated");
        assertThat(persistenceUnitUtil.isLoaded(resource, "groups")).isTrue();
    }
}
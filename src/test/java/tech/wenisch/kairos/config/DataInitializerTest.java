package tech.wenisch.kairos.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import tech.wenisch.kairos.entity.AppUser;
import tech.wenisch.kairos.entity.AuthProvider;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.entity.UserRole;
import tech.wenisch.kairos.repository.AppUserRepository;
import tech.wenisch.kairos.repository.DiscoveryServiceConfigRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DataInitializerTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private ResourceTypeConfigRepository resourceTypeConfigRepository;

    @Autowired
    private DiscoveryServiceConfigRepository discoveryServiceConfigRepository;

    @Test
    void runSeedsDockerConfigWithFiveMinuteDefault() {
        DataInitializer initializer = initializerWithExistingUser();

        initializer.run(new DefaultApplicationArguments(new String[0]));

        Optional<ResourceTypeConfig> dockerConfig = resourceTypeConfigRepository.findByTypeName("DOCKER");
        assertThat(dockerConfig).isPresent();
        assertThat(dockerConfig.orElseThrow().getCheckIntervalMinutes())
                .isEqualTo(DataInitializer.DEFAULT_DOCKER_CHECK_INTERVAL_MINUTES);
    }

    @Test
    void runMigratesLegacyDockerDefaultToFiveMinutes() {
        appUserRepository.save(AppUser.builder()
                .email("existing@example.test")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .provider(AuthProvider.LOCAL)
                .build());
        resourceTypeConfigRepository.save(ResourceTypeConfig.builder()
                .typeName("DOCKER")
                .checkIntervalMinutes(DataInitializer.LEGACY_DOCKER_CHECK_INTERVAL_MINUTES)
                .parallelism(2)
                .build());

        DataInitializer initializer = new DataInitializer(
                appUserRepository,
                null,
                resourceTypeConfigRepository,
                discoveryServiceConfigRepository);

        initializer.run(new DefaultApplicationArguments(new String[0]));

        ResourceTypeConfig dockerConfig = resourceTypeConfigRepository.findByTypeName("DOCKER").orElseThrow();
        assertThat(dockerConfig.getCheckIntervalMinutes())
                .isEqualTo(DataInitializer.DEFAULT_DOCKER_CHECK_INTERVAL_MINUTES);
    }

    private DataInitializer initializerWithExistingUser() {
        appUserRepository.save(AppUser.builder()
                .email("existing@example.test")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .provider(AuthProvider.LOCAL)
                .build());

        return new DataInitializer(
                appUserRepository,
                null,
                resourceTypeConfigRepository,
                discoveryServiceConfigRepository);
    }
}
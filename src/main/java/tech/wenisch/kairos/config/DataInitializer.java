package tech.wenisch.kairos.config;

import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.entity.DiscoveryServiceConfig;
import tech.wenisch.kairos.repository.AppUserRepository;
import tech.wenisch.kairos.repository.DiscoveryServiceConfigRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;
import tech.wenisch.kairos.service.UserService;
import tech.wenisch.kairos.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    static final int DEFAULT_DOCKER_CHECK_INTERVAL_MINUTES = 5;
    static final int LEGACY_DOCKER_CHECK_INTERVAL_MINUTES = 3600;

    private final AppUserRepository userRepository;
    private final UserService userService;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;
    private final DiscoveryServiceConfigRepository discoveryServiceConfigRepository;

    @Override
    public void run(ApplicationArguments args) {
        initDefaultAdmin();
        initResourceTypeConfigs();
    }

    private void initDefaultAdmin() {
        if (userRepository.count() == 0) {
            log.warn("No users found. Creating default admin user: admin@kairos.local");
            log.warn("*** SECURITY WARNING: Change the default admin password immediately! ***");
            userService.createUser("admin@kairos.local", "admin", UserRole.ADMIN);
        }
    }

    private void initResourceTypeConfigs() {
        // Migrate legacy config name "URL" -> "HTTP" used by older versions.
        resourceTypeConfigRepository.findByTypeName("URL").ifPresent(legacyUrlConfig -> {
            if (resourceTypeConfigRepository.findByTypeName("HTTP").isPresent()) {
                resourceTypeConfigRepository.delete(legacyUrlConfig);
                log.info("Removed legacy ResourceTypeConfig 'URL' because 'HTTP' already exists");
            } else {
                legacyUrlConfig.setTypeName("HTTP");
                resourceTypeConfigRepository.save(legacyUrlConfig);
                log.info("Migrated ResourceTypeConfig from 'URL' to 'HTTP'");
            }
        });

        if (resourceTypeConfigRepository.findByTypeName("HTTP").isEmpty()) {
            resourceTypeConfigRepository.save(ResourceTypeConfig.builder()
                    .typeName("HTTP")
                    .checkIntervalMinutes(1)
                    .parallelism(5)
                    .allowPublicAdd(false)
                    .checkHistoryRetentionEnabled(true)
                    .checkHistoryRetentionIntervalMinutes(60)
                    .checkHistoryRetentionDays(31)
                    .outageRetentionEnabled(true)
                    .outageRetentionIntervalHours(12)
                    .outageRetentionDays(31)
                    .build());
            log.info("Created default ResourceTypeConfig for HTTP");
        }
        if (resourceTypeConfigRepository.findByTypeName("DOCKER").isEmpty()) {
            resourceTypeConfigRepository.save(ResourceTypeConfig.builder()
                    .typeName("DOCKER")
                    .checkIntervalMinutes(DEFAULT_DOCKER_CHECK_INTERVAL_MINUTES)
                    .parallelism(2)
                    .allowPublicAdd(false)
                    .checkHistoryRetentionEnabled(true)
                    .checkHistoryRetentionIntervalMinutes(60)
                    .checkHistoryRetentionDays(31)
                    .outageRetentionEnabled(true)
                    .outageRetentionIntervalHours(12)
                    .outageRetentionDays(31)
                    .build());
            log.info("Created default ResourceTypeConfig for DOCKER");
        }
        resourceTypeConfigRepository.findByTypeName("DOCKER").ifPresent(dockerConfig -> {
            if (dockerConfig.getCheckIntervalMinutes() == LEGACY_DOCKER_CHECK_INTERVAL_MINUTES) {
                dockerConfig.setCheckIntervalMinutes(DEFAULT_DOCKER_CHECK_INTERVAL_MINUTES);
                resourceTypeConfigRepository.save(dockerConfig);
                log.info("Updated legacy DOCKER check interval default from {} to {} minutes",
                        LEGACY_DOCKER_CHECK_INTERVAL_MINUTES,
                        DEFAULT_DOCKER_CHECK_INTERVAL_MINUTES);
            }
        });

        resourceTypeConfigRepository.findByTypeName("DOCKERREPOSITORY").ifPresent(resourceTypeConfigRepository::delete);

        if (discoveryServiceConfigRepository.findByTypeName("DOCKER_REPOSITORY").isEmpty()) {
            discoveryServiceConfigRepository.save(DiscoveryServiceConfig.builder()
                    .typeName("DOCKER_REPOSITORY")
                    .syncIntervalMinutes(60)
                    .parallelism(1)
                    .build());
            log.info("Created default DiscoveryServiceConfig for DOCKER_REPOSITORY");
        }
    }
}

package com.jfwendisch.kairos.config;

import com.jfwendisch.kairos.entity.ResourceTypeConfig;
import com.jfwendisch.kairos.repository.AppUserRepository;
import com.jfwendisch.kairos.repository.ResourceTypeConfigRepository;
import com.jfwendisch.kairos.service.UserService;
import com.jfwendisch.kairos.entity.UserRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final UserService userService;
    private final ResourceTypeConfigRepository resourceTypeConfigRepository;

    @Override
    public void run(ApplicationArguments args) {
        initDefaultAdmin();
        initResourceTypeConfigs();
    }

    private void initDefaultAdmin() {
        if (userRepository.count() == 0) {
            log.info("Creating default admin user: admin@kairos.local / admin");
            userService.createUser("admin@kairos.local", "admin", UserRole.ADMIN);
        }
    }

    private void initResourceTypeConfigs() {
        if (resourceTypeConfigRepository.findByTypeName("URL").isEmpty()) {
            resourceTypeConfigRepository.save(ResourceTypeConfig.builder()
                    .typeName("URL")
                    .checkIntervalMinutes(1)
                    .parallelism(5)
                    .allowPublicAdd(false)
                    .build());
            log.info("Created default ResourceTypeConfig for URL");
        }
        if (resourceTypeConfigRepository.findByTypeName("DOCKER").isEmpty()) {
            resourceTypeConfigRepository.save(ResourceTypeConfig.builder()
                    .typeName("DOCKER")
                    .checkIntervalMinutes(3600)
                    .parallelism(2)
                    .allowPublicAdd(false)
                    .build());
            log.info("Created default ResourceTypeConfig for DOCKER");
        }
    }
}

package tech.wenisch.kairos.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

@Service
public class ApplicationVersionService {

    private final String configuredVersion;
    @Nullable
    private final BuildProperties buildProperties;

    public ApplicationVersionService(
            @Value("${app.version:unknown}") String configuredVersion,
            @Nullable BuildProperties buildProperties
    ) {
        this.configuredVersion = configuredVersion;
        this.buildProperties = buildProperties;
    }

    public String getVersion() {
        if (buildProperties != null && hasText(buildProperties.getVersion())) {
            return buildProperties.getVersion();
        }

        String manifestVersion = ApplicationVersionService.class
                .getPackage()
                .getImplementationVersion();
        if (hasText(manifestVersion)) {
            return manifestVersion;
        }

        if (hasText(configuredVersion) && !isUnresolvedPlaceholder(configuredVersion)) {
            return configuredVersion;
        }

        return "unknown";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean isUnresolvedPlaceholder(String value) {
        return value.startsWith("@") && value.endsWith("@");
    }
}

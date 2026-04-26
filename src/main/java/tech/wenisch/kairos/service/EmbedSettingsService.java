package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.EmbedAllowedOrigin;
import tech.wenisch.kairos.entity.EmbedPolicy;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.EmbedAllowedOriginRepository;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmbedSettingsService {

    private final ResourceTypeConfigRepository resourceTypeConfigRepository;
    private final EmbedAllowedOriginRepository embedAllowedOriginRepository;

    public EmbedPolicy getPolicy() {
        return resourceTypeConfigRepository.findAll().stream()
                .map(ResourceTypeConfig::getEmbedPolicy)
                .findFirst()
                .map(EmbedPolicy::fromValue)
                .orElse(EmbedPolicy.ALLOW_ALL);
    }

    @Transactional
    public void setPolicyForAllResourceTypes(EmbedPolicy policy) {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        for (ResourceTypeConfig config : configs) {
            config.setEmbedPolicy(policy.name());
            resourceTypeConfigRepository.save(config);
        }
    }

    public List<EmbedAllowedOrigin> listAllowedOrigins() {
        return embedAllowedOriginRepository.findAll().stream()
                .sorted(Comparator.comparing(EmbedAllowedOrigin::getOrigin, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public boolean addAllowedOrigin(String rawOrigin) {
        String normalized = normalizeOrigin(rawOrigin);
        if (embedAllowedOriginRepository.existsByOrigin(normalized)) {
            return false;
        }
        embedAllowedOriginRepository.save(EmbedAllowedOrigin.builder().origin(normalized).build());
        return true;
    }

    public void removeAllowedOrigin(Long id) {
        embedAllowedOriginRepository.deleteById(id);
    }

    public String normalizeOrigin(String rawOrigin) {
        if (rawOrigin == null) {
            throw new IllegalArgumentException("Origin is required.");
        }

        String trimmed = rawOrigin.trim();
        if (trimmed.isBlank()) {
            throw new IllegalArgumentException("Origin is required.");
        }

        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            if (scheme == null || host == null) {
                throw new IllegalArgumentException("Invalid origin. Use format: https://example.com");
            }

            String lowerScheme = scheme.toLowerCase();
            if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
                throw new IllegalArgumentException("Invalid origin. Only http:// and https:// are supported.");
            }

            int port = uri.getPort();
            String authority = port > -1 ? host + ":" + port : host;
            return lowerScheme + "://" + authority;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("Invalid origin. Use format: https://example.com");
        }
    }

    public String frameAncestorsDirective() {
        EmbedPolicy policy = getPolicy();
        if (policy == EmbedPolicy.DISABLED) {
            return "'none'";
        }
        if (policy == EmbedPolicy.ALLOW_ALL) {
            return "*";
        }

        List<String> origins = listAllowedOrigins().stream()
                .map(EmbedAllowedOrigin::getOrigin)
                .toList();
        if (origins.isEmpty()) {
            return "'none'";
        }
        return "'self' " + String.join(" ", origins);
    }
}

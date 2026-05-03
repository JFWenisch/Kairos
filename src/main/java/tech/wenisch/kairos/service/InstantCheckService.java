package tech.wenisch.kairos.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tech.wenisch.kairos.dto.InstantCheckExecutionResult;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeConfig;
import tech.wenisch.kairos.repository.ResourceTypeConfigRepository;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class InstantCheckService {

    private final ResourceTypeConfigRepository resourceTypeConfigRepository;
    private final HttpCheckService httpCheckService;
    private final DockerCheckService dockerCheckService;
    private final TcpCheckService tcpCheckService;

    public InstantCheckSettings getSettings() {
        List<ResourceTypeConfig> configs = resourceTypeConfigRepository.findAll();
        if (configs.isEmpty()) {
            return new InstantCheckSettings(false, false, false, "*");
        }

        ResourceTypeConfig first = configs.get(0);
        String allowedDomains = first.getInstantCheckAllowedDomains();
        if (allowedDomains == null || allowedDomains.isBlank()) {
            allowedDomains = "*";
        }

        return new InstantCheckSettings(
                configs.stream().anyMatch(ResourceTypeConfig::isInstantCheckEnabled),
                configs.stream().anyMatch(ResourceTypeConfig::isInstantCheckAllowPublic),
                configs.stream().anyMatch(ResourceTypeConfig::isInstantCheckUseStoredAuth),
                allowedDomains
        );
    }

    public boolean isAllowedForRequest(Authentication authentication) {
        InstantCheckSettings settings = getSettings();
        return settings.enabled() && (settings.allowPublic() || isAuthenticated(authentication));
    }

    public boolean isTargetAllowed(String target, String allowedDomainPatterns) {
        List<String> patterns = parseAllowedPatterns(allowedDomainPatterns);
        if (patterns.isEmpty()) {
            return false;
        }

        Set<String> candidates = buildTargetCandidates(target);
        for (String pattern : patterns) {
            if ("*".equals(pattern)) {
                return true;
            }
            for (String candidate : candidates) {
                if (matchesPattern(candidate, pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    public InstantCheckExecutionResult runInstantCheck(ResourceType resourceType,
                                                       String target,
                                                       boolean skipTls,
                                                       boolean useStoredAuth) {
        if (resourceType == ResourceType.DOCKER) {
            return dockerCheckService.probe(target, skipTls, useStoredAuth);
        }
        if (resourceType == ResourceType.TCP) {
            return tcpCheckService.probe(target, skipTls, useStoredAuth);
        }
        return httpCheckService.probe(target, skipTls, useStoredAuth);
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private List<String> parseAllowedPatterns(String raw) {
        String normalized = raw == null ? "" : raw.trim();
        if (normalized.isBlank()) {
            return List.of("*");
        }

        List<String> patterns = normalized.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();

        return patterns.isEmpty() ? List.of("*") : patterns;
    }

    private Set<String> buildTargetCandidates(String target) {
        String raw = target == null ? "" : target.trim();
        Set<String> candidates = new LinkedHashSet<>();
        if (raw.isBlank()) {
            return candidates;
        }

        candidates.add(raw);

        int slashIdx = raw.indexOf('/');
        if (slashIdx > 0) {
            candidates.add(raw.substring(0, slashIdx));
        }

        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                String normalizedHost = host.toLowerCase(Locale.ROOT);
                String path = uri.getPath();
                if (path == null || path.isBlank()) {
                    path = "/";
                }

                candidates.add(normalizedHost);
                candidates.add(normalizedHost + path);

                if (uri.getScheme() != null && !uri.getScheme().isBlank()) {
                    String schemeHost = uri.getScheme().toLowerCase(Locale.ROOT) + "://" + normalizedHost;
                    candidates.add(schemeHost);
                    candidates.add(schemeHost + path);
                }
            }
        } catch (Exception ignored) {
            // Keep raw candidates for non-URL targets (for example Docker image references).
        }

        return candidates;
    }

    private boolean matchesPattern(String target, String pattern) {
        if (target == null || pattern == null) {
            return false;
        }

        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        try {
            return target.matches(regex);
        } catch (Exception ignored) {
            return false;
        }
    }

    public record InstantCheckSettings(
            boolean enabled,
            boolean allowPublic,
            boolean useStoredAuth,
            String allowedDomains
    ) {
    }
}

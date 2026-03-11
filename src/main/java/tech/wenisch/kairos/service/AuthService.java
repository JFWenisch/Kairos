package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.repository.ResourceTypeAuthRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Resolves authentication credentials for a given resource target URL.
 * <p>
 * Credentials are stored per resource-type (e.g. HTTP, DOCKER) and may include a
 * wildcard ({@code *}) at the end of the URL pattern to match a prefix.
 * </p>
 * <p>
 * Matching examples:
 * <ul>
 *   <li>Pattern {@code https://registry.example.com} matches only that exact target.</li>
 *   <li>Pattern {@code https://registry.example.com*} matches any target that starts
 *       with {@code https://registry.example.com}.</li>
 * </ul>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final ResourceTypeAuthRepository authRepository;

    /**
     * Finds the first credential whose URL pattern matches {@code target} within the
     * given resource-type scope.
     *
     * @param target   the resource target (full URL or Docker image reference)
     * @param typeName the resource type name (e.g. "HTTP" or "DOCKER")
     * @return an {@link Optional} containing the matching auth, or empty if none match
     */
    public Optional<ResourceTypeAuth> findMatchingAuth(String target, String typeName) {
        List<ResourceTypeAuth> auths = authRepository.findByResourceTypeConfig_TypeName(typeName);
        return auths.stream()
                .filter(auth -> matchesPattern(target, auth.getUrlPattern()))
                .findFirst();
    }

    /**
     * Checks whether {@code target} matches {@code pattern}.
     * The only supported wildcard is a trailing {@code *}, which matches any suffix.
     * Without a wildcard the check is an exact string comparison.
     */
    private boolean matchesPattern(String target, String pattern) {
        if (pattern == null || target == null) {
            return false;
        }
        // Convert the glob pattern to a regex: escape everything, then replace * placeholder
        String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
        try {
            return target.matches(regex);
        } catch (Exception e) {
            log.warn("Invalid auth URL pattern '{}': {}", pattern, e.getMessage());
            return false;
        }
    }
}

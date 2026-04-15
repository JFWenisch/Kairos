package tech.wenisch.kairos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerRepositorySyncService {

    private static final int PAGE_SIZE = 100;
    private static final String USER_AGENT = "Kairos-DockerRepositorySync/1.0";

    private final ResourceService resourceService;
    private final MonitoredResourceRepository resourceRepository;
    private final AuthService authService;
    private final DockerCheckService dockerCheckService;
    private final ResourceStatusStreamService resourceStatusStreamService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = createDefaultHttpClient();
    private final HttpClient insecureHttpClient = createInsecureHttpClient();

    @Transactional
    public void sync(MonitoredResource sourceResource) {
        RepositoryRef repositoryRef = parseRepositoryRef(sourceResource.getTarget());
        Set<String> discoveredRepositories = discoverRepositories(repositoryRef, sourceResource);

        ResourceGroup targetGroup = resourceService.findOrCreateManagedDockerGroup(sourceResource);

        List<MonitoredResource> existingResources = resourceRepository
                .findByGroup_IdAndResourceType(targetGroup.getId(), ResourceType.DOCKER);

        Map<String, MonitoredResource> existingByTarget = new HashMap<>();
        for (MonitoredResource existing : existingResources) {
            if (existing.getTarget() != null) {
                existingByTarget.put(existing.getTarget().trim().toLowerCase(Locale.ROOT), existing);
            }
        }

        List<String> sortedRepositories = discoveredRepositories.stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        Set<String> desiredTargets = new HashSet<>();
        int displayOrder = 0;

        for (String repositoryPath : sortedRepositories) {
            String normalizedRepositoryPath = normalizeImageRepositoryPath(repositoryRef, repositoryPath);
            Optional<ResourceTypeAuth> auth = resolveAuthForRepository(repositoryRef, sourceResource.getTarget());
            
            Set<String> discoveredTags = discoverTagsForRepository(repositoryRef, normalizedRepositoryPath, auth, sourceResource);
            if (discoveredTags.isEmpty()) {
                log.debug("No tags discovered for repository '{}', skipping", normalizedRepositoryPath);
                continue;
            }

            for (String tag : discoveredTags.stream().sorted().toList()) {
                String imageTarget = repositoryRef.registry() + "/" + normalizedRepositoryPath + ":" + tag;
                String targetKey = imageTarget.toLowerCase(Locale.ROOT);
                desiredTargets.add(targetKey);

                String imageName = displayName(repositoryRef, normalizedRepositoryPath) + ":" + tag;
                MonitoredResource resource = existingByTarget.get(targetKey);
                boolean isNew = resource == null;
                if (resource == null) {
                    resource = MonitoredResource.builder()
                            .name(imageName)
                            .resourceType(ResourceType.DOCKER)
                            .target(imageTarget)
                            .skipTls(sourceResource.isSkipTls())
                            .recursive(false)
                            .group(targetGroup)
                            .displayOrder(displayOrder)
                            .active(true)
                            .build();
                } else {
                    resource.setName(imageName);
                    resource.setTarget(imageTarget);
                    resource.setResourceType(ResourceType.DOCKER);
                    resource.setSkipTls(sourceResource.isSkipTls());
                    resource.setGroup(targetGroup);
                    resource.setDisplayOrder(displayOrder);
                    resource.setActive(true);
                }
                MonitoredResource saved = resourceService.save(resource);

                if (isNew) {
                    try {
                        resourceStatusStreamService.publishResourceChecking(saved);
                        dockerCheckService.check(saved);
                    } catch (Exception ex) {
                        log.warn("Immediate check for newly created Docker resource '{}' failed: {}",
                                saved.getTarget(), ex.getMessage());
                    }
                }

                displayOrder++;
            }
        }

        for (MonitoredResource existing : existingResources) {
            String key = existing.getTarget() == null ? "" : existing.getTarget().trim().toLowerCase(Locale.ROOT);
            if (!desiredTargets.contains(key)) {
                resourceService.delete(existing.getId());
            }
        }

        log.info("Dockerrepository sync finished for '{}' (created/updated: {}, removed: {})",
                sourceResource.getTarget(), sortedRepositories.size(), Math.max(0, existingResources.size() - sortedRepositories.size()));
    }

    private Set<String> discoverRepositories(RepositoryRef repositoryRef, MonitoredResource sourceResource) {
        Set<String> repositories = new LinkedHashSet<>();

        try {
            repositories.addAll(discoverViaRegistryCatalog(repositoryRef, sourceResource));
        } catch (Exception ex) {
            log.debug("Registry catalog discovery failed for '{}': {}", repositoryRef.originalInput(), ex.getMessage());
        }

        if (repositories.isEmpty() && "ghcr.io".equalsIgnoreCase(repositoryRef.registry())) {
            try {
                repositories.addAll(discoverViaGithubApi(repositoryRef, sourceResource));
            } catch (Exception ex) {
                log.warn("GitHub package discovery failed for '{}': {}", repositoryRef.originalInput(), ex.getMessage());
            }
        }

        if (repositories.isEmpty()) {
            log.warn("No repositories discovered for Dockerrepository target '{}'", repositoryRef.originalInput());
        }

        return repositories;
    }

    private Set<String> discoverTagsForRepository(RepositoryRef repositoryRef,
                                                   String repositoryName,
                                                   Optional<ResourceTypeAuth> auth,
                                                   MonitoredResource sourceResource) {
        Set<String> tags = new LinkedHashSet<>();
        try {
            String url = "https://" + repositoryRef.registry() + "/v2/" + repositoryName + "/tags/list?n=" + PAGE_SIZE;
            HttpResponse<String> response = sendGet(url, auth, sourceResource);

            if (response.statusCode() == 404) {
                log.debug("Repository '{}' not found (404), skipping", repositoryName);
                return tags;
            }

            if (response.statusCode() != 200) {
                log.warn("Failed to discover tags for '{}': HTTP {}", repositoryName, response.statusCode());
                return tags;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode tagList = root.path("tags");

            if (tagList.isArray()) {
                for (JsonNode tagNode : tagList) {
                    String tag = tagNode.asText();
                    if (tag != null && !tag.isBlank()) {
                        tags.add(tag);
                    }
                }
            }

            if (tags.isEmpty()) {
                log.debug("No tags found for repository '{}'", repositoryName);
            } else {
                log.debug("Discovered {} tags for repository '{}'", tags.size(), repositoryName);
            }
        } catch (Exception ex) {
            log.debug("Tag discovery failed for repository '{}': {}", repositoryName, ex.getMessage());
        }

        return tags;
    }

    private Set<String> discoverViaRegistryCatalog(RepositoryRef repositoryRef, MonitoredResource sourceResource) throws Exception {
        Set<String> matches = new LinkedHashSet<>();
        Optional<ResourceTypeAuth> auth = resolveAuthForRepository(repositoryRef, sourceResource.getTarget());

        // 1) Standard registry catalog root: /v2/_catalog
        collectFromCatalogEndpoint(matches, auth, repositoryRef, sourceResource,
                "https://" + repositoryRef.registry() + "/v2/_catalog?n=" + PAGE_SIZE,
                false);

        // 2) Path-scoped registry catalog (e.g. Artifactory repo key): /<prefix>/v2/_catalog
        // Some registries return repository names relative to this prefix.
        collectFromCatalogEndpoint(matches, auth, repositoryRef, sourceResource,
                "https://" + repositoryRef.registry() + "/" + repositoryRef.path() + "/v2/_catalog?n=" + PAGE_SIZE,
                true);

        // 3) Artifactory Docker API catalog fallback:
        //    https://<host>/artifactory/api/docker/<repo-key>/v2/_catalog
        collectFromArtifactoryCatalog(matches, auth, repositoryRef, sourceResource);

        return matches;
    }

    private void collectFromArtifactoryCatalog(Set<String> matches,
                                               Optional<ResourceTypeAuth> auth,
                                               RepositoryRef repositoryRef,
                                               MonitoredResource sourceResource) {
        String path = repositoryRef.path();
        int firstSlash = path.indexOf('/');
        if (firstSlash < 0) {
            return;
        }

        String prefix = path.substring(0, firstSlash);
        String repoKey = path.substring(firstSlash + 1);
        if (!"artifactory".equalsIgnoreCase(prefix) || repoKey.isBlank()) {
            return;
        }

        collectFromCatalogEndpoint(matches, auth, repositoryRef, sourceResource,
                "https://" + repositoryRef.registry() + "/artifactory/api/docker/" + repoKey + "/v2/_catalog?n=" + PAGE_SIZE,
                true);
    }

    private void collectFromCatalogEndpoint(Set<String> matches,
                                            Optional<ResourceTypeAuth> auth,
                                            RepositoryRef repositoryRef,
                                            MonitoredResource sourceResource,
                                            String firstUrl,
                                            boolean scopedToPrefix) {
        try {
            String nextUrl = firstUrl;
            int safetyCounter = 0;

            while (nextUrl != null && safetyCounter < 100) {
                HttpResponse<String> response = sendGet(nextUrl, auth, sourceResource);
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("Catalog endpoint responded with HTTP " + response.statusCode());
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode repositories = root.path("repositories");
                if (repositories.isArray()) {
                    for (JsonNode item : repositories) {
                        String repositoryName = item.asText();
                        if (repositoryName == null || repositoryName.isBlank()) {
                            continue;
                        }

                        String normalizedName = normalizeCatalogRepositoryName(repositoryRef, repositoryName, scopedToPrefix);
                        if (matchesPrefix(normalizedName, repositoryRef.path(), sourceResource.isRecursive())) {
                            matches.add(normalizedName);
                        }
                    }
                }

                nextUrl = parseNextLink(response.headers().firstValue("Link").orElse(null));
                safetyCounter++;
            }
        } catch (Exception ex) {
            log.debug("Catalog discovery failed for endpoint {}: {}", firstUrl, ex.getMessage());
        }
    }

    private String normalizeCatalogRepositoryName(RepositoryRef repositoryRef,
                                                  String repositoryName,
                                                  boolean scopedToPrefix) {
        String trimmed = repositoryName.trim();

        String registryPrefix = repositoryRef.registry() + "/";
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(registryPrefix.toLowerCase(Locale.ROOT))) {
            trimmed = trimmed.substring(registryPrefix.length());
        }

        String httpsRegistryPrefix = "https://" + repositoryRef.registry() + "/";
        if (trimmed.toLowerCase(Locale.ROOT).startsWith(httpsRegistryPrefix.toLowerCase(Locale.ROOT))) {
            trimmed = trimmed.substring(httpsRegistryPrefix.length());
        }

        if (!scopedToPrefix) {
            return trimmed;
        }

        if (trimmed.equals(repositoryRef.path()) || trimmed.startsWith(repositoryRef.path() + "/")) {
            return trimmed;
        }
        return repositoryRef.path() + "/" + trimmed;
    }

    private Set<String> discoverViaGithubApi(RepositoryRef repositoryRef, MonitoredResource sourceResource) throws Exception {
        Set<String> matches = new LinkedHashSet<>();
        Optional<ResourceTypeAuth> auth = resolveAuthForRepository(repositoryRef, "https://api.github.com", sourceResource.getTarget());

        String owner = repositoryRef.owner();
        if (owner == null || owner.isBlank()) {
            return matches;
        }

        String endpointBase = "https://api.github.com/users/" + owner + "/packages?package_type=container&per_page=" + PAGE_SIZE + "&page=";
        int status = fetchGithubPackages(matches, endpointBase, auth, repositoryRef, sourceResource);
        if (status == 404) {
            endpointBase = "https://api.github.com/orgs/" + owner + "/packages?package_type=container&per_page=" + PAGE_SIZE + "&page=";
            fetchGithubPackages(matches, endpointBase, auth, repositoryRef, sourceResource);
        }

        return matches;
    }

    private int fetchGithubPackages(Set<String> matches,
                                    String endpointBase,
                                    Optional<ResourceTypeAuth> auth,
                                    RepositoryRef repositoryRef,
                                    MonitoredResource sourceResource) throws Exception {
        int page = 1;
        while (page <= 100) {
            String endpoint = endpointBase + page;
            HttpResponse<String> response = sendGet(endpoint, auth, sourceResource);
            if (response.statusCode() == 404) {
                return 404;
            }
            if (response.statusCode() != 200) {
                throw new IllegalStateException("GitHub package endpoint responded with HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray() || root.isEmpty()) {
                break;
            }

            int itemCount = 0;
            for (JsonNode item : root) {
                String packageName = item.path("name").asText(null);
                if (packageName == null || packageName.isBlank()) {
                    continue;
                }
                String repositoryPath = normalizeGithubRepositoryPath(repositoryRef.owner(), packageName);
                if (matchesPrefix(repositoryPath, repositoryRef.path(), sourceResource.isRecursive())) {
                    matches.add(repositoryPath);
                }
                itemCount++;
            }

            if (itemCount < PAGE_SIZE) {
                break;
            }
            page++;
        }
        return 200;
    }

    private HttpResponse<String> sendGet(String url,
                                         Optional<ResourceTypeAuth> auth,
                                         MonitoredResource sourceResource) throws Exception {
        URI uri = URI.create(url);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET();

        if ("api.github.com".equals(host)) {
            requestBuilder.header("Accept", "application/vnd.github+json");
            requestBuilder.header("X-GitHub-Api-Version", "2022-11-28");
        }

        auth.ifPresent(value -> {
            if ("api.github.com".equals(host)) {
                String token = value.getPassword();
                if (token == null || token.isBlank()) {
                    token = value.getUsername();
                }
                if (token != null && !token.isBlank()) {
                    requestBuilder.header("Authorization", "Bearer " + token.trim());
                }
                return;
            }

            if (value.getUsername() != null && !value.getUsername().isBlank()) {
                String encoded = Base64.getEncoder().encodeToString(
                        (value.getUsername() + ":" + (value.getPassword() == null ? "" : value.getPassword())).getBytes()
                );
                requestBuilder.header("Authorization", "Basic " + encoded);
            }
        });

        return getHttpClient(sourceResource).send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Optional<ResourceTypeAuth> resolveAuth(String... candidates) {
        for (String candidate : candidates) {
            Optional<ResourceTypeAuth> auth = authService.findMatchingAuth(candidate, "DOCKER");
            if (auth.isPresent()) {
                return auth;
            }
        }
        return Optional.empty();
    }

    private Optional<ResourceTypeAuth> resolveAuthForRepository(RepositoryRef repositoryRef, String... additionalCandidates) {
        List<String> candidates = new java.util.ArrayList<>();

        String registry = repositoryRef.registry();
        String path = repositoryRef.path();

        candidates.add("https://" + registry);
        candidates.add(registry);
        candidates.add("https://" + registry + "/" + path);
        candidates.add(registry + "/" + path);

        for (String candidate : additionalCandidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            candidates.add(candidate);
            if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
                candidates.add("https://" + candidate);
            }
        }

        return resolveAuth(candidates.toArray(String[]::new));
    }

    private boolean matchesPrefix(String repositoryPath, String requestedPath, boolean recursive) {
        if (repositoryPath == null || repositoryPath.isBlank()) {
            return false;
        }
        if (requestedPath == null || requestedPath.isBlank()) {
            return true;
        }

        if (repositoryPath.equals(requestedPath)) {
            return true;
        }

        String prefix = requestedPath + "/";
        if (!repositoryPath.startsWith(prefix)) {
            return false;
        }

        if (recursive) {
            return true;
        }

        String remainder = repositoryPath.substring(prefix.length());
        return !remainder.contains("/");
    }

    private String normalizeGithubRepositoryPath(String owner, String packageName) {
        String trimmed = packageName.trim();
        if (trimmed.startsWith(owner + "/")) {
            return trimmed;
        }
        return owner + "/" + trimmed;
    }

    private String displayName(RepositoryRef repositoryRef, String repositoryPath) {
        String ownerPrefix = repositoryRef.owner() + "/";
        if (repositoryPath.startsWith(ownerPrefix)) {
            return repositoryPath.substring(ownerPrefix.length());
        }
        return repositoryPath;
    }

    private String normalizeImageRepositoryPath(RepositoryRef repositoryRef, String repositoryPath) {
        String trimmed = repositoryPath == null ? "" : repositoryPath.trim();
        if (trimmed.isBlank()) {
            return trimmed;
        }

        String requestedPath = repositoryRef.path();
        if (!requestedPath.toLowerCase(Locale.ROOT).startsWith("artifactory/")) {
            return trimmed;
        }

        if (trimmed.equalsIgnoreCase("artifactory")) {
            return trimmed;
        }

        if (trimmed.toLowerCase(Locale.ROOT).startsWith("artifactory/")) {
            return trimmed.substring("artifactory/".length());
        }

        return trimmed;
    }

    private String parseNextLink(String rawLinkHeader) {
        if (rawLinkHeader == null || rawLinkHeader.isBlank()) {
            return null;
        }
        String[] parts = rawLinkHeader.split(",");
        for (String part : parts) {
            String[] sections = part.trim().split(";");
            if (sections.length < 2) {
                continue;
            }
            String urlPart = sections[0].trim();
            String relPart = sections[1].trim();
            if (!relPart.contains("rel=\"next\"")) {
                continue;
            }
            if (urlPart.startsWith("<") && urlPart.endsWith(">")) {
                return urlPart.substring(1, urlPart.length() - 1);
            }
        }
        return null;
    }

    private RepositoryRef parseRepositoryRef(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            throw new IllegalArgumentException("Dockerrepository target is required");
        }

        String normalized = rawTarget.trim();
        int schemeSeparator = normalized.indexOf("://");
        if (schemeSeparator >= 0) {
            normalized = normalized.substring(schemeSeparator + 3);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        int slash = normalized.indexOf('/');
        if (slash <= 0 || slash == normalized.length() - 1) {
            throw new IllegalArgumentException("Dockerrepository target must include registry and namespace (e.g. ghcr.io/jfwenisch)");
        }

        String registry = normalized.substring(0, slash).toLowerCase(Locale.ROOT);
        String path = normalized.substring(slash + 1);
        String owner = path.contains("/") ? path.substring(0, path.indexOf('/')) : path;
        return new RepositoryRef(rawTarget.trim(), registry, path, owner);
    }

    private HttpClient getHttpClient(MonitoredResource resource) {
        return resource.isSkipTls() ? insecureHttpClient : httpClient;
    }

    private HttpClient createDefaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    private HttpClient createInsecureHttpClient() {
        try {
            TrustManager[] trustAllCerts = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);

            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .sslContext(sslContext)
                    .sslParameters(sslParameters)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize insecure HTTP client", ex);
        }
    }

    private record RepositoryRef(String originalInput, String registry, String path, String owner) {
    }
}

package tech.wenisch.kairos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.wenisch.kairos.entity.AuthType;
import tech.wenisch.kairos.entity.DiscoveryServiceAuth;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceDiscovery;
import tech.wenisch.kairos.entity.ResourceGroup;
import tech.wenisch.kairos.entity.ResourceType;
import tech.wenisch.kairos.repository.MonitoredResourceRepository;
import tech.wenisch.kairos.repository.ResourceGroupRepository;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
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
public class OpenshiftRouteSyncService {

    private static final String USER_AGENT = "Kairos-OpenshiftRouteSync/1.0";
    private static final String SERVICE_TYPE = "OPENSHIFT_ROUTE";

    private final ResourceService resourceService;
    private final ResourceDiscoveryManagementService resourceDiscoveryManagementService;
    private final MonitoredResourceRepository resourceRepository;
    private final ResourceGroupRepository resourceGroupRepository;
    private final ResourceStatusStreamService resourceStatusStreamService;
    private final HttpCheckService httpCheckService;
    private final ProxySettingsService proxySettingsService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public void sync(ResourceDiscovery sourceResource) {
        String apiUrl = normalizeApiUrl(sourceResource.getTarget());
        log.debug("Starting OpenShift route discovery sync for API: {}", apiUrl);

        // Discover all projects
        Set<String> projectNames = discoverProjects(apiUrl, sourceResource);
        log.debug("Discovered {} projects", projectNames.size());

        // Get the main managed group for this discovery service
        ResourceGroup mainGroup = resourceDiscoveryManagementService.findOrCreateManagedGroup(sourceResource);

        // Track all discovered resources for cleanup
        Map<String, MonitoredResource> existingResourcesByTarget = buildExistingResourcesMap();

        Set<String> desiredTargets = new HashSet<>();
        int displayOrder = 0;

        // Process each project
        for (String projectName : projectNames.stream().sorted().toList()) {
            try {
                // Create a sub-group for this project
                String projectGroupName = mainGroup.getName() + "/" + projectName;
                ResourceGroup projectGroup = findOrCreateProjectGroup(projectGroupName);

                // Discover routes in this project
                Set<RouteInfo> routes = discoverRoutesInProject(apiUrl, projectName, sourceResource);
                log.debug("Discovered {} routes in project '{}'", routes.size(), projectName);

                // Create/update resources for each route
                for (RouteInfo route : routes.stream()
                        .sorted(Comparator.comparing(r -> r.name))
                        .toList()) {
                    String resourceTarget = constructRouteTarget(route);
                    String targetKey = resourceTarget.toLowerCase(Locale.ROOT);
                    desiredTargets.add(targetKey);

                    String resourceName = projectName + "/" + route.name;
                    MonitoredResource resource = existingResourcesByTarget.get(targetKey);
                    boolean isNew = resource == null;

                    if (resource == null) {
                        resource = MonitoredResource.builder()
                                .name(resourceName)
                                .resourceType(ResourceType.HTTP)
                                .target(resourceTarget)
                                .skipTls(sourceResource.isSkipTls())
                                .recursive(false)
                                .displayOrder(displayOrder)
                                .active(true)
                                .build();
                        resource.getGroups().add(projectGroup);
                        resource.getGroups().add(mainGroup);
                    } else {
                        resource.setName(resourceName);
                        resource.setTarget(resourceTarget);
                        resource.setResourceType(ResourceType.HTTP);
                        resource.setSkipTls(sourceResource.isSkipTls());
                        resource.setDisplayOrder(displayOrder);
                        resource.setActive(true);
                        if (!resource.getGroups().contains(projectGroup)) {
                            resource.getGroups().add(projectGroup);
                        }
                        if (!resource.getGroups().contains(mainGroup)) {
                            resource.getGroups().add(mainGroup);
                        }
                    }

                    MonitoredResource saved = resourceService.save(resource);

                    if (isNew) {
                        try {
                            resourceStatusStreamService.publishResourceChecking(saved);
                            httpCheckService.check(saved);
                        } catch (Exception ex) {
                            log.warn("Immediate check for newly created OpenShift route '{}' failed: {}",
                                    saved.getTarget(), ex.getMessage());
                        }
                    }

                    existingResourcesByTarget.put(targetKey, saved);
                    displayOrder++;
                }
            } catch (Exception ex) {
                log.warn("Failed to discover routes in project '{}': {}", projectName, ex.getMessage());
            }
        }

        // Clean up resources that are no longer discovered
        List<MonitoredResource> resourcesInMainGroup = resourceRepository
                .findByGroups_IdAndResourceType(mainGroup.getId(), ResourceType.HTTP);

        for (MonitoredResource existing : resourcesInMainGroup) {
            String key = existing.getTarget() == null ? "" : existing.getTarget().trim().toLowerCase(Locale.ROOT);
            if (!desiredTargets.contains(key)) {
                log.debug("Deleting OpenShift route resource '{}' (no longer discovered)", existing.getTarget());
                resourceService.delete(existing.getId());
            }
        }

        log.info("OpenShift route discovery sync finished for '{}' (total resources: {}, removed: {})",
                sourceResource.getName(),
                desiredTargets.size(),
                Math.max(0, resourcesInMainGroup.size() - desiredTargets.size()));
    }

    private String normalizeApiUrl(String target) {
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("OpenShift API URL is required");
        }
        String normalized = target.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private Set<String> discoverProjects(String apiUrl, ResourceDiscovery sourceResource) {
        Set<String> projects = new LinkedHashSet<>();
        try {
            Optional<DiscoveryServiceAuth> auth = resourceDiscoveryManagementService
                    .findMatchingAuth(apiUrl, SERVICE_TYPE);

            List<String> candidateUrls = List.of(
                    apiUrl + "/apis/project.openshift.io/v1/projects",
                    apiUrl + "/api/v1/namespaces"
            );

            for (String url : candidateUrls) {
                HttpResponse<String> response = sendGet(url, auth, sourceResource);

                if (response.statusCode() == 401) {
                    log.warn("Unauthorized access to OpenShift API at {}: check credentials", apiUrl);
                    return projects;
                }

                if (response.statusCode() == 404) {
                    log.debug("Project discovery endpoint not available at {}", url);
                    continue;
                }

                if (response.statusCode() != 200) {
                    log.warn("Failed to discover projects from {}: HTTP {}", url, response.statusCode());
                    return projects;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode items = root.path("items");

                if (items.isArray()) {
                    for (JsonNode item : items) {
                        String projectName = item.path("metadata").path("name").asText(null);
                        if (projectName != null && !projectName.isBlank()) {
                            projects.add(projectName);
                        }
                    }
                }

                if (!projects.isEmpty()) {
                    log.debug("Discovered {} projects from OpenShift API via {}", projects.size(), url);
                    return projects;
                }
            }

            log.warn("Failed to discover projects from OpenShift API: no supported project endpoint responded successfully");
        } catch (Exception ex) {
            log.warn("Error discovering OpenShift projects: {}", ex.getMessage());
        }

        return projects;
    }

    private Set<RouteInfo> discoverRoutesInProject(String apiUrl, String projectName, ResourceDiscovery sourceResource) {
        Set<RouteInfo> routes = new LinkedHashSet<>();
        try {
            String url = apiUrl + "/apis/route.openshift.io/v1/namespaces/" + projectName + "/routes";
            Optional<DiscoveryServiceAuth> auth = resourceDiscoveryManagementService
                    .findMatchingAuth(apiUrl, SERVICE_TYPE);

            HttpResponse<String> response = sendGet(url, auth, sourceResource);

            if (response.statusCode() == 403 || response.statusCode() == 404) {
                log.debug("No access or routes not found in project '{}': HTTP {}", projectName, response.statusCode());
                return routes;
            }

            if (response.statusCode() != 200) {
                log.warn("Failed to discover routes in project '{}': HTTP {}", projectName, response.statusCode());
                return routes;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("items");

            if (items.isArray()) {
                for (JsonNode item : items) {
                    RouteInfo route = parseRoute(item);
                    if (route != null && route.host != null && !route.host.isBlank()) {
                        routes.add(route);
                    }
                }
            }

            log.debug("Discovered {} routes in project '{}'", routes.size(), projectName);
        } catch (Exception ex) {
            log.debug("Error discovering routes in project '{}': {}", projectName, ex.getMessage());
        }

        return routes;
    }

    private RouteInfo parseRoute(JsonNode routeItem) {
        try {
            String name = routeItem.path("metadata").path("name").asText(null);
            String host = routeItem.path("status").path("ingress").get(0)
                    .path("host").asText(null);
            String scheme = routeItem.path("spec").path("tls").isMissingNode() ? "http" : "https";
            int port = routeItem.path("spec").path("port").path("targetPort").asInt(80);
            if ("https".equals(scheme) && port == 80) {
                port = 443;
            }

            if (name == null || name.isBlank() || host == null || host.isBlank()) {
                return null;
            }

            return new RouteInfo(name, host, scheme, port);
        } catch (Exception ex) {
            log.debug("Failed to parse route: {}", ex.getMessage());
            return null;
        }
    }

    private String constructRouteTarget(RouteInfo route) {
        return route.scheme + "://" + route.host + (isDefaultPort(route.scheme, route.port) ? "" : ":" + route.port);
    }

    private boolean isDefaultPort(String scheme, int port) {
        return ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
    }

    private ResourceGroup findOrCreateProjectGroup(String groupName) {
        Optional<ResourceGroup> existing = resourceGroupRepository.findByNameIgnoreCase(groupName);
        if (existing.isPresent()) {
            return existing.get();
        }
        return resourceGroupRepository.save(ResourceGroup.builder()
                .name(groupName)
                .displayOrder(0)
                .build());
    }

    private Map<String, MonitoredResource> buildExistingResourcesMap() {
        Map<String, MonitoredResource> map = new HashMap<>();
        List<MonitoredResource> allHttpResources = resourceRepository.findByResourceType(ResourceType.HTTP);
        for (MonitoredResource resource : allHttpResources) {
            String target = resource.getTarget();
            if (target != null && !target.isBlank()) {
                String key = target.trim().toLowerCase(Locale.ROOT);
                map.put(key, resource);
            }
        }
        return map;
    }

    private HttpResponse<String> sendGet(String url,
                                        Optional<DiscoveryServiceAuth> auth,
                                        ResourceDiscovery sourceResource) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET();

        auth.ifPresent(value -> {
            if (shouldUseBearerAuth(value)) {
                if (value.getPassword() != null && !value.getPassword().isBlank()) {
                    requestBuilder.header("Authorization", "Bearer " + value.getPassword().trim());
                }
            } else if (value.getUsername() != null && !value.getUsername().isBlank()) {
                String encoded = Base64.getEncoder().encodeToString(
                        (value.getUsername() + ":" + (value.getPassword() == null ? "" : value.getPassword())).getBytes()
                );
                requestBuilder.header("Authorization", "Basic " + encoded);
            }
        });

        return getHttpClient(url, sourceResource.isSkipTls()).send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean shouldUseBearerAuth(DiscoveryServiceAuth auth) {
        if (auth.getAuthType() == AuthType.BEARER) {
            return true;
        }

        String username = auth.getUsername() == null ? "" : auth.getUsername().trim();
        if (username.isBlank()) {
            return true;
        }

        String password = auth.getPassword() == null ? "" : auth.getPassword().trim();
        return password.startsWith("sha256~") || (password.startsWith("eyJ") && countOccurrences(password, '.') >= 2);
    }

    private int countOccurrences(String value, char needle) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == needle) {
                count++;
            }
        }
        return count;
    }

    private HttpClient getHttpClient(String target, boolean skipTls) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL);

        proxySettingsService.resolveHttpProxyForTarget(target).ifPresent(endpoint -> {
            builder.proxy(ProxySelector.of(InetSocketAddress.createUnresolved(endpoint.host(), endpoint.port())));
            if (endpoint.hasCredentials()) {
                builder.authenticator(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(
                                endpoint.username(),
                                endpoint.password().toCharArray());
                    }
                });
            }
        });

        if (skipTls) {
            return createInsecureHttpClient(builder);
        }
        return builder.build();
    }

    private HttpClient createInsecureHttpClient(HttpClient.Builder builder) {
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                        @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm(null);
            return builder
                    .sslContext(sslContext)
                    .sslParameters(sslParameters)
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize insecure HTTP client", ex);
        }
    }

    private record RouteInfo(String name, String host, String scheme, int port) {}
}

package tech.wenisch.kairos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class DockerCheckService {

    private static final Pattern AUTH_PARAM_PATTERN = Pattern.compile("(\\w+)=(\"([^\"]*)\"|([^,]+))");
    private static final String MANIFEST_ACCEPT =
            "application/vnd.docker.distribution.manifest.v2+json," +
            "application/vnd.oci.image.manifest.v1+json," +
            "application/vnd.docker.distribution.manifest.list.v2+json," +
            "application/vnd.oci.image.index.v1+json";

    private final CheckResultRepository checkResultRepository;
    private final AuthService authService;
    private final ResourceStatusStreamService resourceStatusStreamService;
    private final OutageService outageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = createDefaultHttpClient();
    private final HttpClient insecureHttpClient = createInsecureHttpClient();

    public CheckResult check(MonitoredResource resource) {
        String image = resource.getTarget();
        long checkStartedNanos = System.nanoTime();
        try {
            DockerImageRef imageRef = parseImageRef(image);

            Optional<ResourceTypeAuth> authOpt = resolveDockerAuth(image, imageRef);
                String basicAuthHeader = authOpt
                    .map(auth -> toBasicHeader(auth.getUsername(), auth.getPassword()))
                    .orElse(null);
                String authUsername = authOpt.map(ResourceTypeAuth::getUsername).orElse(null);

            HttpClient client = getHttpClient(resource);
            AuthState authState = new AuthState(
                    basicAuthHeader,
                    authUsername,
                null
            );

            ManifestResponse manifestResponse = fetchManifest(client, imageRef, imageRef.reference(), authState);
            List<String> blobDigests = extractBlobDigests(client, imageRef, manifestResponse, authState);

            if (blobDigests.isEmpty()) {
                throw new RuntimeException("Manifest has no blobs to validate pullability");
            }

            for (String digest : blobDigests) {
                probeBlobDownload(client, imageRef, digest, authState);
            }

            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckStatus.AVAILABLE)
                    .checkedAt(LocalDateTime.now())
                    .message("Manifest and " + blobDigests.size() + " blobs downloadable")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
            CheckResult saved = checkResultRepository.save(result);
            outageService.evaluate(resource);
            resourceStatusStreamService.publishResourceUpdate(resource);
            return saved;

        } catch (Exception e) {
            log.warn("Docker check failed for image {}: {}", image, e.getMessage());
            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckFailureClassifier.resolveStatus(e))
                    .checkedAt(LocalDateTime.now())
                    .message(e.getMessage())
                    .errorCode("DOCKER_ERROR")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
            CheckResult saved = checkResultRepository.save(result);
            outageService.evaluate(resource);
            resourceStatusStreamService.publishResourceUpdate(resource);
            return saved;
        }
    }

    private Optional<ResourceTypeAuth> resolveDockerAuth(String image, DockerImageRef imageRef) {
        Set<String> candidates = new LinkedHashSet<>();
        String repositoryPath = imageRef.registry() + "/" + imageRef.repository();

        candidates.add(image);
        candidates.add(imageRef.registry());
        candidates.add(repositoryPath);
        candidates.add("https://" + imageRef.registry());
        candidates.add("https://" + repositoryPath);

        if ("registry-1.docker.io".equalsIgnoreCase(imageRef.registry())) {
            candidates.add("docker.io");
            candidates.add("docker.io/" + imageRef.repository());
            candidates.add("https://docker.io");
            candidates.add("https://docker.io/" + imageRef.repository());
            candidates.add("index.docker.io");
            candidates.add("index.docker.io/" + imageRef.repository());
            candidates.add("https://index.docker.io");
            candidates.add("https://index.docker.io/" + imageRef.repository());
        }

        return candidates.stream()
                .filter(candidate -> candidate != null && !candidate.isBlank())
                .map(candidate -> authService.findMatchingAuth(candidate, "DOCKER"))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    private ManifestResponse fetchManifest(HttpClient client,
                                           DockerImageRef imageRef,
                                           String reference,
                                           AuthState authState) throws Exception {
        String manifestUrl = "https://" + imageRef.registry() + "/v2/" + imageRef.repository()
                + "/manifests/" + reference;

        HttpResponse<String> response = sendAuthenticatedRequest(
                client,
                HttpRequest.newBuilder()
                        .uri(URI.create(manifestUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Accept", MANIFEST_ACCEPT)
                        .GET(),
                imageRef,
                authState,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(formatRegistryHttpError("Manifest endpoint", response.statusCode(), response.body()));
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        return new ManifestResponse(contentType, response.body());
    }

    private List<String> extractBlobDigests(HttpClient client,
                                            DockerImageRef imageRef,
                                            ManifestResponse manifestResponse,
                                            AuthState authState) throws Exception {
        JsonNode manifestJson = objectMapper.readTree(manifestResponse.body());
        String mediaType = firstNonBlank(
                manifestJson.path("mediaType").asText(),
                normalizeMediaType(manifestResponse.contentType())
        );

        if (isManifestList(mediaType)) {
            String platformDigest = selectPlatformDigest(manifestJson);
            if (platformDigest == null || platformDigest.isBlank()) {
                throw new RuntimeException("Manifest list/index does not contain a usable platform manifest");
            }
            ManifestResponse platformManifest = fetchManifest(client, imageRef, platformDigest, authState);
            manifestJson = objectMapper.readTree(platformManifest.body());
        }

        List<String> digests = new ArrayList<>();
        JsonNode configDigest = manifestJson.path("config").path("digest");
        if (configDigest.isTextual() && !configDigest.asText().isBlank()) {
            digests.add(configDigest.asText());
        }

        JsonNode layers = manifestJson.path("layers");
        if (layers.isArray()) {
            for (JsonNode layer : layers) {
                JsonNode digestNode = layer.path("digest");
                if (digestNode.isTextual() && !digestNode.asText().isBlank()) {
                    digests.add(digestNode.asText());
                }
            }
        }
        return digests;
    }

    private void probeBlobDownload(HttpClient client,
                                   DockerImageRef imageRef,
                                   String digest,
                                   AuthState authState) throws Exception {
        String blobUrl = "https://" + imageRef.registry() + "/v2/" + imageRef.repository() + "/blobs/" + digest;

        HttpResponse<String> response = sendAuthenticatedRequest(
                client,
                HttpRequest.newBuilder()
                        .uri(URI.create(blobUrl))
                        .timeout(Duration.ofSeconds(30))
                        .header("Range", "bytes=0-0")
                        .GET(),
                imageRef,
                authState,
            HttpResponse.BodyHandlers.ofString()
        );

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException(formatRegistryHttpError("Blob download denied for " + digest, status, response.body()));
        }
    }

    private <T> HttpResponse<T> sendAuthenticatedRequest(HttpClient client,
                                                         HttpRequest.Builder baseRequest,
                                                         DockerImageRef imageRef,
                                                         AuthState authState,
                                                         HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        HttpResponse<T> response = sendWithAuth(client, baseRequest, authState, bodyHandler);

        if (response.statusCode() == 401) {
            String authHeader = response.headers().firstValue("WWW-Authenticate").orElse("");
            if (authHeader.toLowerCase().startsWith("bearer ")) {
                authState.setBearerToken(fetchBearerToken(
                        client,
                        authHeader,
                        imageRef.repository(),
                        authState.getBasicAuthHeader(),
                        authState.getBasicUsername()
                ));
                response = sendWithAuth(client, baseRequest, authState, bodyHandler);
            }
        }
        return response;
    }

    private <T> HttpResponse<T> sendWithAuth(HttpClient client,
                                             HttpRequest.Builder baseRequest,
                                             AuthState authState,
                                             HttpResponse.BodyHandler<T> bodyHandler) throws Exception {
        HttpRequest base = baseRequest.build();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(base.uri())
                .timeout(Duration.ofSeconds(30))
                .method(base.method(), base.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()));

        base.headers().map().forEach((key, values) -> values.forEach(value -> requestBuilder.header(key, value)));

        if (authState.getBasicAuthHeader() != null && !authState.getBasicAuthHeader().isBlank()) {
            requestBuilder.header("Authorization", authState.getBasicAuthHeader());
        }
        if (authState.getBearerToken() != null && !authState.getBearerToken().isBlank()) {
            requestBuilder.setHeader("Authorization", "Bearer " + authState.getBearerToken());
        }
        return client.send(requestBuilder.build(), bodyHandler);
    }

    private String toBasicHeader(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    private String fetchBearerToken(HttpClient client,
                                    String wwwAuthenticate,
                                    String repository,
                                    String basicAuthHeader,
                                    String basicUsername) throws Exception {
        Map<String, String> params = parseAuthParams(wwwAuthenticate.substring(7));
        String realm = params.get("realm");
        if (realm == null || realm.isBlank()) {
            throw new RuntimeException("Registry returned Bearer auth challenge without realm");
        }

        String service = params.getOrDefault("service", "registry.docker.io");
        String scope = params.getOrDefault("scope", "repository:" + repository + ":pull");

        String tokenUrl = realm
                + (realm.contains("?") ? "&" : "?")
                + "service=" + URLEncoder.encode(service, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
        if (basicUsername != null && !basicUsername.isBlank()) {
            tokenUrl += "&account=" + URLEncoder.encode(basicUsername, StandardCharsets.UTF_8);
        }

        HttpRequest.Builder tokenRequestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(30))
                .GET();

        if (basicAuthHeader != null && !basicAuthHeader.isBlank()) {
            tokenRequestBuilder.header("Authorization", basicAuthHeader);
        }

        HttpResponse<String> tokenResponse = client.send(tokenRequestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (tokenResponse.statusCode() == 401 && basicAuthHeader != null && !basicAuthHeader.isBlank()) {
            HttpRequest retryRequest = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            tokenResponse = client.send(retryRequest, HttpResponse.BodyHandlers.ofString());
        }
        if (tokenResponse.statusCode() < 200 || tokenResponse.statusCode() >= 300) {
            throw new RuntimeException(formatRegistryHttpError("Token endpoint", tokenResponse.statusCode(), tokenResponse.body()));
        }

        String body = tokenResponse.body();
        String token = extractJsonString(body, "token");
        if (token == null || token.isBlank()) {
            token = extractJsonString(body, "access_token");
        }
        if (token == null || token.isBlank()) {
            throw new RuntimeException("Token endpoint response does not contain token");
        }
        return token;
    }

    private boolean isManifestList(String mediaType) {
        if (mediaType == null) {
            return false;
        }
        return mediaType.contains("manifest.list.v2+json") || mediaType.contains("image.index.v1+json");
    }

    private String selectPlatformDigest(JsonNode manifestListJson) {
        JsonNode manifests = manifestListJson.path("manifests");
        if (!manifests.isArray() || manifests.isEmpty()) {
            return null;
        }

        for (JsonNode manifest : manifests) {
            String os = manifest.path("platform").path("os").asText("");
            String arch = manifest.path("platform").path("architecture").asText("");
            if ("linux".equalsIgnoreCase(os) && "amd64".equalsIgnoreCase(arch)) {
                String digest = manifest.path("digest").asText();
                if (digest != null && !digest.isBlank()) {
                    return digest;
                }
            }
        }

        String digest = manifests.get(0).path("digest").asText();
        return digest == null || digest.isBlank() ? null : digest;
    }

    private String normalizeMediaType(String contentTypeHeader) {
        if (contentTypeHeader == null) {
            return "";
        }
        int idx = contentTypeHeader.indexOf(';');
        return (idx >= 0 ? contentTypeHeader.substring(0, idx) : contentTypeHeader).trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Map<String, String> parseAuthParams(String input) {
        Map<String, String> result = new HashMap<>();
        Matcher matcher = AUTH_PARAM_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            String quotedValue = matcher.group(3);
            String rawValue = matcher.group(4);
            result.put(key, quotedValue != null ? quotedValue : rawValue);
        }
        return result;
    }

    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json == null ? "" : json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String formatRegistryHttpError(String endpoint, int statusCode, String body) {
        String detail = extractRegistryErrorDetail(body);
        if (detail.isBlank()) {
            return endpoint + " responded with HTTP " + statusCode;
        }
        return endpoint + " responded with HTTP " + statusCode + ": " + detail;
    }

    private String extractRegistryErrorDetail(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode errors = root.path("errors");
            if (errors.isArray() && !errors.isEmpty()) {
                JsonNode firstError = errors.get(0);
                String code = firstError.path("code").asText("").trim();
                String message = firstError.path("message").asText("").trim();
                String manifest = firstError.path("detail").path("manifest").asText("").trim();

                StringBuilder builder = new StringBuilder();
                if (!code.isBlank()) {
                    builder.append(code);
                }
                if (!message.isBlank()) {
                    if (builder.length() > 0) {
                        builder.append(" - ");
                    }
                    builder.append(message);
                }
                if (!manifest.isBlank()) {
                    builder.append(" (manifest=").append(manifest).append(")");
                }
                if (builder.length() > 0) {
                    return builder.toString();
                }
            }
        } catch (Exception ignored) {
        }

        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 240) {
            return normalized;
        }
        return normalized.substring(0, 240) + "...";
    }

    private DockerImageRef parseImageRef(String image) {
        String normalized = image == null ? "" : image.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Image reference must not be empty");
        }

        String namePart = normalized;
        String reference = "latest";

        int digestIdx = normalized.indexOf('@');
        if (digestIdx >= 0) {
            namePart = normalized.substring(0, digestIdx);
            reference = normalized.substring(digestIdx + 1);
        } else {
            int lastSlash = normalized.lastIndexOf('/');
            int lastColon = normalized.lastIndexOf(':');
            if (lastColon > lastSlash) {
                namePart = normalized.substring(0, lastColon);
                reference = normalized.substring(lastColon + 1);
            }
        }

        String registry;
        String repository;
        String[] segments = namePart.split("/", 2);
        if (segments.length > 1 && (segments[0].contains(".") || segments[0].contains(":") || "localhost".equals(segments[0]))) {
            registry = segments[0];
            repository = segments[1];
        } else {
            registry = "registry-1.docker.io";
            repository = namePart;
            if (!repository.contains("/")) {
                repository = "library/" + repository;
            }
        }

        return new DockerImageRef(registry, repository, reference);
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
            TrustManager[] trustAllManagers = new TrustManager[]{new X509TrustManager() {
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
            }};

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllManagers, new SecureRandom());

            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");

            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .sslContext(sslContext)
                    .sslParameters(sslParameters)
                    .build();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to initialize insecure HTTP client", ex);
        }
    }

    private Long elapsedMillis(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    private record ManifestResponse(String contentType, String body) {
    }

    private static class AuthState {
        private final String basicAuthHeader;
        private final String basicUsername;
        private String bearerToken;

        private AuthState(String basicAuthHeader,
                          String basicUsername,
                          String bearerToken) {
            this.basicAuthHeader = basicAuthHeader;
            this.basicUsername = basicUsername;
            this.bearerToken = bearerToken;
        }

        private String getBasicAuthHeader() {
            return basicAuthHeader;
        }

        private String getBearerToken() {
            return bearerToken;
        }

        private String getBasicUsername() {
            return basicUsername;
        }

        private void setBearerToken(String bearerToken) {
            this.bearerToken = bearerToken;
        }
    }

    private record DockerImageRef(String registry, String repository, String reference) {
    }
}

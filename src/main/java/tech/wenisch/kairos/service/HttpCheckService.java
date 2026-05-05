package tech.wenisch.kairos.service;

import tech.wenisch.kairos.dto.InstantCheckExecutionResult;
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
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HttpCheckService {

    private final CheckResultRepository checkResultRepository;
    private final AuthService authService;
    private final ResourceStatusStreamService resourceStatusStreamService;
    private final OutageService outageService;
    private final ProxySettingsService proxySettingsService;

    public InstantCheckExecutionResult probe(String target, boolean skipTls, boolean useStoredAuth) {
        String url = target == null ? "" : target.trim();
        long checkStartedNanos = System.nanoTime();
        try {
            URI uri = URI.create(url);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .GET();

            if (useStoredAuth) {
                authService.findMatchingAuth(url, "HTTP").ifPresent(auth -> {
                    String credentials = auth.getUsername() + ":" + auth.getPassword();
                    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                    requestBuilder.header("Authorization", "Basic " + encoded);
                });
            }

            HttpResponse<Void> response = getHttpClient(url, skipTls)
                    .send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();

            CheckStatus status = statusCode >= 200 && statusCode < 300
                    ? CheckStatus.AVAILABLE
                    : CheckStatus.NOT_AVAILABLE;

            return InstantCheckExecutionResult.builder()
                    .status(status)
                    .message("HTTP " + statusCode)
                    .errorCode(String.valueOf(statusCode))
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
        } catch (Exception e) {
            return InstantCheckExecutionResult.builder()
                    .status(CheckFailureClassifier.resolveStatus(e))
                    .message(e.getMessage())
                    .errorCode("CONNECTION_ERROR")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .build();
        }
    }

    public CheckResult check(MonitoredResource resource) {
        String url = resource.getTarget();
        long checkStartedNanos = System.nanoTime();
        LatencyMetrics phaseLatency = new LatencyMetrics(null, null, null);
        try {
            URI uri = URI.create(url);
            phaseLatency = measureHttpPhases(resource, uri);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(Duration.ofSeconds(15))
                    .GET();

            // Apply Basic Auth if a matching credential is configured for this URL
            Optional<ResourceTypeAuth> authOpt = authService.findMatchingAuth(url, "HTTP");
            authOpt.ifPresent(auth -> {
                String credentials = auth.getUsername() + ":" + auth.getPassword();
                String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
                requestBuilder.header("Authorization", "Basic " + encoded);
                log.debug("Applying Basic Auth '{}' to HTTP check for {}", auth.getName(), url);
            });

            HttpResponse<Void> response = getHttpClient(resource.getTarget(), resource.isSkipTls())
                    .send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();

            CheckResult result;
            if (statusCode >= 200 && statusCode < 300) {
                result = CheckResult.builder()
                        .resource(resource)
                        .status(CheckStatus.AVAILABLE)
                        .checkedAt(LocalDateTime.now())
                        .message("HTTP " + statusCode)
                        .errorCode(String.valueOf(statusCode))
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .dnsResolutionMs(phaseLatency.dnsResolutionMs())
                    .connectMs(phaseLatency.connectMs())
                    .tlsHandshakeMs(phaseLatency.tlsHandshakeMs())
                        .build();
            } else {
                result = CheckResult.builder()
                        .resource(resource)
                        .status(CheckStatus.NOT_AVAILABLE)
                        .checkedAt(LocalDateTime.now())
                        .message("HTTP " + statusCode)
                        .errorCode(String.valueOf(statusCode))
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .dnsResolutionMs(phaseLatency.dnsResolutionMs())
                    .connectMs(phaseLatency.connectMs())
                    .tlsHandshakeMs(phaseLatency.tlsHandshakeMs())
                        .build();
            }
            CheckResult saved = checkResultRepository.save(result);
            outageService.evaluate(resource);
            resourceStatusStreamService.publishResourceUpdate(resource);
            return saved;
        } catch (Exception e) {
            log.warn("HTTP check failed for {}: {}", url, e.getMessage());
            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckFailureClassifier.resolveStatus(e))
                    .checkedAt(LocalDateTime.now())
                    .message(e.getMessage())
                    .errorCode("CONNECTION_ERROR")
                    .latencyMs(elapsedMillis(checkStartedNanos))
                    .dnsResolutionMs(phaseLatency.dnsResolutionMs())
                    .connectMs(phaseLatency.connectMs())
                    .tlsHandshakeMs(phaseLatency.tlsHandshakeMs())
                    .build();
            CheckResult saved = checkResultRepository.save(result);
            outageService.evaluate(resource);
            resourceStatusStreamService.publishResourceUpdate(resource);
            return saved;
        }
    }

    HttpClient getHttpClient(String target, boolean skipTls) {
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

            return builder
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

    private LatencyMetrics measureHttpPhases(MonitoredResource resource, URI uri) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return new LatencyMetrics(null, null, null);
        }

        Long dnsResolutionMs = null;
        Long connectMs = null;
        Long tlsHandshakeMs = null;

        InetAddress[] addresses = null;
        long phaseStart = System.nanoTime();
        try {
            addresses = InetAddress.getAllByName(host);
            dnsResolutionMs = elapsedMillis(phaseStart);
        } catch (Exception ex) {
            log.debug("DNS phase timing unavailable for {}: {}", host, ex.getMessage());
        }

        int port = resolvePort(uri);
        if (addresses != null && addresses.length > 0 && port > 0) {
            try (Socket socket = new Socket()) {
                phaseStart = System.nanoTime();
                socket.connect(new InetSocketAddress(addresses[0], port), 5000);
                connectMs = elapsedMillis(phaseStart);

                if ("https".equalsIgnoreCase(uri.getScheme()) && !resource.isSkipTls()) {
                    phaseStart = System.nanoTime();
                    SSLSocketFactory sslFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                    try (SSLSocket sslSocket = (SSLSocket) sslFactory.createSocket(socket, host, port, true)) {
                        sslSocket.startHandshake();
                    }
                    tlsHandshakeMs = elapsedMillis(phaseStart);
                }
            } catch (IOException ex) {
                log.debug("Connection phase timing unavailable for {}:{}: {}", host, port, ex.getMessage());
            }
        }

        return new LatencyMetrics(dnsResolutionMs, connectMs, tlsHandshakeMs);
    }

    private int resolvePort(URI uri) {
        if (uri.getPort() > 0) {
            return uri.getPort();
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        return -1;
    }

    private record LatencyMetrics(Long dnsResolutionMs, Long connectMs, Long tlsHandshakeMs) {
    }
}

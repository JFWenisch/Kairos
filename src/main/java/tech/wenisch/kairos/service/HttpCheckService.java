package tech.wenisch.kairos.service;

import tech.wenisch.kairos.entity.CheckResult;
import tech.wenisch.kairos.entity.CheckStatus;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.ResourceTypeAuth;
import tech.wenisch.kairos.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CheckResult check(MonitoredResource resource) {
        String url = resource.getTarget();
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
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

            HttpResponse<Void> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();

            CheckResult result;
            if (statusCode >= 200 && statusCode < 300) {
                result = CheckResult.builder()
                        .resource(resource)
                        .status(CheckStatus.AVAILABLE)
                        .checkedAt(LocalDateTime.now())
                        .message("HTTP " + statusCode)
                        .errorCode(String.valueOf(statusCode))
                        .build();
            } else {
                result = CheckResult.builder()
                        .resource(resource)
                        .status(CheckStatus.NOT_AVAILABLE)
                        .checkedAt(LocalDateTime.now())
                        .message("HTTP " + statusCode)
                        .errorCode(String.valueOf(statusCode))
                        .build();
            }
            return checkResultRepository.save(result);
        } catch (Exception e) {
            log.warn("HTTP check failed for {}: {}", url, e.getMessage());
            CheckResult result = CheckResult.builder()
                    .resource(resource)
                    .status(CheckStatus.NOT_AVAILABLE)
                    .checkedAt(LocalDateTime.now())
                    .message(e.getMessage())
                    .errorCode("CONNECTION_ERROR")
                    .build();
            return checkResultRepository.save(result);
        }
    }
}

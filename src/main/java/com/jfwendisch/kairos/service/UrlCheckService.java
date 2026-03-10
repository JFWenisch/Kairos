package com.jfwendisch.kairos.service;

import com.jfwendisch.kairos.entity.CheckResult;
import com.jfwendisch.kairos.entity.CheckStatus;
import com.jfwendisch.kairos.entity.MonitoredResource;
import com.jfwendisch.kairos.repository.CheckResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class UrlCheckService {

    private final CheckResultRepository checkResultRepository;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public CheckResult check(MonitoredResource resource) {
        String url = resource.getTarget();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
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
            log.warn("URL check failed for {}: {}", url, e.getMessage());
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

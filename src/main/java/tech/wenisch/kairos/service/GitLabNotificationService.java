package tech.wenisch.kairos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationEvent;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.Outage;
import tech.wenisch.kairos.entity.OutageNotificationRef;
import tech.wenisch.kairos.repository.OutageNotificationRefRepository;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitLabNotificationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OutageNotificationRefRepository outageNotificationRefRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void sendOutageNotification(NotificationProvider provider, NotificationEvent event,
                                       Outage outage, MonitoredResource resource) throws Exception {
        if (event == NotificationEvent.OUTAGE_STARTED) {
            openIncident(provider, outage, resource);
        } else {
            closeIncident(provider, outage, resource);
        }
    }

    public void sendTestNotification(NotificationProvider provider) throws Exception {
        String baseUrl = stripTrailingSlash(provider.getGitlabBaseUrl());
        String projectId = provider.getGitlabProjectId();
        String token = provider.getGitlabToken();

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = buildHeaders(token);

        // Verify connectivity: GET the project
        String projectUrl = baseUrl + "/api/v4/projects/" + encodeProjectId(projectId);
        ResponseEntity<String> response = rest.exchange(projectUrl, HttpMethod.GET,
                new HttpEntity<>(headers), String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("GitLab project not accessible, status: " + response.getStatusCode());
        }
        log.info("GitLab provider '{}' test successful – project '{}' is accessible", provider.getName(), projectId);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private void openIncident(NotificationProvider provider, Outage outage, MonitoredResource resource) throws Exception {
        String baseUrl = stripTrailingSlash(provider.getGitlabBaseUrl());
        String projectId = encodeProjectId(provider.getGitlabProjectId());
        String token = provider.getGitlabToken();

        String title = "Outage: " + resource.getName() + " (" + resource.getResourceType() + ")";
        String description = buildOpenDescription(resource, outage);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("issue_type", "incident");

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = buildHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = baseUrl + "/api/v4/projects/" + projectId + "/issues";
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = rest.postForObject(url, request, Map.class);

        if (response == null || !response.containsKey("iid")) {
            throw new RuntimeException("GitLab did not return an issue IID");
        }

        String iid = String.valueOf(response.get("iid"));
        outageNotificationRefRepository.save(OutageNotificationRef.builder()
                .outageId(outage.getId())
                .providerId(provider.getId())
                .externalRef(iid)
                .build());

        log.info("GitLab incident #{} created for outage {} (resource: {})", iid, outage.getId(), resource.getName());
    }

    private void closeIncident(NotificationProvider provider, Outage outage, MonitoredResource resource) throws Exception {
        String baseUrl = stripTrailingSlash(provider.getGitlabBaseUrl());
        String projectId = encodeProjectId(provider.getGitlabProjectId());
        String token = provider.getGitlabToken();

        Optional<OutageNotificationRef> refOpt = outageNotificationRefRepository
                .findByOutageIdAndProviderId(outage.getId(), provider.getId());

        if (refOpt.isEmpty()) {
            log.warn("No GitLab incident reference found for outage {} / provider '{}' – skipping close",
                    outage.getId(), provider.getName());
            return;
        }

        String iid = refOpt.get().getExternalRef();
        String description = buildCloseDescription(resource, outage);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("state_event", "close");
        body.put("description", description);

        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = buildHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = baseUrl + "/api/v4/projects/" + projectId + "/issues/" + iid;
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
        rest.exchange(url, HttpMethod.PUT, request, Void.class);

        outageNotificationRefRepository.deleteByOutageIdAndProviderId(outage.getId(), provider.getId());
        log.info("GitLab incident #{} closed for outage {} (resource: {})", iid, outage.getId(), resource.getName());
    }

    private String buildOpenDescription(MonitoredResource resource, Outage outage) {
        return "## Outage Detected\n\n" +
                "| Field | Value |\n" +
                "|---|---|\n" +
                "| **Resource** | " + resource.getName() + " |\n" +
                "| **Type** | " + resource.getResourceType() + " |\n" +
                "| **Target** | " + resource.getTarget() + " |\n" +
                "| **Started** | " + (outage.getStartDate() != null ? outage.getStartDate().format(FORMATTER) : "—") + " |\n";
    }

    private String buildCloseDescription(MonitoredResource resource, Outage outage) {
        return "## Outage Resolved\n\n" +
                "| Field | Value |\n" +
                "|---|---|\n" +
                "| **Resource** | " + resource.getName() + " |\n" +
                "| **Type** | " + resource.getResourceType() + " |\n" +
                "| **Target** | " + resource.getTarget() + " |\n" +
                "| **Started** | " + (outage.getStartDate() != null ? outage.getStartDate().format(FORMATTER) : "—") + " |\n" +
                "| **Ended** | " + (outage.getEndDate() != null ? outage.getEndDate().format(FORMATTER) : "—") + " |\n";
    }

    private HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("PRIVATE-TOKEN", token);
        return headers;
    }

    private String stripTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String encodeProjectId(String projectId) {
        if (projectId == null) return "";
        // If it's a path (contains /), URL-encode for GitLab API
        if (projectId.contains("/")) {
            return projectId.replace("/", "%2F");
        }
        return projectId;
    }
}

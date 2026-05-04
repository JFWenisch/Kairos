package tech.wenisch.kairos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationEvent;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.Outage;

import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class WebhookNotificationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendOutageNotification(NotificationProvider provider, NotificationEvent event,
                                       Outage outage, MonitoredResource resource) {
        String body = buildBody(provider.getWebhookBodyTemplate(), event, outage, resource);
        post(provider.getWebhookUrl(), body, provider.getName());
    }

    public void sendTestNotification(NotificationProvider provider) {
        String testBody = "{ \"event\": \"TEST\", \"message\": \"Kairos webhook test notification\" }";
        post(provider.getWebhookUrl(), testBody, provider.getName());
    }

    private void post(String url, String body, String providerName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Webhook notification sent to {} via provider '{}'", url, providerName);
        } catch (Exception e) {
            log.error("Failed to send webhook notification via provider '{}': {}", providerName, e.getMessage(), e);
            throw new RuntimeException("Webhook send failed: " + e.getMessage(), e);
        }
    }

    private String buildBody(String template, NotificationEvent event, Outage outage, MonitoredResource resource) {
        if (template == null || template.isBlank()) {
            return buildDefaultBody(event, outage, resource);
        }
        String result = template
                .replace("{{event_type}}", event.name())
                .replace("{{resource_name}}", resource.getName() != null ? resource.getName() : "")
                .replace("{{resource_type}}", resource.getResourceType() != null ? resource.getResourceType().name() : "")
                .replace("{{resource_target}}", resource.getTarget() != null ? resource.getTarget() : "")
                .replace("{{outage_start}}", outage.getStartDate() != null ? outage.getStartDate().format(FORMATTER) : "")
                .replace("{{outage_end}}", outage.getEndDate() != null ? outage.getEndDate().format(FORMATTER) : "");
        return result;
    }

    private String buildDefaultBody(NotificationEvent event, Outage outage, MonitoredResource resource) {
        return String.format(
                "{\"event\":\"%s\",\"resource\":\"%s\",\"type\":\"%s\",\"target\":\"%s\",\"outage_start\":\"%s\",\"outage_end\":\"%s\"}",
                event.name(),
                resource.getName() != null ? resource.getName() : "",
                resource.getResourceType() != null ? resource.getResourceType().name() : "",
                resource.getTarget() != null ? resource.getTarget() : "",
                outage.getStartDate() != null ? outage.getStartDate().format(FORMATTER) : "",
                outage.getEndDate() != null ? outage.getEndDate().format(FORMATTER) : ""
        );
    }
}

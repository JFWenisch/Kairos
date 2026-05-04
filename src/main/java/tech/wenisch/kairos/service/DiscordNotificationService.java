package tech.wenisch.kairos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class DiscordNotificationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int COLOR_RED   = 16711680;
    private static final int COLOR_GREEN = 3066993;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendOutageNotification(NotificationProvider provider, NotificationEvent event,
                                       Outage outage, MonitoredResource resource) {
        String payload = buildPayload(event, outage, resource);
        post(provider.getDiscordWebhookUrl(), payload, provider.getName());
    }

    public void sendTestNotification(NotificationProvider provider) {
        try {
            Map<String, Object> embed = new LinkedHashMap<>();
            embed.put("title", "Kairos – Test Notification");
            embed.put("description", "Your Discord provider is configured correctly.");
            embed.put("color", COLOR_GREEN);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("embeds", List.of(embed));
            post(provider.getDiscordWebhookUrl(), objectMapper.writeValueAsString(body), provider.getName());
        } catch (Exception e) {
            throw new RuntimeException("Discord test failed: " + e.getMessage(), e);
        }
    }

    private void post(String url, String payload, String providerName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(payload, headers);
        try {
            restTemplate.postForEntity(url, request, String.class);
            log.info("Discord notification sent via provider '{}'", providerName);
        } catch (Exception e) {
            log.error("Failed to send Discord notification via provider '{}': {}", providerName, e.getMessage(), e);
            throw new RuntimeException("Discord send failed: " + e.getMessage(), e);
        }
    }

    private String buildPayload(NotificationEvent event, Outage outage, MonitoredResource resource) {
        try {
            boolean started = event == NotificationEvent.OUTAGE_STARTED;

            Map<String, Object> embed = new LinkedHashMap<>();
            embed.put("title", (started ? "⚠️ Outage Started" : "✅ Outage Resolved") + ": " + resource.getName());
            embed.put("color", started ? COLOR_RED : COLOR_GREEN);

            List<Map<String, Object>> fields = new ArrayList<>();
            fields.add(field("Resource", resource.getName(), true));
            fields.add(field("Type", resource.getResourceType() != null ? resource.getResourceType().name() : "-", true));
            fields.add(field("Target", resource.getTarget() != null ? resource.getTarget() : "-", false));
            fields.add(field("Started", outage.getStartDate() != null ? outage.getStartDate().format(FORMATTER) : "-", true));
            if (!started && outage.getEndDate() != null) {
                fields.add(field("Resolved", outage.getEndDate().format(FORMATTER), true));
            }
            embed.put("fields", fields);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("embeds", List.of(embed));
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build Discord payload", e);
        }
    }

    private Map<String, Object> field(String name, String value, boolean inline) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("name", name);
        f.put("value", value);
        f.put("inline", inline);
        return f;
    }
}

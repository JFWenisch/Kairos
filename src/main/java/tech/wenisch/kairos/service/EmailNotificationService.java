package tech.wenisch.kairos.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import tech.wenisch.kairos.entity.MonitoredResource;
import tech.wenisch.kairos.entity.NotificationEvent;
import tech.wenisch.kairos.entity.NotificationProvider;
import tech.wenisch.kairos.entity.Outage;

import java.time.format.DateTimeFormatter;
import java.util.Properties;

@Service
@Slf4j
public class EmailNotificationService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void sendOutageNotification(NotificationProvider provider, NotificationEvent event,
                                       Outage outage, MonitoredResource resource) {
        String subject = buildSubject(event, resource);
        String body = buildBody(event, outage, resource);
        send(provider, subject, body);
    }

    public void sendTestNotification(NotificationProvider provider) {
        send(provider, "Kairos – Test Notification", "This is a test notification from Kairos. Your email provider is configured correctly.");
    }

    private void send(NotificationProvider provider, String subject, String body) {
        JavaMailSenderImpl mailSender = buildMailSender(provider);
        try {
            mailSender.send(mimeMessage -> {
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
                helper.setFrom(provider.getSenderEmail());
                helper.setTo(provider.getRecipientEmail());
                helper.setSubject(subject);
                helper.setText(body, false);
            });
            log.info("Email notification sent to {} (subject: {})", provider.getRecipientEmail(), subject);
        } catch (Exception e) {
            log.error("Failed to send email notification via provider '{}': {}", provider.getName(), e.getMessage(), e);
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    private JavaMailSenderImpl buildMailSender(NotificationProvider provider) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(provider.getSmtpHost());
        sender.setPort(provider.getSmtpPort() != null ? provider.getSmtpPort() : 587);
        if (provider.getSmtpUsername() != null && !provider.getSmtpUsername().isBlank()) {
            sender.setUsername(provider.getSmtpUsername());
            sender.setPassword(provider.getSmtpPassword());
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        boolean useTls = Boolean.TRUE.equals(provider.getSmtpUseTls());
        props.put("mail.smtp.auth", provider.getSmtpUsername() != null && !provider.getSmtpUsername().isBlank());
        props.put("mail.smtp.starttls.enable", useTls);
        return sender;
    }

    private String buildSubject(NotificationEvent event, MonitoredResource resource) {
        return switch (event) {
            case OUTAGE_STARTED -> "⚠️ Outage started: " + resource.getName();
            case OUTAGE_ENDED   -> "✅ Outage resolved: " + resource.getName();
        };
    }

    private String buildBody(NotificationEvent event, Outage outage, MonitoredResource resource) {
        StringBuilder sb = new StringBuilder();
        sb.append("Kairos Notification\n\n");
        sb.append("Event:    ").append(event == NotificationEvent.OUTAGE_STARTED ? "Outage Started" : "Outage Resolved").append("\n");
        sb.append("Resource: ").append(resource.getName()).append("\n");
        sb.append("Type:     ").append(resource.getResourceType()).append("\n");
        sb.append("Target:   ").append(resource.getTarget()).append("\n");
        sb.append("Started:  ").append(outage.getStartDate() != null ? outage.getStartDate().format(FORMATTER) : "-").append("\n");
        if (event == NotificationEvent.OUTAGE_ENDED && outage.getEndDate() != null) {
            sb.append("Resolved: ").append(outage.getEndDate().format(FORMATTER)).append("\n");
        }
        return sb.toString();
    }
}

package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationProvider {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationProviderType type;

    // Email fields
    private String smtpHost;
    private Integer smtpPort;
    private String smtpUsername;
    private String smtpPassword;

    @Builder.Default
    private Boolean smtpUseTls = true;

    private String senderEmail;
    private String recipientEmail;

    // Webhook fields
    @Column(length = 2048)
    private String webhookUrl;

    @Column(columnDefinition = "TEXT")
    private String webhookBodyTemplate;

    // Discord fields
    @Column(length = 2048)
    private String discordWebhookUrl;
}

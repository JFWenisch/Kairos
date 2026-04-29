package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "discoveryServiceConfig")
@EqualsAndHashCode(exclude = "discoveryServiceConfig")
public class DiscoveryServiceAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discovery_service_config_id", nullable = false)
    private DiscoveryServiceConfig discoveryServiceConfig;

    private String name;

    @Enumerated(EnumType.STRING)
    private AuthType authType;

    /**
     * URL/target pattern for this credential. Supports a trailing {@code *} wildcard.
     */
    private String urlPattern;

    private String username;

    private String password;
}

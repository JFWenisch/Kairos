package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceDiscovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private DiscoveryServiceType type;

    private String target;

    @Builder.Default
    private boolean skipTls = false;

    @Column(name = "recursive_enabled", nullable = false)
    @Builder.Default
    private boolean recursive = false;

    @Builder.Default
    private boolean active = true;

    private LocalDateTime createdAt;
}

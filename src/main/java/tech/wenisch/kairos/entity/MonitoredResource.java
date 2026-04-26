package tech.wenisch.kairos.entity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoredResource {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private ResourceType resourceType;

    private String target;

    @JsonProperty("skipTLS")
    @JsonAlias("skipTls")
    private boolean skipTls;

    @Column(name = "recursive_enabled", nullable = false)
    @Builder.Default
    private boolean recursive = false;

    @Builder.Default
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "resource_group_resource",
        joinColumns = @JoinColumn(name = "resource_id"),
        inverseJoinColumns = @JoinColumn(name = "resource_group_id")
    )
    @Builder.Default
    private Set<ResourceGroup> groups = new HashSet<>();

    @Builder.Default
    private int displayOrder = 0;

    private LocalDateTime createdAt;
}

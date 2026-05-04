package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private NotificationProvider provider;

    @Column(nullable = false)
    @Builder.Default
    private boolean notifyOnOutageStarted = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean notifyOnOutageEnded = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private NotificationScopeType scopeType = NotificationScopeType.ALL;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "notification_policy_group",
        joinColumns = @JoinColumn(name = "policy_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    @Builder.Default
    private Set<ResourceGroup> scopedGroups = new HashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "notification_policy_resource",
        joinColumns = @JoinColumn(name = "policy_id"),
        inverseJoinColumns = @JoinColumn(name = "resource_id")
    )
    @Builder.Default
    private Set<MonitoredResource> scopedResources = new HashSet<>();
}

package com.jfwendisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "resourceTypeConfig")
@EqualsAndHashCode(exclude = "resourceTypeConfig")
public class ResourceTypeAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resource_type_config_id", nullable = false)
    private ResourceTypeConfig resourceTypeConfig;

    /** Human-readable label for this credential entry. */
    private String name;

    @Enumerated(EnumType.STRING)
    private AuthType authType;

    /**
     * URL/target pattern that this credential applies to.
     * Supports a single trailing wildcard, e.g. {@code https://registry.example.com*}.
     * An exact value (no wildcard) requires an exact match against the resource target.
     */
    private String urlPattern;

    private String username;

    private String password;
}

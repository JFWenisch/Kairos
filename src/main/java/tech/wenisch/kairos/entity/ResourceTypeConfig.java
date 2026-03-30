package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "authentications")
@EqualsAndHashCode(exclude = "authentications")
public class ResourceTypeConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String typeName;

    private int checkIntervalMinutes;

    private int parallelism;

    private boolean allowPublicAdd;

    private boolean allowPublicCheckNow;

    @Builder.Default
    private int outageThreshold = 3;

    @Builder.Default
    private int recoveryThreshold = 2;

    @OneToMany(mappedBy = "resourceTypeConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ResourceTypeAuth> authentications = new ArrayList<>();
}

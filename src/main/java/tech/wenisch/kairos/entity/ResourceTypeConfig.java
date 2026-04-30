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

    @Builder.Default
    private boolean allowPublicAccess = true;

    private boolean allowPublicAdd;

    private boolean allowPublicCheckNow;

    @Builder.Default
    private boolean instantCheckEnabled = false;

    @Builder.Default
    private boolean instantCheckAllowPublic = false;

    @Builder.Default
    private boolean instantCheckUseStoredAuth = false;

    @Builder.Default
    @Column(length = 4000)
    private String instantCheckAllowedDomains = "*";

    @Builder.Default
    private boolean alwaysDisplayUrl = false;

    @Builder.Default
    private boolean checkHistoryRetentionEnabled = true;

    @Builder.Default
    private int checkHistoryRetentionIntervalMinutes = 60;

    @Builder.Default
    private int checkHistoryRetentionDays = 31;

    @Builder.Default
    private boolean outageRetentionEnabled = true;

    @Builder.Default
    private int outageRetentionIntervalHours = 12;

    @Builder.Default
    private int outageRetentionDays = 31;

    @Builder.Default
    private int outageThreshold = 3;

    @Builder.Default
    private int recoveryThreshold = 2;

    @Builder.Default
    private boolean deleteOutagesOnResourceDelete = true;

    @Builder.Default
    private String embedPolicy = "ALLOW_ALL";

    @OneToMany(mappedBy = "resourceTypeConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ResourceTypeAuth> authentications = new ArrayList<>();
}

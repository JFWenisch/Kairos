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
public class DiscoveryServiceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String typeName;

    @Builder.Default
    private int syncIntervalMinutes = 60;

    @Builder.Default
    private int parallelism = 1;

    @OneToMany(mappedBy = "discoveryServiceConfig", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<DiscoveryServiceAuth> authentications = new ArrayList<>();
}

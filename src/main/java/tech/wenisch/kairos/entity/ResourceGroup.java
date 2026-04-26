package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResourceGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ResourceGroupVisibility visibility = ResourceGroupVisibility.PUBLIC;

    public ResourceGroupVisibility getVisibilityOrDefault() {
        return visibility == null ? ResourceGroupVisibility.PUBLIC : visibility;
    }
}

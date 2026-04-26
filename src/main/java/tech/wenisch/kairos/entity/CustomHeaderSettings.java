package tech.wenisch.kairos.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomHeaderSettings {

    @Id
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String content;

    private boolean applyToAdmin;
}
